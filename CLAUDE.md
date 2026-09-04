# CLAUDE.md

Claude Codeがこのリポジトリで作業する際のガイド。ユーザー向けの説明は `README.md` を参照。

## プロジェクト概要

フリーランス・個人事業主向けの商談録音・要約Androidアプリ「商談メモ」。録音→オンデバイス音声認識で文字起こし→Claude APIで構造化要約(サマリー/決定事項/ToDo/次回打ち合わせ/懸念点)→クライアント別に保存、が中心機能。元の設計仕様書は `C:\projects\商談メモアプリ_設計仕様書.md`(このリポジトリの外にある)。

## 技術スタック

Kotlin 2.4.0 / Jetpack Compose(Material3、BOM 2026.08.00) / Navigation Compose / Room 2.8.4(KSP) / OkHttp+kotlinx.serialization(Retrofit不使用の手組みHTTP) / AGP 9.1.0 / play-services-ads 25.4.0。DIコンテナは使わず`MeetingNotesApp`(Applicationクラス)で手動DI。詳細は `README.md` の技術スタック表を参照。

## 重要なディレクトリ・ファイル

- `app/src/main/java/com/meetingnotes/MeetingNotesApp.kt` — DB/Repository/AdMob初期化の起点。新しいDAOやリポジトリメソッドを追加したら、ここでの配線漏れがないか確認する
- `app/src/main/java/com/meetingnotes/ui/MeetingViewModel.kt` — 録音〜要約〜保存の状態機械(`RecordingPhase`: Countdown/Recording/Stopping/Editing)。録音画面・結果画面の両方から参照される共有ViewModel(`Navigation.kt`のNavHost外側で1つだけ生成)
- `app/src/main/java/com/meetingnotes/data/remote/AnthropicClient.kt` — 要約クライアント。APIキーはアプリに持たず、`server/` の中継Worker(`SUMMARY_PROXY_URL`)へ `{transcript}` をPOSTする。Workerが Anthropic のレスポンスをそのまま返すためパース処理(`MessagesResponse`/`SummaryDto`)は不変。プロンプト・toolスキーマ・モデルは **Worker側(`server/src/index.ts`)** にある
- `server/` — 要約プロキシ(Cloudflare Worker、TypeScript)。デプロイ手順は `server/README.md`。秘密情報(`ANTHROPIC_API_KEY`, `APP_TOKEN`)は `wrangler secret` 管理でリポジトリに入らない
- `app/src/main/java/com/meetingnotes/data/MeetingRepository.kt` — 全DAOを束ねる単一リポジトリ。新機能を足す時はまずここにメソッドを足す
- `app/src/main/java/com/meetingnotes/data/local/MeetingNotesDatabase.kt` — Room DB定義。現在 version = 7(v6 = 商談フェーズ列、v7 = `client_briefing` テーブル。`feature/solo-crm` ブランチ)

## アーキテクチャ・設計上の重要事項

- **フォルダ/グループのパターンが2箇所ある**: 商談を整理する「フォルダ」(`FolderEntity`、クライアントに紐付く)と、クライアントを整理する「グループ」(`ClientGroupEntity`、独立エンティティ)。どちらも「手動作成→対象に1つだけ割り当て→デフォルト未展開の折りたたみUI→未分類/未展開は常時表示→削除時は対象を消さずSET_NULLで未分類に戻す」という同じ設計パターンを踏襲している。片方に機能を足すときはもう片方への横展開が必要か確認する(実際に「フォルダに名前変更・削除UIが無い」という抜けが過去に発生した)
- **DBスキーマ変更時は正式なMigrationを書く**(2026-09-01〜。以前の`fallbackToDestructiveMigration(true)`全面適用から変更した): `MeetingNotesApp.kt`は`fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4)`のみ。**version 5(クローズドテスト初回配信)以降は破壊的フォールバックしない**ため、v5→v6以降の変更で`data/local/Migrations.kt`に`Migration`を書かないとアプリ更新時にクラッシュする(=書き忘れ防止)。手順: (1) Entity変更+`MeetingNotesDatabase`の`version`を+1 (2) ビルドで`app/schemas/<db>/<新version>.json`が生成される→**コミット** (3) `Migrations.kt`に`MIGRATION_x_y`を追加し`databaseMigrations`に含める (4) `app/src/androidTest`に`MigrationTestHelper`でテスト追加。`exportSchema = true`。v1〜v4は開発中・旧テストビルドのみなので破壊的マイグレーション許容
- **共有ViewModelのライフサイクル**: `MeetingViewModel`はNavHost外側で生成され画面をまたいで生存する。録音中の状態(カウントダウンJob等)を持つため、画面遷移で離脱する経路(戻るボタン等)では必ず`cancelRecordingFlow()`を呼んで後始末すること
- **エクスポートと画面表示の項目構成は揃える設計**: `MeetingExportContentBuilder.kt`(`ExportBlock` の共通モデル。PDF/Word/Markdown が共有)と`ui/common/MeetingSummarySections.kt`(画面表示)は意図的に同じ項目順序(サマリー→決定事項→懸念点・注意点→ToDo→次回打ち合わせ)を保つ。片方の順序を変えたらもう片方も確認する。エクスポート形式は `export/` に per-format のオブジェクト(`PdfExporter`/`WordExporter`/`MarkdownExporter`/`ExcelExporter`/`CsvExporter`/`IcsExporter`)。`ExcelExporter`/`CsvExporter` は `todos: List<TodoEntity>` のみを受け **ToDo 一覧(タスク/担当/期限/完了)だけ**を出力(xlsx シート名 "ToDo")。`IcsExporter` は `nextMeetingDate` が ISO 日付/日時のときだけ VEVENT を生成(未定なら null)。`MeetingDetailScreen` の `ExportOptionsDialog` は形式を3グループ(議事録ぜんぶ=PDF/Word/Markdown、ToDoリストだけ=Excel/CSV、次回打ち合わせ=.ics)で表示し、`IcsExporter.hasUsableDate(nextMeetingDate)` が false のときは .ics グループを非表示。いずれも Apache POI 不使用・標準ライブラリのみ(.docx/.xlsx は OOXML zip を手組み、CSV は RFC 4180 + UTF-8 BOM)
- **配色**: アプリは `MaterialTheme{}`(引数なし)= M3 標準の紫を使う(カスタム ColorScheme なし、ダークテーマなし)。例外として「作成」系アクション(クライアント追加 FAB・フォルダ/グループ作成アイコン)は `ui/theme/CreateActionColors.kt` の `CreateActionBlue`(#1565C0)。ヘルプ画面(`ui/help/HelpScreen.kt`)はカード + アイコン + セクション別アクセント色(紫/青/緑/スレート、`Accent*` 定数)のデザイン
- **フォルダ/グループ作成の UI**: クライアント一覧・アーカイブ(`ClientDetailScreen`)ともに **TopAppBar の `actions` に `Icons.Filled.CreateNewFolder`(青 tint)**。片方の見た目を変えたらもう片方も揃える
- **アーカイブの検索・並び替え**: `ui/client/MeetingArchiveSearch.kt`(純粋関数の `object`、単体テスト対象)に集約。検索は LIKE 相当のメモリ内部分一致(タイトル→サマリー→決定事項→懸念点→ToDo→文字起こし全文 の優先順で最初の一致をラベル付きプレビュー表示)。FTS は不採用(個人利用の件数では LIKE で十分・日本語の途中一致が確実)。ToDo 検索用に `TodoDao.observeByClient`(meetings と JOIN)を追加。並び替え(`MeetingSortOrder`)は日本語 `Collator`。並び替え設定はメモリ保持(画面離脱でリセット)。検索中はフォルダ表示をやめフラットな結果リストにする

## コーディング上のルール

- **Composeのスコープメンバー関数を明示的にimportしない**: `Modifier.weight()`(RowScope/ColumnScope)、`ExposedDropdownMenu`(ExposedDropdownMenuBoxScope)、`Button`のcontentラムダ内の暗黙Row配置などは、明示的に`import`すると内部APIの同名シンボルと衝突してコンパイルエラーになる。`Row{}`/`Column{}`/`ExposedDropdownMenuBox{}`のブロック内で暗黙レシーバのまま使う
- `Modifier.menuAnchor(MenuAnchorType...)`は非推奨。`ExposedDropdownMenuAnchorType`を使う
- AGP 9.1.0はKotlinサポートを内蔵しているため、`org.jetbrains.kotlin.android`プラグインは併用しない(`kotlin.plugin.compose`と`kotlin.plugin.serialization`のみ適用)
- Compose BOM 2026.08系を使う場合は`compileSdk = 37`が必須(35/36だとAAR metadataエラー)
- 危険な権限(RECORD_AUDIO等)を使う新機能を追加する場合、マニフェスト宣言だけでなく**実行時パーミッションリクエストの実装まで必ずセットで**確認する(過去に実装漏れがあった、後述)

## テスト方法

```bash
./gradlew.bat testDebugUnitTest          # JVMユニットテスト(エミュレータ不要)
./gradlew.bat connectedDebugAndroidTest  # インストルメンテーションテスト(実機/エミュレータ必須)
```

エミュレータでの手動確認が必要な場合、`adb`はフルパス指定が必要(PATHに無いことがある):
```
/c/Users/zhong/AppData/Local/Android/Sdk/platform-tools/adb.exe
```

### adb操作の既知の落とし穴(Windows/Git Bash環境)

- `adb shell input keyevent 111`(ESCAPE)はGboardの「元に戻す」に割り当てられており、直前に入力したテキストを消す。キーボードを閉じたいだけなら入力欄以外をタップする
- `adb shell input tap`で座標指定する際、スクリーンショットの表示解像度と実機解像度の換算を手計算するとズレやすい。`adb shell uiautomator dump`→該当要素の`bounds`から中心座標を計算する方が確実
- Room DBの実ファイル名は`meeting-notes.db`(ハイフン、アンダースコアではない)
- DBを直接編集する場合: `adb exec-out run-as <pkg> cat //data/data/<pkg>/databases/<name>`(`//`二重スラッシュでWindows Git BashのMSYSパス変換を抑止、`exec-out`必須)で`.db`/`.db-wal`/`.db-shm`を取得し、ローカルの`.db-shm`を削除してからPythonの`sqlite3`モジュールで開く(stale shmがWALを空と誤認させることがある)。編集後は`PRAGMA wal_checkpoint(TRUNCATE); PRAGMA journal_mode=DELETE;`で単一ファイルに統合してから書き戻す
- 日本語テキストを`adb shell input text`で送る場合、IME予測変換の影響で文字化けすることがある(スクリーンショットで確認し、必要なら`%s`でスペースを区切るか単語ごとに送る)

## ビルド・実行方法

```bash
./gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.manaapps.meetingnotes/com.meetingnotes.MainActivity
```

**パッケージ名に注意**: ストアの識別子(`applicationId`)は `com.manaapps.meetingnotes`、コード上のパッケージ名(`namespace`)は `com.meetingnotes` で**両者が異なる**。`adb` でアプリを指定する時・`run-as` する時・DBパスは `com.manaapps.meetingnotes` を使う。Activityクラスの完全名は `com.meetingnotes.MainActivity`。DBの絶対パスは `/data/data/com.manaapps.meetingnotes/databases/meeting-notes.db`。

## 変更時に注意すべき箇所

- 不要なリファクタリングは行わない。既存のコードパターン(ViewModelのファクトリDSL、Repository経由のDAOアクセス、フォルダ/グループの共通パターン等)に合わせて実装する
- 既存機能への影響を最小限にする。特に`MeetingViewModel`(録音〜保存の中心)や`MeetingRepository`(全機能が経由する)を変更する際は、他画面への影響を必ず確認する
- DBスキーマを変更する場合はEntityにデフォルト値を持つフィールドとして追加すると、既存のテストコードの修正が不要になることが多い(実例: `endedAt: Long? = null`, `groupId: Long? = null`)
- テスト(ユニット+インストルメンテーション)は改修のたびに実行し、可能であればエミュレータでの実機確認も行う

## 現在の開発状況(2026-09-03時点)

MVP相当の機能は一通り実装済み。Google Play Console でのクローズドテストを回している段階。すべての変更はコミット・push 済み(`origin/main`、`github.com/nebu965039-gamecreate/MeetingNotes`、**Public**)。

現在 `versionCode = 9` / `versionName = "0.1.8"`(= クローズドテスト5回目。前回配信 vc4/0.1.3 以降の変更をまとめて配信)。Play Console 提出の詳細な進捗・手順は `docs/` を参照(`play-console-checklist.md`、`play-data-safety.md`、`play-store-listing.md`、`release-notes.md` の「5回目」)。

### 対応済み(Play Console クローズドテスト準備)

- リリース署名設定(`signingConfigs`、`keystore.properties` 方式、Play App Signing)。ユーザーがアップロード鍵 `upload-keystore.jks` を生成済み(ローカルのみ・未コミット)。`./gradlew.bat bundleRelease` で署名済み AAB が出る
- プライバシーポリシー(`docs/privacy-policy.md/html`)。GitHub Pages で公開: `https://nebu965039-gamecreate.github.io/MeetingNotes/privacy-policy.html`
- データセーフティ フォームの記入内容(`docs/play-data-safety.md`)
- ストア掲載テキスト(`docs/play-store-listing.md`)+ アイコン512×512 / フィーチャーグラフィック1024×500 / スクリーンショット4枚(`docs/store-assets/`)
- **アプリアイコン刷新**(AGPテンプレート→「ふきだし+メモ罫線」/ M3 baseline purple `#6750A4`。`ic_launcher_foreground/monochrome/background`)
- `applicationId` を `com.manaapps.meetingnotes` に確定(公開後変更不可)。namespace は `com.meetingnotes` のまま
- `allowBackup="false"` + `data_extraction_rules.xml`(クラウドバックアップ・端末間転送を除外)
- **使い勝手の改善**(2026-09-01〜02): 無料枠を月3→**月5回**(`CreditPolicy.MONTHLY_FREE_CREDITS`)。**「使い方・ヘルプ」画面**(`ui/help/HelpScreen.kt`、`Routes.HELP`、クライアント一覧の TopAppBar に ? アイコン)。`TranscriptPreprocessor` に隣接重複文の除去・記号/長音の正規化を追加(音声認識のセッション連結で重複しやすいため)。**1回の文字起こし上限 約20,000字**(≈60〜80分、`AnthropicClient.MAX_TRANSCRIPT_CHARS` と Worker の `MAX_TRANSCRIPT_CHARS` を揃える。超過時は要約前に明示エラー・クレジット消費なし)。Worker: system/tools を prompt cache 対象に、ユーザーメッセージ冒頭に「現在の日付」を付与(次回打ち合わせの年ズレ対策)
- **要約フローの調整**(2026-09-02): tool に `strict: true`(スキーマ厳密化。Worker が 400 なら自動で strict なしにフォールバック、`STRICT_TOOL` env で無効化も可)。`max_tokens` 1500→2000(長時間商談での切り詰め防止)。**インタースティシャル広告を要約完了後→要約リクエスト直後**に移動(待ち時間に表示、`InterstitialAdController` の頻度キャップ 60分→90秒)。`ResultScreen` の要約中表示を段階メッセージ(「決定事項を抽出中...」等)に。真のトークンストリーミングは tool_use 構造化出力とは相性が悪く未実装(UX上の利点が小さいため)
- **広告収益の見直し**(2026-09-02): `TranscriptPreprocessor` に相槌のみの文の除去・つなぎ言葉の追加削減を追加(要約コスト削減)。`BannerAdView` に `BannerAdFormat`(アダプティブ帯 / 300x250 MediumRectangle)を追加し、**商談詳細画面に300x250バナーを新設**(次回打ち合わせセクションとエクスポートの間)、**商談アーカイブ画面の bottomBar にもアダプティブバナーを新設**(録音開始ボタンの上)。録音画面に経過時間表示 + 50分超で区切りを促す警告、編集画面に文字数カウンタ + 上限超過時は要約ボタンを無効化(上限に達する前に気づけるように)
- **録音の安全性**(2026-09-02): 録音の「停止」を誤タップ防止のため**スライド操作**に変更(`SlideToStop`、`Modifier.draggable`)。**下書き自動保存**(`data/RecordingDraftStore`、SharedPreferences。録音中は数秒おき + 編集中は都度保存、保存成功で消去)。プロセス終了しても文字起こしが残り、クライアント一覧の先頭の復元カードから再開できる。`RecordingScreen` に `BackHandler` を追加(**従来は録音中にシステムバックで戻ると認識器が止まらず孤立していたバグを修正**)。録音中の戻る操作は中止確認ダイアログを挟む
- **APIキープロキシ フェーズ1**(2026-09-01): `server/`(Cloudflare Worker、TS)。アプリは `AnthropicClient` から Worker へ `{transcript}` を POST するだけ。`ANTHROPIC_API_KEY` はアプリから削除、`SUMMARY_PROXY_URL` / `SUMMARY_PROXY_APP_TOKEN` を `local.properties` から読む。プロンプト/toolスキーマ/モデルは Worker 側に移動。**ユーザーがデプロイ済み**: `https://meetingnotes-summary-proxy.manaapps.workers.dev/summarize`(疎通・実要約とも確認済み)
- **APIキープロキシ フェーズ2 コード実装済み**(2026-09-01、未有効化): Worker `src/integrity.ts`(Play Integrity トークンを GCP サービスアカウント経由で `decodeIntegrityToken` 検証、`PLAY_INTEGRITY_ENABLED` = off/audit/enforce)、KV による日次上限(`DAILY_REQUEST_CAP`、`RL` バインディング任意)。アプリ `data/remote/IntegrityTokenProvider.kt`(Standard Integrity API、`PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` 未設定なら null 返却で無害)、`AnthropicClient` が `x-integrity-token` ヘッダを付与、`requestHash` = SHA-256(transcript) hex を app/Worker で一致させる。deps: `com.google.android.play:integrity:1.4.0` + `kotlinx-coroutines-play-services:1.9.0`。有効化手順は `server/README.md`「フェーズ2」
- **AdMob 本番ユニットID登録済み・切替はフラグ制御**(2026-08-31〜09-01): 本番アカウント `ca-app-pub-7474417689976149`。`build.gradle.kts` は `local.properties` の `ADMOB_USE_PRODUCTION_ADS`(既定 false)で本番/テストを切替。**クローズドテスト・ローカル開発はすべてテスト広告のまま**(Google公式テストID)= 無効トラフィックのリスクなし。本番/オープンテスト用の AAB をビルドするときだけ `ADMOB_USE_PRODUCTION_ADS=true` にする。App ID は `manifestPlaceholders["admobAppId"]`、ユニットIDは `BuildConfig.ADMOB_*_UNIT_ID`。本番切替後に実機テスターへテスト広告を出す用の `ADMOB_TEST_DEVICE_IDS`(カンマ区切り)→ `MeetingNotesApp` で `RequestConfiguration.setTestDeviceIds`

### 「1人CRM」機能拡張(2026-09-04〜、方向性 承認済み・`feature/solo-crm` ブランチで開発)

競合分析の結果、汎用議事録アプリとの差別化のため **「記録」から「一人商談のやりきり支援」へ** 軸を移す。基本設計・コスト試算・競合分析は Artifact「1人CRM 基本設計」(`https://claude.ai/code/artifact/be51e1cf-e7c3-4962-a2aa-e416f87a2605`)。決定事項:

- **無料**: F1 フォローボード / F2 前回のおさらい(ブリーフィング) / F3 商談フェーズ / F5 フォローアップ下書き生成
- **Pro**: F4 要約→文字起こしの根拠リンク / F6 ヒアリング分析。Pro 価格 **月¥980 / 年¥7,800**(Billing 実装時に確定)
- 音声保存は**しない**(プライバシー訴求維持)。フェーズは8段階(初回接触/ヒアリング/提案/見積提示/検討中/成約/保留/失注)
- 実装順: **P1 F1 → P2 F3(+v6 マイグレーション)→ P3 F2+F5 → P4 Billing+F4+F6**
- 無料機能(F1・F2・F3・F5)をまとめて次回クローズドテスト(v0.1.9 想定)へ
- **P1 完了**(`6a45e97`): `MeetingDao.observeLatestMeetingPerClient`(射影 `ClientLatestMeeting`)、`FollowupRules`(純粋関数)、`FollowupBoard` composable、`ClientListViewModel.followups`。閾値 14日
- **P2 完了**: `DealPhase`(enum、`data/model`)、`MeetingEntity` に `dealPhase`/`phaseOverride` 列、**DB version 6 + `MIGRATION_5_6`**(`app/schemas/.../6.json` コミット、`MigrationTest` androidTest 追加・通過)。Worker の tool スキーマに `dealPhase` enum + SYSTEM_PROMPT ルール9。`SummaryDto`/`toDomain`/`MeetingSummary`/`saveMeeting` 対応。`ui/common/DealPhaseChip.kt`(`DealPhaseChip` + `DealPhasePickerDialog` + `MeetingEntity.effectivePhase()`)。アーカイブの `MeetingRow` と `MeetingDetailScreen` にチップ(タップで変更)。F1 のフォロー理由にフェーズ表示、WON/LOST は除外
- **P3 完了**: (F5)Worker `POST /followup`(tool なしのプレーン補完、共通ヘルパー `guard`/`generateText`)、`AnthropicClient.generateFollowup`、`MeetingDetailViewModel.FollowupState`、商談詳細に「フォローアップの下書きを作る」+ `FollowupDialog`(丁寧/カジュアル、コピー/共有)。(F2)Worker `POST /briefing`、`AnthropicClient.generateBriefing`、`client_briefing` テーブル + `ClientBriefingDao` + **DB version 7 + `MIGRATION_6_7`**(`7.json` コミット、`MigrationTest` に 5→7 チェーン追加・通過)。`ui/briefing/`(`BriefingScreen` + `BriefingViewModel`)、`Routes.BRIEFING`。2回目以降の録音は「録音開始」→ 前回のおさらい画面 →「録音を始める」。`ClientDetailScreen` の ⋮ に「前回のおさらい」。おさらいは商談が増えたら再生成(`sourceMeetingCount`)
- **Worker は要 `wrangler deploy`**: dealPhase スキーマ(P2)+ `/briefing` `/followup` エンドポイント(P3)。デプロイ前は該当機能がエラーになる

### 未完了のタスク(優先度順)

1. **APIキープロキシ フェーズ2 の有効化**(製品版公開前): コードは実装済み(`server/` + アプリ)。ユーザーが GCP/Play Console 設定を行って有効化する段階。(a) KV による全体日次上限 (b) Cloudflare ダッシュボードの IP レート制限 (c) **Play Integrity**(app: `IntegrityTokenProvider`、Worker: `src/integrity.ts`、`PLAY_INTEGRITY_ENABLED` で off/audit/enforce)。手順は `server/README.md` の「フェーズ2」。(d) 端末ごとのクレジット管理をサーバー側へ、は AdMob SSV・プライバシーポリシー更新を伴うため Billing と合わせて別タスク
2. Google Play Billing Library(定期購入)の実装(Play Console 側のアプリ登録・商品設定が前提)
3. 不正リセット対策フェーズ2(端末ごとのクレジット管理をサーバー側へ)。上記プロキシ フェーズ2に統合
4. 透かしのON/OFFをサブスク状態で自動判定(Billing 実装後)
4b. **エクスポート形式のサブスク制限**(Billing 実装後): 形式は実装済み(PDF/Word/Markdown=議事録全体、Excel/CSV=ToDoのみ、.ics=次回打ち合わせ)。**ロック表示の仕組みも実装済み**: `billing/ProAccess`(`gatingEnabled`/`isPro`、現状どちらも false 固定)、`ui/common/ProGate.kt`(`ProGate` = グレーアウト+金枠+左上「Pro」バッジ〈王冠アイコン、`ui/theme/ProColors.kt` の `ProGold`〉、`ProPaywallDialog`)。`ExportOptionsDialog` の非PDFチップと透かしON/OFFを `ProGate` で包み済み。**Billing + paywall が揃うまで `ProAccess.gatingEnabled` は false**(= ロックは一切表示されない)。有効化するには (1) `gatingEnabled` を購入状態に連動させる (2) `isPro` を実購入判定へ (3) `ProPaywallDialog` に登録導線を追加。追加候補の形式: HTML / 画像カード(PNG)
5. PDF への画像ロゴ埋め込み(レイアウトは改善済み。テキストワードマークを画像に差し替え。アイコンSVGを流用可能)
6. **要約項目のプリセット化**(サブスク実装後・Pro機能想定): 現状は5項目固定(サマリー/決定事項/懸念点・注意点/ToDo/次回打ち合わせ)がWorkerのスキーマからRoomのカラム、表示、エクスポートまで全レイヤーにハードコード。「商談」「社内MTG」「採用面談」等のプリセットを切替できるようにする案。プリセットごとに固定スキーマを持てば prompt cache は維持可。DBは項目を可変にするため固定カラム→JSON1カラム等へ寄せる v6 マイグレーションが必要。自由入力のカスタム項目(動的スキーマ生成)はさらに大きいので当面対象外

## 既知の問題

- **【修正済み・実機再検証待ち】録音の音声認識エラー(`ERROR_CLIENT` / code=5)**: テスターの実機で発生(2026-08-31)。`TranscriptionManager` がセッション再開を `onEndOfSpeech` と `onResults`/`onError` で二重に行い、かつコールバック内から同期的に `startListening` していたのが原因。再開をメインHandler経由の遅延実行に一本化・多重起動ガード追加・code=5/8 は認識器を作り直して自動リトライ(連続5回超で打ち切り)に変更。エミュレータは日本語モデルが無く code=5 の再現不可のため、**テスターの実機での再テストが必要**
- **【修正済み・実機再検証待ち】下部ボタンがナビゲーションバーと重なる**: targetSdk 35+ の edge-to-edge 強制が原因。`ClientDetailScreen`(録音開始)/`ClientListScreen`/`ResultScreen` の `bottomBar` に `navigationBarsPadding()` を追加。エミュレータで解消を確認、実機(3ボタン/ジェスチャー両方)の確認待ち
- **【要再検証】実機での日本語音声認識の精度**: エミュレータは音声入力できないため未検証。テスターの実地確認が進行中
- (対応済み)DBマイグレーション: v5以降は正式Migrationを書く方式に変更済み(上記「アーキテクチャ・設計上の重要事項」参照)。v1〜v4からの更新のみ破壊的
