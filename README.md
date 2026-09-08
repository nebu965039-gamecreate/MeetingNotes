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
- **直接録音**: ホーム右下の録音FABからクライアントを選ばず即録音。要約後の保存時に既存クライアントを選ぶか新規登録する

### ホーム画面（起動直後の画面）

- **下部ナビ 4タブ**: ホーム / クライアント / 予定表 / ToDo（未完了数バッジ付き）。録音は右下のFAB（ホームタブのみ）
- **ダッシュボード**（最上部）: 進行中フェーズのドーナツ + クライアント数・今月の商談・未完了ToDo + 全期間の成約率（母数0なら非表示）
- **直近の予定**: 次回打ち合わせが近い商談のプレビュー（日時・クライアント名・フェーズ）
- **やること（期限あり）**: 期限切れ・今日・3日以内の未完了ToDo（AI が抽出した期限を日付に解決）。チェックで完了、期限当日は通知
- **ToDo**: 対応が必要な商談（最新商談のフォロー連絡がまだ・成約/失注でない・直近30日以内）。要約完了時に「お礼・フォローアップのメールを送る」ToDo を自動起票し、クライアントのToDoリストにも要約由来のToDoと並べて表示。ボードの「完了」とこのメールToDoのチェックは連動

### 1人CRM

- **商談フェーズ（8段階）**: 初回接触／ヒアリング／提案／見積提示／検討中／成約／保留／失注。AI が推定し、タップで手動変更可。フェーズごとに色分けタグ表示
- **ToDo ボード**（`ui/client/FollowupListScreen`）: 「ToDo」「完了」の2タブ。完了にした項目は「ToDoに戻す」で戻せる
- **前回のおさらい（ブリーフィング）**: 2回目以降の録音前に、そのクライアントとの「ここまでの流れ」を過去要約から自動生成して表示
- **予定表**: 月カレンダー（土日祝を色分け、予定がある日に印）＋ 予定一覧。日付タップで絞り込み、行から日程変更・削除、FAB から予定追加
- **リマインド通知**: 次回打ち合わせの当日・前日に通知（WorkManager、12時間周期）。設定でON/OFF
- **フォローアップ下書き**: 要約時に生成済みの丁寧な連絡文面を商談詳細から参照（コピー／共有）。個別生成はせずトークンを追加消費しない

### 管理・出力

- **クライアント管理**: 追加（グループ選択つき）・削除、グループへの分類（折りたたみUI）。クライアント画面は「アーカイブ / ToDo（未完了・完了）/ 情報」の3タブ。情報タブで名前・現在のステータス・担当者（複数登録可）・メール・電話・備考を閲覧、「情報を編集」から編集
- **商談アーカイブ**: クライアントごとに商談を記録。フォルダ整理、タイトル変更、全文検索（タイトル→サマリー→決定事項→懸念点→ToDo→文字起こしの優先順）と並び替え。各商談に管理番号（`No. yyyyMMddHHmm`）
- **エクスポート**: 議事録ぜんぶ（PDF／Word／Markdown）／ToDoリストだけ（Excel／CSV）／次回打ち合わせ（.ics）。「共有」と「保存」を選択可。PDF は透かし・パスワード保護（AES-256、Pro想定）オプションつき。Apache POI 不使用（標準ライブラリのみ）
- **設定**: 打ち合わせリマインドON/OFF、背景テーマ（ライト／ダーク／端末の設定に従う）
- **使い方・ヘルプ画面**

### 課金・広告

- **無料枠クレジット制**: 月5回まで無料で要約（`CreditPolicy.MONTHLY_FREE_CREDITS`）。リワード広告視聴で追加、要約失敗時は自動返却。1回の文字起こしは約20,000字（60〜80分）まで
- **Pro（未実装）**: 要約→文字起こしの根拠リンク、ヒアリング分析、エクスポート形式・透かし・PDFパスワードの解放を想定。月¥980／年¥7,800（Billing 実装時に確定）。ゲート機構（`billing/ProAccess`・`ui/common/ProGate`）は実装済みだが `gatingEnabled = false` で無効
- **広告**: AdMob。バナー（各一覧の下部・商談詳細の300x250）、インタースティシャル（要約リクエスト直後）、リワード（クレジット切れ時）。既定はGoogle公式テスト広告。`local.properties` の `ADMOB_USE_PRODUCTION_ADS=true` で本番ユニットに切替

## 技術スタック

| 分類 | 内容 |
|---|---|
| 言語 | Kotlin 2.4.0 |
| UI | Jetpack Compose（Material3、Compose BOM 2026.08.00）。配色は M3 baseline purple。ダークテーマ対応 |
| 画面遷移 | Navigation Compose 2.9.8 |
| DB | Room 2.8.4（KSPでコード生成）。現在 version 15 |
| バックグラウンド | WorkManager（リマインド通知の周期実行） |
| 通信 | OkHttp 5.5.0 + kotlinx.serialization.json 1.11.0（Retrofit不使用、手組みHTTP） |
| 要約AI | Anthropic Claude（Messages API、tool_use、モデル `claude-haiku-4-5-20251001`）。APIキーはアプリに持たず自前の中継 Cloudflare Worker（`server/`）経由 |
| 音声認識 | Android標準 `SpeechRecognizer` のオンデバイス版（`createOnDeviceSpeechRecognizer`）。音声は保存しない |
| 広告 | AdMob（`play-services-ads` 25.4.0） |
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
├── billing/                 # Pro ゲート機構(ProAccess。現状 無効)
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
- 未完了: Google Play Billing（定期購入）、APIキープロキシ フェーズ2 の有効化、Pro 機能（F4 根拠リンク・F6 ヒアリング分析）
