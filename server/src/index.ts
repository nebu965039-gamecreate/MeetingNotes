/**
 * 商談メモ 要約プロキシ Worker。
 *
 * アプリは APIキーを持たず、このWorkerに文字起こしテキストだけを送る:
 *   POST /summarize
 *   headers: { "x-app-token": "<APP_TOKEN>", "content-type": "application/json" }
 *   body:    { "transcript": "..." }
 *
 * Worker が Anthropic Messages API を呼び、レスポンス(tool_use を含む JSON)を
 * そのままアプリへ返す。アプリ側のパース処理は変更不要。
 *
 * 秘密情報(wrangler secret put で登録):
 *   ANTHROPIC_API_KEY  Anthropic のAPIキー
 *   APP_TOKEN          アプリと共有するトークン(ランダム文字列)
 */

interface Env {
  ANTHROPIC_API_KEY: string;
  APP_TOKEN: string;
  MAX_TRANSCRIPT_CHARS?: string;
}

const MODEL = "claude-haiku-4-5-20251001";
const MAX_TOKENS = 1500;
const TEMPERATURE = 0.2;
const TOOL_NAME = "extract_meeting_summary";
const ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
const MAX_ATTEMPTS = 3;
const INITIAL_BACKOFF_MS = 1000;

const SYSTEM_PROMPT = `あなたはフリーランス・個人事業主向けの商談記録アシスタントです。
以下の商談の文字起こしテキストを読み、内容を構造化して抽出してください。

# 抽出ルール
1. 「決定事項」: その商談内で合意・確定した内容のみを抽出する。
   検討中・保留になった内容は含めない。
2. 「ToDo」: 誰が(担当者)、何を、いつまでに行うかが
   話されているものを抽出する。担当者や期限が不明な場合は
   「担当者: 未定」「期限: 未定」と明記する。
3. 「次回打ち合わせ」: 日時が明言されている場合のみ抽出する。
   曖昧な表現(例:「また来週あたり」)は
   originalText にそのまま記録し、date は null とする。
4. 「懸念点・注意点」: クライアントが不安・懸念・要望として
   話した内容を抽出する。
5. 文字起こしには誤字・脱字が含まれる可能性がある。
   文脈から明らかな誤変換は自然な形に補正してよいが、
   意味を変えるような推測は行わない。
6. 該当する情報がない項目は、空配列 [] または null とする。
   存在しない情報を作り出さない。
7. summary・decisions・concernsなど、すべてのテキスト項目の文体は
   体言止め中心の簡潔な記録調で統一すること。
   (例:「月額プランで契約に合意」「導入コストへの懸念」のように
   名詞で言い切り、「です・ます」「だ・である」などの
   語尾は付けない。)`;

const SUMMARY_TOOL_SCHEMA = {
  type: "object",
  properties: {
    decisions: {
      type: "array",
      items: { type: "object", properties: { content: { type: "string" } } },
    },
    todos: {
      type: "array",
      items: {
        type: "object",
        properties: {
          task: { type: "string" },
          assignee: { type: "string" },
          deadline: { type: "string" },
        },
      },
    },
    nextMeeting: {
      type: "object",
      properties: {
        date: { type: ["string", "null"] },
        originalText: { type: ["string", "null"] },
      },
    },
    concerns: {
      type: "array",
      items: { type: "object", properties: { content: { type: "string" } } },
    },
    summary: { type: "string" },
  },
  required: ["decisions", "todos", "nextMeeting", "concerns", "summary"],
} as const;

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/") {
      return jsonResponse({ ok: true, service: "meetingnotes-summary-proxy" });
    }
    if (request.method !== "POST" || url.pathname !== "/summarize") {
      return jsonResponse({ error: "not_found" }, 404);
    }

    // --- 認証(アプリとの共有トークン)---
    if (!env.APP_TOKEN || request.headers.get("x-app-token") !== env.APP_TOKEN) {
      return jsonResponse({ error: "unauthorized" }, 401);
    }
    if (!env.ANTHROPIC_API_KEY) {
      return jsonResponse({ error: "server_misconfigured" }, 500);
    }

    // --- 入力の検証 ---
    let transcript: unknown;
    try {
      const parsed = (await request.json()) as { transcript?: unknown };
      transcript = parsed.transcript;
    } catch {
      return jsonResponse({ error: "invalid_json" }, 400);
    }
    if (typeof transcript !== "string" || transcript.trim().length === 0) {
      return jsonResponse({ error: "transcript_required" }, 400);
    }
    const maxChars = Number(env.MAX_TRANSCRIPT_CHARS ?? "60000");
    if (transcript.length > maxChars) {
      return jsonResponse({ error: "transcript_too_long", maxChars }, 413);
    }

    // --- Anthropic Messages API を呼ぶ(このリクエストの形しか作れない)---
    const anthropicBody = JSON.stringify({
      model: MODEL,
      max_tokens: MAX_TOKENS,
      temperature: TEMPERATURE,
      system: SYSTEM_PROMPT,
      messages: [
        {
          role: "user",
          content: `以下は商談の文字起こしテキストです。上記のルールに従って抽出してください。\n\n# 文字起こしテキスト\n${transcript}`,
        },
      ],
      tools: [
        {
          name: TOOL_NAME,
          description: "商談の文字起こしから構造化データを抽出する",
          input_schema: SUMMARY_TOOL_SCHEMA,
        },
      ],
      tool_choice: { type: "tool", name: TOOL_NAME },
    });

    let lastStatus = 502;
    let lastBody = '{"error":"upstream_unavailable"}';
    for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      let upstream: Response;
      try {
        upstream = await fetch(ANTHROPIC_URL, {
          method: "POST",
          headers: {
            "x-api-key": env.ANTHROPIC_API_KEY,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
          },
          body: anthropicBody,
        });
      } catch {
        lastStatus = 502;
        lastBody = '{"error":"upstream_fetch_failed"}';
        if (attempt === MAX_ATTEMPTS - 1) break;
        await sleep(INITIAL_BACKOFF_MS * 2 ** attempt);
        continue;
      }

      const text = await upstream.text();
      if (upstream.ok) {
        // Anthropic のレスポンスをそのまま返す(アプリのパースは変更不要)
        return new Response(text, {
          status: 200,
          headers: { "content-type": "application/json; charset=utf-8" },
        });
      }

      lastStatus = upstream.status;
      lastBody = text;
      const retryable = upstream.status === 429 || upstream.status >= 500;
      if (!retryable || attempt === MAX_ATTEMPTS - 1) break;
      await sleep(INITIAL_BACKOFF_MS * 2 ** attempt);
    }

    // 上流のステータス/本文を透過して返す
    return new Response(lastBody, {
      status: lastStatus,
      headers: { "content-type": "application/json; charset=utf-8" },
    });
  },
};
