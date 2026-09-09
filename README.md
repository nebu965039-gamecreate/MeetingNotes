# 商談メモ (MeetingNotes)

フリーランス・個人事業主向けの「一人商談やりきり支援」Androidアプリ。商談を録音し、オンデバイス音声認識で文字起こしした後、Claude で「サマリー・決定事項・懸念点・ToDo・次回打ち合わせ・商談フェーズ・フォローアップ下書き」を自動抽出。クライアント別に記録し、次にやるべきこと（ToDo・予定）を見える化する。

元の設計仕様書: `C:\projects\商談メモアプリ_設計仕様書.md`
方向性・機能一覧の検討: Artifact「1人CRM 基本設計」

## 主な機能

### 記録・要約

- **録音 → 文字起こし → 要約**: 録音開始で「対面 / リモート会議」を選択 → 3秒カウントダウン → 録音（音声レベルバー・経過時間・長時間警告つき、停止はスライド操作で誤タップ防止）→ 停止 → 文字起こしを手動編集 → Claude で構造化要約
- **対面 / リモート会議モード**: 対面は端末内で文字起こし（音声は端末外に出ない）。リモート会議はWeb会議やスピーカー越しの相手の声も高精度で文字起こし（録音音声を Cloudflare のエッジで処理し即削除・保存なし。Pro 機能、無料は月1回＋広告視聴で追加）
- **要約項目**: サマリー / 決定事項 / 懸念点・注意点 / ToDo（タスク・担当・期限）/ 次回打ち合わせ / 商談フェーズ / フォローアップ下書き（要約と同時に生成）
- **下書き自動保存**: 録音中・編集中は数秒おきに端末へ保存。アプリが落ちてもホーム画面の復元カードから再開できる
- **直接録音**: 下部ナビ中央の録音ボタンからクライアントを選ばず即録音。要約後の保存時に既存クライアントを選ぶか新規登録する

### ホーム画面（起動直後の画面）

- **下部ナビ**: ホーム / クライアント / 予定表 / ToDo（未完了数バッジ付き）の4タブ + 中央に丸い録音ボタン（全画面から即録音）
- **ダッシュボード**（最上部）: 進行中フェーズのドーナツ + クライアント数・今月の商談・未完了ToDo + 全期間の成約率（母数0なら非表示）
- **動いていない案件**: 進行中なのに21日以上フェーズが変わっていない案件を一覧（該当があるときのみ表示）
- **直近の予定**: 次回打ち合わせが近い商談のプレビュー（日時・クライアント名・フェーズ）
- **やること（期限あり）**: 期限切れ・今日・3日以内の未完了ToDo（AI が抽出した期限を日付に解決）。チェックで完了、期限当日は通知
- **ToDo**: 対応が必要な商談（最新商談のフォロー連絡がまだ・成約/失注でない・直近30日以内）。要約完了時に「お礼・フォローアップのメールを送る」ToDo を自動起票し、クライアントのToDoリストにも要約由来のToDoと並べて表示。ボードの「完了」とこのメールToDoのチェックは連動

### 1人CRM

- **商談フェーズ（8段階）**: 初回接触／ヒアリング／提案／見積提示／検討中／成約／保留／失注。AI が推定し、タップで手動変更可。フェーズごとに色分けタグ表示
- **パイプラインボード**: ホームのダッシュボードから、進行中の案件をフェーズ別の列で一覧。カードの矢印やフェーズ選択で進捗を更新
- **ToDo ボード**（`ui/client/FollowupListScreen`）: 「ToDo」「スヌーズ」「完了」の3タブ。ToDo 行から「1週間／1ヶ月／3ヶ月後に再表示」でスヌーズでき、期日が来ると自動で戻る（「解除」で即戻す）。完了にした項目は「ToDoに戻す」で戻せる
- **前回のおさらい（ブリーフィング）**: 2回目以降の録音前に、そのクライアントとの「ここまでの流れ」を過去要約から自動生成して表示
- **予定表**: 月カレンダー（土日祝を色分け、予定がある日に印）＋ 予定一覧。**1クライアントに複数の予定**を作成でき（既存を上書きしない）、各予定に 内容・参加者・会議URL・場所・メモ・進捗ラベル を記録。行から「カレンダーに追加」でOSカレンダーへ。AI が要約から拾った「次回打ち合わせ」も予定として並ぶ
- **リマインド通知**: 予定の当日・前日に通知（WorkManager、12時間周期）。設定でON/OFF
- **フォローアップ下書き**: 要約時に生成済みの丁寧な連絡文面を商談詳細から参照（コピー／共有）。個別生成はせずトークンを追加消費しない

### 管理・出力

- **クライアント管理**: 追加（グループ選択つき）・削除、グループへの分類（折りたたみUI）。クライアント画面は「アーカイブ / ToDo（未完了・完了）/ 情報」の3タブ。ToDo タブでは要約由来のタスクに加え、録音なしで手動 ToDo を追加・編集・削除できる（内容＋任意の期限日）。情報タブで名前・現在のステータス・担当者（複数登録可）・メール・電話・備考を閲覧、「情報を編集」から編集
- **案件（プロジェクト・任意）**: 1クライアントに複数の案件を作成でき、商談を任意で紐付け。アーカイブ／ToDo タブのセレクトボックスで案件別に絞り込み表示。案件ごとに進捗フェーズ・見積額・成約額・成約日・失注理由・想定クローズ日・受注確度（円／ドル）を記録でき、ホームのダッシュボードに「今月の成約」「パイプライン」を集計表示。「売上・実績」ビュー（Pro・近日）で月次成約額グラフ・成約率（金額／件数）・売上予測（加重パイプライン）・失注理由の内訳・フェーズ別パイプラインを振り返り
- **商談アーカイブ**: クライアントごとに商談を記録。フォルダ整理、タイトル変更、全文検索（タイトル→サマリー→決定事項→懸念点→ToDo→文字起こしの優先順）と並び替え。各商談に管理番号（`No. yyyyMMddHHmm`）
- **エクスポート**: 議事録ぜんぶ（PDF／Word／Markdown）／ToDoリストだけ（Excel／CSV）／次回打ち合わせ（.ics）。「共有」と「保存」を選択可。PDF は透かし・パスワード保護（AES-256、Pro想定）オプションつき。Apache POI 不使用（標準ライブラリのみ）
- **設定**: 打ち合わせリマインドON/OFF、背景テーマ（ライト／ダーク／端末の設定に従う）、データのバックアップ／復元
- **バックアップ／復元**: 全クライアント・商談・ToDo・案件・予定を1つのJSONファイルに書き出し、機種変更・再インストール時に取り込み（全置換え）。パスワードを設定すると AES-GCM で暗号化。クラウド同期はせず、ファイルはユーザーが選んだ場所に保存
- **使い方・ヘルプ画面**

### 課金・広告

- **無料枠クレジット制**: 月5回まで無料で要約（`CreditPolicy.MONTHLY_FREE_CREDITS`）。リワード広告視聴で追加、要約失敗時は自動返却。1回の文字起こしは約20,000字（60〜80分）まで
- **Pro（サブスクリプション）**: 要約→文字起こしの根拠リンク、ヒアリング分析、売上・実績ビュー、エクスポート形式・透かし・PDFパスワードの解放を想定。Google Play Billing の基盤（`billing/BillingManager`、商品 `meetingnotes_pro`、購入フロー／復元／acknowledge、ペイウォール）は実装済み。ロック表示は `local.properties` の `PRO_GATING_ENABLED`（既定 false）で切替。Play Console でのサブスク商品登録・ライセンステスト後に有効化する
- **広告**: AdMob。バナー（各一覧の下部・商談詳細の300x250）、インタースティシャル（要約リクエスト直後）、リワード（クレジット切れ時）。既定はGoogle公式テスト広告。`local.properties` の `ADMOB_USE_PRODUCTION_ADS=true` で本番ユニットに切替

## 技術スタック

| 分類 | 内容 |
|---|---|
| 言語 | Kotlin 2.4.0 |
| UI | Jetpack Compose（Material3、Compose BOM 2026.08.00）。配色は M3 baseline purple。ダークテーマ対応 |
| 画面遷移 | Navigation Compose 2.9.8 |
| DB | Room 2.8.4（KSPでコード生成）。現在 version 24 |
| バックグラウンド | WorkManager（リマインド通知の周期実行） |
| 通信 | OkHttp 5.5.0 + kotlinx.serialization.json 1.11.0（Retrofit不使用、手組みHTTP） |
| 要約AI | Anthropic Claude（Messages API、tool_use、モデル `claude-haiku-4-5-20251001`）。APIキーはアプリに持たず自前の中継 Cloudflare Worker（`server/`）経由 |
| 音声認識 | Android標準 `SpeechRecognizer` のオンデバイス版（`createOnDeviceSpeechRecognizer`）。音声は保存しない |
| 広告 | AdMob（`play-services-ads` 25.4.0） |
| 課金 | Google Play Billing（`com.android.billingclient:billing` 7.1.1） |
| 不正対策 | Play Integrity API（Standard。`server/` 側で decode。コード実装済み・未有効化） |
| ビルド | AGP 9.1.0 / Gradle 9.3.1 / KSP 2.3.11 |
| DI | 無し（手動DI、`MeetingNotesApp` で lazy 生成） |

- `minSdk = 33`（Android 13以上、オンデバイス音声認識の都合）
- `targetSdk = compileSdk = 37`
- パッケージ名: ストア識別子 `com.manaapps.meetingnotes` / コード上の namespace `com.meetingnotes`（両者が異なる）

## ディレクトリ構成

```
app/src/main/java/com/meetingnotes/
├── MainActivity.kt          # エントリーポイント。テーマ出し分け + NavHost
├── MeetingNotesApp.kt       # Applicationクラス。Room DB・Repository・AdMob・リマインダ登録(lazy)
├── ads/                     # AdMob(Rewarded/Interstitial/Banner)
├── billing/                 # Google Play Billing(BillingManager)+ Pro ゲート判定(ProAccess)
├── data/
│   ├── CreditPolicy.kt      # 無料枠クレジットの月次リセット判定
│   ├── RecordingDraftStore.kt  # 録音下書きの自動保存(SharedPreferences)
│   ├── MeetingRepository.kt # 全DAOを束ねる単一リポジトリ
│   ├── local/               # Room: Entity/DAO/Database/Migrations/schemas
│   ├── model/               # ドメインモデル(MeetingSummary, DealPhase, NextMeetingTime 等)
│   └── remote/              # AnthropicClient(要約プロキシWorker経由)
├── export/                  # PDF/Word/Markdown/Excel/CSV/ICS + PDFパスワード保護
├── notifications/           # リマインド(NotificationHelper/ReminderScheduler/MeetingReminderWorker)
├── speech/                  # TranscriptionManager(SpeechRecognizerラッパー)、TranscriptPreprocessor
├── ui/
│   ├── MeetingViewModel.kt  # 録音〜要約〜保存フローの中心ViewModel
│   ├── Navigation.kt        # 画面遷移(NavHost)
│   ├── home/                # ホーム画面(起動直後、start destination)
│   ├── client/              # クライアント一覧・商談アーカイブ・ToDo一覧
│   ├── meeting/             # 商談詳細(要約・ToDo・エクスポート・フォローアップ下書き)
│   ├── recording/           # 録音画面
│   ├── result/              # 要約結果画面(保存前の編集・保存先選択)
│   ├── briefing/            # 前回のおさらい
│   ├── schedule/            # 予定表(カレンダー + 一覧)
│   ├── settings/            # 設定(リマインド・テーマ)
│   ├── notifications/       # 通知履歴・予定リスト
│   ├── help/                # 使い方・ヘルプ
│   ├── common/              # 共通ダイアログ・Composable(DealPhaseChip, LabeledDropdownField 等)
│   └── theme/               # テーマ・状態色(PhaseTrackerColors, PhaseTagColors, ProColors 等)
└── util/                    # DeviceIdentifier, JapaneseHolidays, CalendarIntent 等

app/src/test/java/...        # JVMユニットテスト(gradlew、エミュレータ不要)
app/src/androidTest/java/... # インストルメンテーションテスト(実機/エミュレータ必須。Migrationテスト含む)
app/schemas/...              # Room スキーマ履歴(exportSchema。マイグレーション用にコミット)
server/                      # 要約プロキシ(Cloudflare Worker、TypeScript)。server/README.md 参照
docs/                        # Play Console 提出物・プライバシーポリシー・リリースノート
```

## セットアップ

1. 要約プロキシ Worker をデプロイする（`server/README.md` 参照）。デプロイ後の URL とトークンを控える。
   - `server/` ディレクトリで `npx wrangler deploy` を実行すること（リポジトリルートで実行すると別プロジェクトを作ろうとする）
2. `local.properties.example` を `local.properties` にコピーし、以下を設定（gitignore対象）:
   ```
   sdk.dir=<Android SDKのパス>
   SUMMARY_PROXY_URL=<Worker の /summarize URL>
   SUMMARY_PROXY_APP_TOKEN=<Worker に登録した APP_TOKEN と同じ文字列>
   ```
3. Android Studio または `gradlew` でビルド

## ビルド・実行

```bash
./gradlew.bat assembleDebug              # デバッグAPKビルド
./gradlew.bat testDebugUnitTest          # JVMユニットテスト
./gradlew.bat connectedDebugAndroidTest  # インストルメンテーションテスト(実機/エミュレータ必須)
```

インストール:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.manaapps.meetingnotes/com.meetingnotes.MainActivity
```

## 開発方針・現在の状況

Claude Code で開発を継続する場合は `CLAUDE.md` を参照。現在の実装状況・未完了タスク・既知の問題もそちらにまとめている。

- Google Play Console でクローズドテスト中。無料の 1人CRM 機能一式を次回配信予定
- 未完了: Google Play Billing の有効化（基盤は実装済み、Play Console 商品登録＋`PRO_GATING_ENABLED=true`）、APIキープロキシ フェーズ2 の有効化、Pro 機能（F4 根拠リンク・F6 ヒアリング分析）
