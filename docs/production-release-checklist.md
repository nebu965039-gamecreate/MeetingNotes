# 製品版(オープンテスト / 本番公開)チェックリスト — 商談メモ

クローズドテストから **オープンテスト or 製品版** に進むときに行う設定更新の一覧。
クローズドテスト提出の手順は `play-console-checklist.md`、AdMob は `admob.md`、
プロキシは `server/README.md` を参照。

---

## 1. API キープロキシ / Anthropic

| 項目 | 現在(クローズドテスト) | 製品版で行うこと | 場所 |
|---|---|---|---|
| Anthropic 支出上限 | $20/月 | 想定される正規支出の **2〜3倍** に再設定(利用者数 × 月5回 × 約2円 で試算) | Anthropic Console → Settings → Limits |
| KV 日次上限 `DAILY_REQUEST_CAP` | `"150"` | 想定利用者数に合わせて引き上げ(例: 1000人想定なら `"800"` 程度)。編集後 `cd server && npx wrangler deploy` | `server/wrangler.toml` |
| IP レート制限 | 未設定 | Cloudflare ダッシュボードで Rate limiting rule を作成(`/summarize`、10req/分/IP、Block) | Cloudflare ダッシュボード → Security → WAF |
| Play Integrity `PLAY_INTEGRITY_ENABLED` | `"off"` | GCP 設定を完了 → `"audit"` で数日ログ確認 → `"true"`(`server/README.md`「フェーズ2 -(c)」) | `server/wrangler.toml` + `wrangler secret` |
| `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` | 空 | Play Console でリンクした GCP プロジェクト番号を設定 | `local.properties` |
| `APP_TOKEN` | クローズドテスト用 | 製品版ビルド前にもう一度ローテーション推奨(`wrangler secret put APP_TOKEN` + `local.properties`) | — |
| Worker のドメイン | `*.workers.dev` | 任意。独自ドメインにするならここで(workers.dev のままでも可) | Cloudflare |

---

## 2. AdMob(`docs/admob.md` 参照)

| 項目 | 現在 | 製品版で行うこと |
|---|---|---|
| 広告の切替 | テスト広告(`ADMOB_USE_PRODUCTION_ADS` 未設定) | `local.properties` に `ADMOB_USE_PRODUCTION_ADS=true` を追加してビルド |
| ストアのリンク | 未リンク(「広告配信を制限」表示) | アプリが Play で公開されたら AdMob コンソール →「アプリ」→「アプリストア」で Google Play にリンク → 数日で配信制限が解除 |
| ビルドログ確認 | `AdMob: テスト広告ユニット` | 製品版ビルドで `AdMob: 本番広告ユニット` になることを確認 |
| 実機テスターへの配慮 | 不要 | 本番切替後も社内テストする端末は `ADMOB_TEST_DEVICE_IDS` に登録 |
| app-ads.txt | 未 | 独自ドメインがあれば設定(広告の fill 率向上。任意) |

---

## 3. アプリ本体 / ビルド

- [ ] `local.properties` を製品版設定にする:
  ```
  SUMMARY_PROXY_URL=...           (変更なし、または独自ドメイン)
  SUMMARY_PROXY_APP_TOKEN=...     (ローテーション後の値)
  ADMOB_USE_PRODUCTION_ADS=true
  PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER=<GCPプロジェクト番号>
  ```
- [ ] `app/build.gradle.kts`: `versionCode` +1、`versionName` を `"1.0.0"` に
- [ ] アップロード鍵のパスワードを強化(現在 `mana96`。`keytool -storepasswd` / `-keypasswd` で変更 → `keystore.properties` 更新)
- [ ] R8/難読化の有効化を検討(`isMinifyEnabled = true` + kotlinx.serialization / Room / Play Integrity / AdMob の keep ルール。要動作テスト)
- [ ] `./gradlew.bat bundleRelease` → **実機**でインストール・起動・録音・要約・エクスポートを一通り確認
- [ ] 署名検証(`jarsigner -verify`)

---

## 4. Play Console

- [ ] スクリーンショットの「録音中(音声レベル可視化)」を**実機で撮り直し**て差し替え(`docs/store-assets/参考-録音中...` はエミュのエラー表示)
- [ ] データセーフティ フォームの最終確認(`docs/play-data-safety.md`。Play Integrity トークンは「収集データ」ではないので追加申告不要の想定だが要確認)
- [ ] コンテンツのレーティング / ターゲットユーザー / 広告の申告 がすべて完了していること
- [ ] 「国 / 地域」と価格(無料)の設定
- [ ] クローズドテスト → **オープンテスト** または **製品版** トラックにプロモート
- [ ] 製品版は**段階的公開**(staged rollout)で開始(まず 10〜20%)
- [ ] ※ 個人アカウントの場合: 製品版公開の前に「テスター20人が14日間継続」の要件を満たしていること

---

## 5. Google Play Billing(サブスクを入れる場合)

- [ ] Play Console でサブスク商品を登録(月額 / 年額、価格、無料トライアル等)
- [ ] アプリに Billing Library を実装(購入フロー、購入状態の照会)
- [ ] 購入の**サーバー側検証**(Worker に検証エンドポイントを追加。RTDN / Play Developer API)
- [ ] 透かし ON/OFF をサブスク状態で自動判定に変更(現在はダイアログで毎回手動選択)
- [ ] エクスポート形式をサブスク制限: **無料 = PDF(透かし付き)のみ / 有料 = Word 出力・共有 + 追加形式**。`ExportOptionsDialog` の形式チップを entitlement で出し分け、無料ユーザーが有料形式を選んだら paywall へ
- [ ] サブスク向け追加エクスポート形式の実装(候補と工数の目安):
  - Markdown(.md) — Notion / Obsidian 向け。`ExportBlock` 流用で小
  - CSV — ToDo 一覧(タスク/担当/期限)をタスク管理ツールに取り込み。小
  - iCalendar(.ics) — 「次回打ち合わせ」をカレンダー登録。小・アプリ特性に合致
  - HTML(単一ファイル) — ブラウザ表示・体裁良し。小
  - 画像カード(PNG) — LINE / Slack 等へ画像共有。Canvas 描画、中
  - Excel(.xlsx) — `WordExporter` と同じ OOXML 手組み。中
- [ ] 無料枠クレジット判定のサーバー側移行(不正リセット対策フェーズ2 -(d)。AdMob リワードの SSV、プライバシーポリシー更新を伴う)
- [ ] サブスクの解約・返金ポリシーの文言をストア掲載情報 / アプリ内に記載

---

## 6. プライバシー / 法務

- [ ] プライバシーポリシー(`docs/privacy-policy.*`)の最終確認・必要なら更新(データフローの変更、Billing 追加時の課金情報の扱い等)
- [ ] GitHub Pages の公開URLが有効なことを再確認
- [ ] Billing 追加時: 特定商取引法に基づく表記(事業者情報、返金条件等)が必要か確認

---

## 7. 公開後のモニタリング体制

- [ ] Cloudflare: Worker のアナリティクス / エラー率を定期確認(`npx wrangler tail` でリアルタイムログ)
- [ ] Anthropic Console: 使用量・支出のモニタリング
- [ ] Play Console: Android vitals(クラッシュ率・ANR 率)
- [ ] AdMob: 収益・無効トラフィックの警告

---

## 進捗メモ

- 2026-09-01: Anthropic API キーをローテーション、旧キーは無効化済み。`DAILY_REQUEST_CAP` を `"150"` に変更(要 `wrangler deploy` 反映)。
