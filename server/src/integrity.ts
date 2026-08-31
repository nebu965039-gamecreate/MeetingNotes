/**
 * Play Integrity API のトークン検証(サーバー側)。
 *
 * アプリが `x-integrity-token` ヘッダで送ってくる Standard Integrity Token を、
 * Google の `playintegrity.googleapis.com` で復号・検証する。
 *
 * 必要なシークレット(wrangler secret put):
 *   PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON  … Google Cloud サービスアカウントの鍵JSON(全文)
 * 必要な変数(wrangler.toml [vars] か secret):
 *   ANDROID_PACKAGE_NAME                 … com.manaapps.meetingnotes
 *   PLAY_INTEGRITY_ENABLED               … "true"(拒否) / "audit"(検証のみ通す) / それ以外(無効)
 */

export interface IntegrityEnv {
  PLAY_INTEGRITY_ENABLED?: string;
  PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON?: string;
  ANDROID_PACKAGE_NAME?: string;
}

export type IntegrityMode = "enforce" | "audit" | "off";

export function integrityMode(env: IntegrityEnv): IntegrityMode {
  const v = (env.PLAY_INTEGRITY_ENABLED ?? "").toLowerCase();
  if (v === "true" || v === "enforce") return "enforce";
  if (v === "audit") return "audit";
  return "off";
}

export interface IntegrityResult {
  ok: boolean;
  reason?: string;
}

// --- base64 / PEM ヘルパー ---

function base64UrlEncode(bytes: Uint8Array): string {
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): ArrayBuffer {
  const body = pem
    .replace(/-----BEGIN [^-]+-----/, "")
    .replace(/-----END [^-]+-----/, "")
    .replace(/\s+/g, "");
  const bin = atob(body);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out.buffer;
}

// --- サービスアカウント → OAuth アクセストークン(RS256 JWT を自前で署名)---

let cachedToken: { token: string; exp: number } | null = null;

async function getAccessToken(saJson: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.exp - 60 > now) return cachedToken.token;

  const sa = JSON.parse(saJson) as { client_email: string; private_key: string };
  const scope = "https://www.googleapis.com/auth/playintegrity";
  const header = base64UrlEncode(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claim = base64UrlEncode(
    new TextEncoder().encode(
      JSON.stringify({
        iss: sa.client_email,
        scope,
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
      })
    )
  );
  const signingInput = `${header}.${claim}`;

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput)
  );
  const jwt = `${signingInput}.${base64UrlEncode(new Uint8Array(sig))}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: `grant_type=${encodeURIComponent(
      "urn:ietf:params:oauth:grant-type:jwt-bearer"
    )}&assertion=${jwt}`,
  });
  if (!res.ok) throw new Error(`oauth token error ${res.status}: ${await res.text()}`);
  const data = (await res.json()) as { access_token: string; expires_in: number };
  cachedToken = { token: data.access_token, exp: now + (data.expires_in ?? 3600) };
  return data.access_token;
}

// --- Integrity Token の復号・検証 ---

interface TokenPayload {
  requestDetails?: { requestPackageName?: string; requestHash?: string; timestampMillis?: string };
  appIntegrity?: { appRecognitionVerdict?: string; packageName?: string };
  deviceIntegrity?: { deviceRecognitionVerdict?: string[] };
}

/**
 * @param token         アプリから受け取った integrity token
 * @param expectedHash  サーバーが独立に計算した requestHash(SHA-256 hex)
 */
export async function verifyIntegrityToken(
  env: IntegrityEnv,
  token: string,
  expectedHash: string
): Promise<IntegrityResult> {
  const pkg = env.ANDROID_PACKAGE_NAME;
  const saJson = env.PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON;
  if (!pkg || !saJson) return { ok: false, reason: "integrity_not_configured" };

  let accessToken: string;
  try {
    accessToken = await getAccessToken(saJson);
  } catch (e) {
    return { ok: false, reason: `oauth_failed: ${(e as Error).message}` };
  }

  const res = await fetch(
    `https://playintegrity.googleapis.com/v1/${encodeURIComponent(pkg)}:decodeIntegrityToken`,
    {
      method: "POST",
      headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
      body: JSON.stringify({ integrity_token: token }),
    }
  );
  if (!res.ok) return { ok: false, reason: `decode_failed_${res.status}` };

  const body = (await res.json()) as { tokenPayloadExternal?: TokenPayload };
  const p = body.tokenPayloadExternal;
  if (!p) return { ok: false, reason: "no_payload" };

  if (p.requestDetails?.requestPackageName !== pkg) return { ok: false, reason: "package_mismatch" };
  if (p.requestDetails?.requestHash !== expectedHash) return { ok: false, reason: "hash_mismatch" };

  const appVerdict = p.appIntegrity?.appRecognitionVerdict;
  if (appVerdict !== "PLAY_RECOGNIZED") return { ok: false, reason: `app_${appVerdict ?? "unknown"}` };

  const deviceVerdicts = p.deviceIntegrity?.deviceRecognitionVerdict ?? [];
  if (!deviceVerdicts.includes("MEETS_DEVICE_INTEGRITY")) {
    return { ok: false, reason: `device_${deviceVerdicts.join(",") || "unknown"}` };
  }

  return { ok: true };
}

/** transcript から requestHash(SHA-256 hex)を計算。アプリ側と一致させること。 */
export async function requestHashOf(transcript: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(transcript));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
