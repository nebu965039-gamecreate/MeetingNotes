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

## 保護レベル

### フェーズ1(実装済み)

- アプリ⇔Worker の共有トークン(`x-app-token`)
- 文字起こしテキストの文字数上限(`wrangler.toml` の `MAX_TRANSCRIPT_CHARS`)
- `model` / `max_tokens` / プロンプトは Worker 側で固定(汎用 Claude プロキシとして悪用できない)
- Anthropic コンソールで**支出上限を必ず設定しておくこと**

### フェーズ2 -(a) 全体の1日あたり上限(KV、任意・コードは実装済み)

leaked トークンで大量に叩かれても被害を頭打ちにする安全弁。

```bash
npx wrangler kv namespace create RL
# 出力される id を wrangler.toml の [[kv_namespaces]] のコメントを外して貼る
```

`wrangler.toml` の `DAILY_REQUEST_CAP` で上限を調整(既定 1000/日)。KV 未設定なら無効(何もしない)。

### フェーズ2 -(b) IPあたりのレート制限(Cloudflare ダッシュボード、コード不要)

Cloudflare ダッシュボード → 対象ドメイン(workers.dev)→ **Security → WAF → Rate limiting rules** →
「if URI Path contains `/summarize`」「10 requests per 1 minute」「同一 IP」→ Block。無料プランで1ルール作成可。

### フェーズ2 -(c) Play Integrity API(端末・アプリの正当性検証・コードは実装済み)

`x-app-token` は APK から抽出可能なので、これが本命の対策。**実装済みで、下記の設定を行って有効化する。**

**1. Play Console でプロジェクトをリンク**
- Play Console → アプリ → 「テストとリリース」→「アプリの完全性」→ Play Integrity API
- Google Cloud プロジェクトをリンク(無ければ作成される)。表示される **Cloud プロジェクト番号** を控える

**2. Google Cloud Console(リンクしたプロジェクト)**
- 「API とサービス」→ **Play Integrity API を有効化**
- 「IAM と管理」→「サービス アカウント」→ 作成(例 `playintegrity-verifier`)
- そのサービスアカウントの「キー」→ 新しい鍵(JSON)を作成してダウンロード
- ※ `decodeIntegrityToken` で 403 が出る場合は、Play Console →「ユーザーと権限」でこのサービスアカウントのメールアドレスを招待し「アプリ情報の閲覧」権限を付与

**3. Worker 側**
```bash
# ダウンロードした JSON の中身を全文貼り付け
npx wrangler secret put PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON
```
`wrangler.toml` の `PLAY_INTEGRITY_ENABLED` を `"audit"` にして `npx wrangler deploy`。
`npx wrangler tail` でログを見て「integrity audit failed」が出ないことを確認 → `"true"` に変えて再デプロイ(不正なリクエストを拒否)。

**4. アプリ側**
- `local.properties` に `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER=<手順1の番号>` を設定
- `versionCode` を上げて再ビルド → **クローズドテストにアップロード**
- ⚠️ Play Integrity(Standard API)は **Play 経由でインストールしたアプリ**でのみ動く。
  ローカルの debug ビルドやサイドロードでは常にトークン取得に失敗する(=検証スキップ)。
  実機検証はクローズドテストのビルドで行う。

### フェーズ2 -(d) 端末ごとのクレジット管理(未実装・別タスク)

無料枠(月3回)の判定を端末ローカルからサーバー側へ移す。AdMob のリワード広告
サーバー検証(SSV)、プライバシーポリシー更新も伴うため、Billing と合わせて別途対応。
