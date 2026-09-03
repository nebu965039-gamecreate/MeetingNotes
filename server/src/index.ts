/**
 * 商談メモ 要約プロキシ Worker。
 *
 * アプリは APIキーを持たず、このWorkerに文字起こしテキストだけを送る:
 *   POST /summarize
 *   headers: {
 *     "x-app-token": "<APP_TOKEN>",              // 必須(フェーズ1)
 *     "x-integrity-token": "<Play Integrity token>", // フェーズ2(PLAY_INTEGRITY_ENABLED時)
 *     "content-type": "application/json"
 *   }
 *   body: { "transcript": "..." }
 *
 * Worker が Anthropic Messages API を呼び、レスポンス(tool_use を含む JSON)を
 * そのままアプリへ返す。
 *
 * シークレット(wrangler secret put):
 *   ANTHROPIC_API_KEY                     Anthropic のAPIキー
 *   APP_TOKEN                             アプリと共有するトークン
 *   PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON   (フェーズ2)GCPサービスアカウント鍵JSON
 * 変数(wrangler.toml [vars]):
 *   MAX_TRANSCRIPT_CHARS, ANDROID_PACKAGE_NAME, PLAY_INTEGRITY_ENABLED, DAILY_REQUEST_CAP
 * バインディング(任意):
 *   RL   KV Namespace。設定すると全体の1日あたり呼び出し回数に上限をかける
 */

import {
  IntegrityEnv,
  integrityMode,
  requestHashOf,
  verifyIntegrityToken,
} from "./integrity";

interface Env extends IntegrityEnv {
  ANTHROPIC_API_KEY: string;
  APP_TOKEN: string;
  MAX_TRANSCRIPT_CHARS?: string;
  DAILY_REQUEST_CAP?: string;
  STRICT_TOOL?: string;
  RL?: KVNamespace;
}

const MODEL = "claude-haiku-4-5-20251001";
const MAX_TOKENS = 2000;
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
   語尾は付けない。)
8. 「次回打ち合わせ」の date は、メッセージ冒頭で与えられる「現在の日付」を基準に
   ISO 8601(YYYY-MM-DD、時刻が明言されていれば YYYY-MM-DDTHH:MM)で解決する。
   年をまたぐ相対表現は現在の日付から最も近い将来の日付を採る。
9. 「dealPhase」: この商談が営業プロセスのどの段階かを、話の内容から1つ推定する。
   - first_contact: 初回の顔合わせ・挨拶が中心
   - hearing: 相手の課題・要望・状況をヒアリングしている段階
   - proposal: こちらから提案・提示を行っている段階
   - quoted: 金額・見積もりを提示済みで、その反応や条件を話している段階
   - considering: 提案後、相手が社内検討・比較検討している段階
   - won: 発注・契約・成約が確定した
   - on_hold: 案件が保留・先送りになった
   - lost: 失注・見送りが確定した
   判断材料が乏しい場合は最も近いものを選ぶ(初期接触なら first_contact、
   金額の話が出ていれば quoted など)。必ず1つ返す。`;

// strict: true 対応のため、入れ子オブジェクトにも additionalProperties:false と required を付ける。
const SUMMARY_TOOL_SCHEMA = {
  type: "object",
  additionalProperties: false,
  properties: {
    decisions: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        properties: { content: { type: "string" } },
        required: ["content"],
      },
    },
    todos: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        properties: {
          task: { type: "string" },
          assignee: { type: "string" },
          deadline: { type: "string" },
        },
        required: ["task", "assignee", "deadline"],
      },
    },
    nextMeeting: {
      type: "object",
      additionalProperties: false,
      properties: {
        date: { type: ["string", "null"] },
        originalText: { type: ["string", "null"] },
      },
      required: ["date", "originalText"],
    },
    concerns: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        properties: { content: { type: "string" } },
        required: ["content"],
      },
    },
    summary: { type: "string" },
    dealPhase: {
      type: "string",
      enum: [
        "first_contact",
        "hearing",
        "proposal",
        "quoted",
        "considering",
        "won",
        "on_hold",
        "lost",
      ],
    },
  },
  required: ["decisions", "todos", "nextMeeting", "concerns", "summary", "dealPhase"],
} as const;

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** KV による「全体で1日あたり N 回」の上限(RL バインディング設定時のみ)。 */
async function underDailyCap(env: Env): Promise<boolean> {
  if (!env.RL) return true;
  const cap = Number(env.DAILY_REQUEST_CAP ?? "1000");
  if (!Number.isFinite(cap) || cap <= 0) return true;
  const key = `count:${new Date().toISOString().slice(0, 10)}`;
  const current = Number((await env.RL.get(key)) ?? "0");
  if (current >= cap) return false;
  await env.RL.put(key, String(current + 1), { expirationTtl: 172800 });
  return true;
}

/** briefing / followup 用の短いテキスト生成(tool_use なしのプレーン補完)。 */
const BRIEFING_SYSTEM = `あなたはフリーランス・個人事業主の商談準備を手伝うアシスタントです。
渡された「過去の商談要約」(古い順)を読み、そのクライアントとの
「ここまでの流れ」を2〜4文でまとめてください。
- 体言止め中心の簡潔な記録調(「〜で合意」「〜への懸念」など)
- 提案→検討→保留 のような流れの推移がわかるように
- 過去要約に無い情報を推測で足さない
- 前置きや見出しを付けず、本文だけを返す`;

const FOLLOWUP_SYSTEM_POLITE = `あなたはフリーランス・個人事業主のフォローアップ文面を作成するアシスタントです。
渡された商談要約をもとに、商談直後に相手へ送るメッセージの下書きを作ってください。
- 構成: お礼 → 決定事項・確認事項の要点 → 次のアクションのお願い
- 3〜5文。丁寧だが冗長でない文体(です・ます)
- 宛名・署名・件名は入れない。本文のみ
- 要約に無い予定や約束を作らない`;

const FOLLOWUP_SYSTEM_CASUAL = `あなたはフリーランス・個人事業主のフォローアップ文面を作成するアシスタントです。
渡された商談要約をもとに、商談直後に相手へ送るメッセージの下書きを作ってください。
- 構成: お礼 → 決定事項・確認事項の要点 → 次のアクションのお願い
- 3〜5文。ややカジュアルで簡潔な文体
- 宛名・署名・件名は入れない。本文のみ
- 要約に無い予定や約束を作らない`;

/** 認証と日次上限。OKなら null、NGなら返すべき Response。 */
async function guard(request: Request, env: Env): Promise<Response | null> {
  if (!env.APP_TOKEN || request.headers.get("x-app-token") !== env.APP_TOKEN) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }
  if (!env.ANTHROPIC_API_KEY) {
    return jsonResponse({ error: "server_misconfigured" }, 500);
  }
  if (!(await underDailyCap(env))) {
    return jsonResponse({ error: "daily_cap_reached" }, 429);
  }
  return null;
}

/** tool を使わない短文生成を Anthropic に投げ、{ text } で返す。 */
async function generateText(
  env: Env,
  system: string,
  userContent: string,
  maxTokens: number
): Promise<Response> {
  const body = JSON.stringify({
    model: MODEL,
    max_tokens: maxTokens,
    temperature: 0.3,
    system: [{ type: "text", text: system, cache_control: { type: "ephemeral" } }],
    messages: [{ role: "user", content: userContent }],
  });

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
        body,
      });
    } catch {
      if (attempt === MAX_ATTEMPTS - 1) return jsonResponse({ error: "upstream_fetch_failed" }, 502);
      await sleep(INITIAL_BACKOFF_MS * 2 ** attempt);
      continue;
    }

    const raw = await upstream.text();
    if (upstream.ok) {
      try {
        const parsed = JSON.parse(raw) as { content?: Array<{ type: string; text?: string }> };
        const text = (parsed.content ?? [])
          .filter((b) => b.type === "text" && typeof b.text === "string")
          .map((b) => b.text)
          .join("")
          .trim();
        return jsonResponse({ text });
      } catch {
        return jsonResponse({ error: "parse_failed" }, 502);
      }
    }
    const retryable = upstream.status === 429 || upstream.status >= 500;
    if (!retryable || attempt === MAX_ATTEMPTS - 1) {
      return jsonResponse({ error: "upstream_error", status: upstream.status }, upstream.status);
    }
    await sleep(INITIAL_BACKOFF_MS * 2 ** attempt);
  }
  return jsonResponse({ error: "upstream_unavailable" }, 502);
}

async function handleBriefing(request: Request, env: Env): Promise<Response> {
  const g = await guard(request, env);
  if (g) return g;
  let summaries: unknown;
  try {
    summaries = ((await request.json()) as { summaries?: unknown }).summaries;
  } catch {
    return jsonResponse({ error: "invalid_json" }, 400);
  }
  if (!Array.isArray(summaries) || summaries.length === 0) {
    return jsonResponse({ error: "summaries_required" }, 400);
  }
  const list = summaries
    .filter((s): s is string => typeof s === "string" && s.trim().length > 0)
    .slice(-8)
    .map((s, i) => `【商談${i + 1}】\n${s.trim()}`)
    .join("\n\n");
  if (list.length === 0) return jsonResponse({ error: "summaries_required" }, 400);
  return generateText(env, BRIEFING_SYSTEM, `過去の商談要約(古い順):\n\n${list}`, 400);
}

async function handleFollowup(request: Request, env: Env): Promise<Response> {
  const g = await guard(request, env);
  if (g) return g;
  let parsed: { summary?: unknown; tone?: unknown };
  try {
    parsed = (await request.json()) as { summary?: unknown; tone?: unknown };
  } catch {
    return jsonResponse({ error: "invalid_json" }, 400);
  }
  if (typeof parsed.summary !== "string" || parsed.summary.trim().length === 0) {
    return jsonResponse({ error: "summary_required" }, 400);
  }
  const system = parsed.tone === "casual" ? FOLLOWUP_SYSTEM_CASUAL : FOLLOWUP_SYSTEM_POLITE;
  return generateText(env, system, `商談要約:\n\n${parsed.summary.trim()}`, 500);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/") {
      return jsonResponse({ ok: true, service: "meetingnotes-summary-proxy" });
    }
    if (request.method === "POST" && url.pathname === "/briefing") {
      return handleBriefing(request, env);
    }
    if (request.method === "POST" && url.pathname === "/followup") {
      return handleFollowup(request, env);
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
    const maxChars = Number(env.MAX_TRANSCRIPT_CHARS ?? "20000");
    if (transcript.length > maxChars) {
      return jsonResponse({ error: "transcript_too_long", maxChars }, 413);
    }

    // --- Play Integrity 検証(フェーズ2)---
    const mode = integrityMode(env);
    if (mode !== "off") {
      const integrityToken = request.headers.get("x-integrity-token");
      const expectedHash = await requestHashOf(transcript);
      if (!integrityToken) {
        if (mode === "enforce") return jsonResponse({ error: "integrity_token_required" }, 401);
      } else {
        const result = await verifyIntegrityToken(env, integrityToken, expectedHash);
        if (!result.ok && mode === "enforce") {
          return jsonResponse({ error: "integrity_check_failed", reason: result.reason }, 403);
        }
        if (!result.ok) console.warn("integrity audit failed:", result.reason);
      }
    }

    // --- 全体の1日あたり上限(任意)---
    if (!(await underDailyCap(env))) {
      return jsonResponse({ error: "daily_cap_reached" }, 429);
    }

    // --- Anthropic Messages API を呼ぶ(このリクエストの形しか作れない)---
    // 現在の日付(JST)。相対的な日付表現の解決に使う。
    const todayJst = new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10);
    const userContent = `現在の日付: ${todayJst}\n\n以下は商談の文字起こしテキストです。上記のルールに従って抽出してください。\n\n# 文字起こしテキスト\n${transcript}`;

    const buildBody = (strict: boolean): string =>
      JSON.stringify({
        model: MODEL,
        max_tokens: MAX_TOKENS,
        temperature: TEMPERATURE,
        // system と tools は毎回同一なのでプロンプトキャッシュ対象にする
        // (最小トークン数に満たない場合は自動的にキャッシュされないだけで無害)。
        system: [{ type: "text", text: SYSTEM_PROMPT, cache_control: { type: "ephemeral" } }],
        messages: [{ role: "user", content: userContent }],
        tools: [
          {
            name: TOOL_NAME,
            description: "商談の文字起こしから構造化データを抽出する",
            input_schema: SUMMARY_TOOL_SCHEMA,
            ...(strict ? { strict: true } : {}),
            cache_control: { type: "ephemeral" },
          },
        ],
        tool_choice: { type: "tool", name: TOOL_NAME },
      });

    // strict:true で tool_use.input が必ずスキーマ通りになる(パース失敗の回避)。
    // 万一 Anthropic が strict を拒否(400)したら、自動で strict なしにフォールバックする。
    let strictEnabled = (env.STRICT_TOOL ?? "true").toLowerCase() !== "false";
    let anthropicBody = buildBody(strictEnabled);

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
        return new Response(text, {
          status: 200,
          headers: { "content-type": "application/json; charset=utf-8" },
        });
      }

      // strict が原因の 400 とみられる場合、strict なしで即やり直す(この attempt は使う)。
      if (upstream.status === 400 && strictEnabled) {
        strictEnabled = false;
        anthropicBody = buildBody(false);
        console.warn("strict tool rejected (400), retrying without strict");
        continue;
      }

      lastStatus = upstream.status;
      lastBody = text;
      const retryable = upstream.status === 429 || upstream.status >= 500;
      if (!retryable || attempt === MAX_ATTEMPTS - 1) break;
      await sleep(INITIAL_BACKOFF_MS * 2 ** attempt);
    }

    return new Response(lastBody, {
      status: lastStatus,
      headers: { "content-type": "application/json; charset=utf-8" },
    });
  },
};
