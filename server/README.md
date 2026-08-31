# 商談メモ 要約プロキシ Worker

アプリに Anthropic の API キーを埋め込まないための中継サーバー(Cloudflare Workers)。
アプリは文字起こしテキストだけをこの Worker に送り、Worker が Claude を呼んで要約を返す。

```
アプリ ──{transcript}──> Worker(APIキー保持)──> Anthropic Claude API
```

## 前提

- Cloudflare アカウント(無料。https://dash.cloudflare.com/sign-up)
- Node.js 18+(`node -v` で確認)

## 初回セットアップ

```bash
cd server
npm install
npx wrangler login          # ブラウザが開くので Cloudflare にログイン
```

秘密情報を登録(このリポジトリには保存されない):

```bash
# Anthropic のAPIキー(コンソールで発行したもの)
npx wrangler secret put ANTHROPIC_API_KEY

# アプリと Worker で共有するトークン。推測不能なランダム文字列を生成して使う:
#   node -e "console.log(require('crypto').randomBytes(24).toString('base64url'))"
npx wrangler secret put APP_TOKEN
```

デプロイ:

```bash
npx wrangler deploy
```

出力される URL(例 `https://meetingnotes-summary-proxy.<subdomain>.workers.dev`)の末尾に
`/summarize` を付けたものが要約エンドポイント。

## アプリ側の設定

プロジェクトルートの `local.properties` に追記:

```
SUMMARY_PROXY_URL=https://meetingnotes-summary-proxy.<subdomain>.workers.dev/summarize
SUMMARY_PROXY_APP_TOKEN=<APP_TOKEN で登録したのと同じ文字列>
```

その後アプリを再ビルド。`ANTHROPIC_API_KEY` はアプリ側では不要になった(削除してよい)。

## 動作確認

```bash
curl -s -X POST "$SUMMARY_PROXY_URL" \
  -H "x-app-token: $APP_TOKEN" \
  -H "content-type: application/json" \
  -d '{"transcript":"来週までに見積もりを送ります。月額プランで契約することで合意しました。"}' | jq
```

`content[].type == "tool_use"` の `input` に要約 JSON が入っていれば成功。

## ローカル実行(任意)

```bash
# .dev.vars に秘密を書く(gitignore 済み)
printf 'ANTHROPIC_API_KEY=sk-ant-...\nAPP_TOKEN=devtoken\n' > .dev.vars
npx wrangler dev            # http://localhost:8787/summarize
```

## 保護レベル(現状 = フェーズ1)

- アプリ⇔Worker の共有トークン(`x-app-token`)
- 文字起こしテキストの文字数上限(`wrangler.toml` の `MAX_TRANSCRIPT_CHARS`)
- `model` / `max_tokens` / プロンプトは Worker 側で固定(汎用 Claude プロキシとして悪用できない)
- Anthropic コンソールで**支出上限を必ず設定しておくこと**

### フェーズ2(製品版公開前に対応)

- Cloudflare ダッシュボードの **Rate Limiting Rules**(IPあたりのレート制限)
- **Play Integrity API** による端末・アプリの検証(`x-app-token` は APK から抽出可能なため)
- 端末ごとの利用回数上限を Worker + KV でサーバー側管理(不正リセット対策と統合)
