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
- `app/src/main/java/com/meetingnotes/data/local/MeetingNotesDatabase.kt` — Room DB定義。現在 version = 14(… v11 = `meetings.meetingType` + `user_credits` オンライン回数、v12 = `todos.dueDate` + `clients.email/phone`、v13 = `client_contacts` テーブル(担当者)、v14 = `client_contacts.email/phone`。`feature/solo-crm` ブランチ)

## アーキテクチャ・設計上の重要事項

- **フォルダ/グループのパターンが2箇所ある**: 商談を整理する「フォルダ」(`FolderEntity`、クライアントに紐付く)と、クライアントを整理する「グループ」(`ClientGroupEntity`、独立エンティティ)。どちらも「手動作成→対象に1つだけ割り当て→デフォルト未展開の折りたたみUI→未分類/未展開は常時表示→削除時は対象を消さずSET_NULLで未分類に戻す」という同じ設計パターンを踏襲している。片方に機能を足すときはもう片方への横展開が必要か確認する(実際に「フォルダに名前変更・削除UIが無い」という抜けが過去に発生した)
- **DBスキーマ変更時は正式なMigrationを書く**(2026-09-01〜。以前の`fallbackToDestructiveMigration(true)`全面適用から変更した): `MeetingNotesApp.kt`は`fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4)`のみ。**version 5(クローズドテスト初回配信)以降は破壊的フォールバックしない**ため、v5→v6以降の変更で`data/local/Migrations.kt`に`Migration`を書かないとアプリ更新時にクラッシュする(=書き忘れ防止)。手順: (1) Entity変更+`MeetingNotesDatabase`の`version`を+1 (2) ビルドで`app/schemas/<db>/<新version>.json`が生成される→**コミット** (3) `Migrations.kt`に`MIGRATION_x_y`を追加し`databaseMigrations`に含める (4) `app/src/androidTest`に`MigrationTestHelper`でテスト追加。`exportSchema = true`。v1〜v4は開発中・旧テストビルドのみなので破壊的マイグレーション許容
- **共有ViewModelのライフサイクル**: `MeetingViewModel`はNavHost外側で生成され画面をまたいで生存する。録音中の状態(カウントダウンJob等)を持つため、画面遷移で離脱する経路(戻るボタン等)では必ず`cancelRecordingFlow()`を呼んで後始末すること
- **エクスポートと画面表示の項目構成は揃える設計**: `MeetingExportContentBuilder.kt`(`ExportBlock` の共通モデル。PDF/Word/Markdown が共有)と`ui/common/MeetingSummarySections.kt`(画面表示)は意図的に同じ項目順序(サマリー→決定事項→懸念点・注意点→ToDo→次回打ち合わせ)を保つ。片方の順序を変えたらもう片方も確認する。エクスポート形式は `export/` に per-format のオブジェクト(`PdfExporter`/`WordExporter`/`MarkdownExporter`/`ExcelExporter`/`CsvExporter`/`IcsExporter`)。`ExcelExporter`/`CsvExporter` は `todos: List<TodoEntity>` のみを受け **ToDo 一覧(タスク/担当/期限/完了)だけ**を出力(xlsx シート名 "ToDo")。`IcsExporter` は `nextMeetingDate` が ISO 日付/日時のときだけ VEVENT を生成(未定なら null)。`MeetingDetailScreen` の `ExportOptionsDialog` は形式を3グループ(議事録ぜんぶ=PDF/Word/Markdown、ToDoリストだけ=Excel/CSV、次回打ち合わせ=.ics)で表示し、`IcsExporter.hasUsableDate(nextMeetingDate)` が false のときは .ics グループを非表示。いずれも Apache POI 不使用・標準ライブラリのみ(.docx/.xlsx は OOXML zip を手組み、CSV は RFC 4180 + UTF-8 BOM)
- **配色**: M3 標準の紫パレット(`lightColorScheme()`/`darkColorScheme()` とも引数なしの baseline。カスタム色は使わない)。**2026-09-06〜ダークテーマ対応**: `MainActivity.kt` が `MeetingNotesApp.themeModeState`(`ui/theme/ThemeMode`: LIGHT/DARK/SYSTEM、`ui/theme/ThemePrefs.kt` で永続化)を読み `darkColorScheme()`/`lightColorScheme()` を出し分けて `MaterialTheme(colorScheme = ...)` に渡す。設定は `ui/settings/SettingsScreen.kt`(「背景(テーマ)」行→ダイアログで3択)。`app.setThemeMode()` が Compose の `MutableState` を書き換えるため全画面へ即時反映される。例外として「作成」系アクション(クライアント追加 FAB・フォルダ/グループ作成アイコン)は `ui/theme/CreateActionColors.kt` の `CreateActionBlue`(#1565C0)、ホーム画面「進行中のフェーズ」は `ui/theme/PhaseTrackerColors.kt` で明暗共通固定 — これらは意図的にテーマ非追従。ヘルプ画面(`ui/help/HelpScreen.kt`)はカード + アイコン + セクション別アクセント色(紫/青/緑/スレート、`Accent*` 定数)のデザイン(ダークテーマでの見え方は未検証)
- **フォルダ/グループ作成の UI**: クライアント一覧・アーカイブ(`ClientDetailScreen`)ともに **TopAppBar の `actions` に `Icons.Filled.CreateNewFolder`(青 tint)**。片方の見た目を変えたらもう片方も揃える
- **アーカイブの検索・並び替え**: `ui/client/MeetingArchiveSearch.kt`(純粋関数の `object`、単体テスト対象)に集約。検索は LIKE 相当のメモリ内部分一致(タイトル→サマリー→決定事項→懸念点→ToDo→文字起こし全文 の優先順で最初の一致をラベル付きプレビュー表示)。FTS は不採用(個人利用の件数では LIKE で十分・日本語の途中一致が確実)。ToDo 検索用に `TodoDao.observeByClient`(meetings と JOIN)を追加。並び替え(`MeetingSortOrder`)は日本語 `Collator`。並び替え設定はメモリ保持(画面離脱でリセット)。検索中はフォルダ表示をやめフラットな結果リストにする
- **ホーム画面(2026-09-05〜、NavHost の start destination)**: 旧来は `ClientListScreen`(クライアント一覧)がアプリ起動直後の画面だったが、`ui/home/HomeScreen.kt`(+`HomeViewModel.kt`)に置き換え。`ClientListScreen` は「クライアント一覧」タイルから遷移するサブ画面になり、`onMeetingSelected`/`onRecoverDraft`/`onShowNotifications`/`onHelp` は撤去(`onClientSelected`/`onBack` のみ)。ホーム画面の構成(2026-09-07 に並び替え): ヘッダー(日付+挨拶+設定+ヘルプ)→ 下書き復元カード(あれば)→ アクションタイル(録音を始める/クライアント一覧/予定表/通知、いずれも `surfaceContainerLow` の枠で囲みグループを明確化)→ **直近の予定**(`UpcomingBoard`、旧「次回の予定」。`surfaceContainerLow` 枠+`secondaryContainer` カード、プレビュー3件+ヘッダーに「すべて表示」)→ **ToDo**(`FollowupBoard`、`surfaceContainerLow` 枠+内部 `tertiaryContainer` カード、`heightIn(max=152.dp)+verticalScroll` で常時3件分の高さ・最大10件まで内部スクロール、ヘッダー右に「すべて表示」常時。行タップで**商談詳細**(`meetingId`)へ、各行末尾に「完了」ボタン)→ **進行中のフェーズ**(ステップ・トラッカー。ヒアリング/提案/見積提示/検討中のみ集計、初回接触・保留・成約・失注は対象外。色は `ui/theme/PhaseTrackerColors.kt` で状態色として明暗共通固定)。「もっと見る」表記は全て「すべて表示」に統一し、リンク文字は `TextDecoration.Underline`(件数表示と区別)。フェーズタグの表示は既存の `ui/common/DealPhaseChip.kt` を再利用(新規コンポーネント化しない)。日付ラベル(本日/明日/M-d)は `ui/common/DateLabels.kt` の `relativeDateTimeLabel` に集約しホーム/予定表で共有。「要フォロー」「予定表」それぞれの全件ページは `ui/client/FollowupListScreen.kt` と `ui/schedule/ScheduleScreen.kt`(いずれも専用の軽量 ViewModel、`FollowupRules.compute`/`UpcomingRules.compute` をキャップなしで使う)。「録音を始める」タイル(2026-09-06〜)はクライアントを選ばず**直接録音**を開始する。`Routes.RECORDING_UNASSIGNED`(パス引数なし)→ `RecordingScreen(clientId = -1L)`。要約後の `ResultScreen` で保存先を選ぶ: `MeetingViewModel.isClientAssigned()`(= `clientId >= 0`)が false のとき、タイトル入力の下に「既存から選ぶ/新規登録」の `FilterChip` を出し、既存は `LabeledDropdownField`(`viewModel.clients`)、新規は名前入力+グループ選択(`viewModel.clientGroups`)。保存は `saveMeetingToClient` / `saveMeetingToNewClient`(共通処理 `persistMeeting(targetClientId, title)`。`saveMeetingToNewClient` は要約成功を確認してから `addClient` する = 空クライアントの残留防止)。`ResultScreen` の `onSaved` は `(clientId: Long) -> Unit` に変更、`Navigation.kt` 側で直接録音フロー時はホームまで pop してから `clientDetail(savedClientId)` を開く。`RecordingDraftStore` の下書きは `clientId = -1` でも保存されるようになり(旧 `persistDraft` の `clientId < 0` ガードを撤去)、ホームの復元カードからも `RECORDING_UNASSIGNED` へ復帰できる
- **設定画面 新設(2026-09-06、`ui/settings/`)**: `Routes.SETTINGS`、ホーム画面ヘッダーのヘルプアイコンの左に歯車アイコンから遷移。内容は (1) 打ち合わせのリマインドON/OFF(旧 `NotificationScreen` から移設。`SettingsViewModel` が `ReminderPrefs`/`ReminderScheduler`/`POST_NOTIFICATIONS` 権限リクエストを保持。`NotificationViewModel` からは同ロジックを削除し履歴+予定リストのみに縮小) (2) 背景(テーマ)。`NotificationScreen`/`ClientListScreen`/`ScheduleScreen` のヘッダーは `ClientDetailScreen`(アーカイブ画面)と同じ `TopAppBarDefaults.topAppBarColors(containerColor = primaryContainer, ...)` 配色に統一し、タイトル左にホーム画面の対応タイルと同じアイコン(People/CalendarMonth/Notifications)を表示
- **予定表(`ui/schedule/ScheduleScreen.kt`)拡張**: 月カレンダー(`MonthCalendar`、日曜始まり、予定がある日に丸印)を上部に追加、その下の一覧は日付タップで絞り込み(再タップで解除)。カレンダーは一覧を多く見せるためコンパクト寄り(セル `aspectRatio(1.4f)`・日付丸 24dp・カード padding 10/8dp)、`ScheduleRow` も縦 padding 6dp・クライアント名 `bodyMedium`・行間 8dp。行の ⋮ メニューから「日程を変更」(`NextMeetingDatePickerDialog` 再利用)・「削除」(`ConfirmDialog` → `MeetingEntity.nextMeetingDate` を null クリアするだけで **商談記録自体は消さない**)。FAB「予定を追加」は既存クライアント(商談記録が1件以上ある = `ClientLatestMeeting` に存在)を `LabeledDropdownField` で選び次回予定を新規設定する方式(商談記録の無い新規クライアントには使えない。予定は `MeetingEntity.nextMeetingDate` に紐付く設計のため、これに依存しない独立した「予定」エンティティは無い)
- **要フォロー/次回の予定は0件でも常に表示**: `FollowupBoard`/`UpcomingBoard` から `if (items.isEmpty()) return` を撤去し、0件時は「フォローが必要な商談はありません」等の空状態メッセージを表示
- **クライアント追加・グループ移動はセレクトボックス**: `ClientListScreen` の「クライアントを追加」ダイアログはグループ選択(`ui/common/LabeledDropdownField.kt`、既定「未分類」)付きに変更(`MeetingRepository.addClient(name, groupId)`)。「グループに移動」もセレクトボックス+「変更」ボタンの確定式に変更(旧 RadioButton 一覧から置き換え)。`LabeledDropdownField` は総称型の再利用可能コンポーネントで、`ScheduleScreen` のクライアント選択でも使用

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
- **要約フローの調整**(2026-09-02): tool に `strict: true`(スキーマ厳密化。Worker が 400 なら自動で strict なしにフォールバック、`STRICT_TOOL` env で無効化も可)。`max_tokens` 1500→2000(長時間商談での切り詰め防止)。**インタースティシャル広告を要約完了後→要約リクエスト直後**に移動(待ち時間に表示、`InterstitialAdController` の頻度キャップ 60分→90秒)。**2026-09-07: `submitForSummary` は要約を `async` で走らせ `delay(1200)` 後にまだ未完了のときだけ `tryShow`**(短時間で終わる要約で「結果が出た後に広告」になるのを回避)。`ResultScreen` の要約中表示を段階メッセージ(「決定事項を抽出中...」等)に。真のトークンストリーミングは tool_use 構造化出力とは相性が悪く未実装(UX上の利点が小さいため)
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
- **F1 = ToDo(要フォロー)**: `MeetingEntity.followedUpAt`(**DB version 9 + `MIGRATION_8_9`**)= 「完了」時刻。`FollowupRules.compute`(純粋関数)が **ToDo** を判定: 各クライアントの最新商談で `followedUpAt == null`(=「完了」を押したら**二度と出ない**) & 成約/失注でない & 最終商談から `RECENT_WINDOW_DAYS`(30日)以内、のみ。**次回予定の有無・経過日数は判定に使わない**(打ち合わせが決まっていてもお礼メール等の連絡は別途必要、という考え方)。ソートは録音が新しい順。`FollowupItem` = `client`/`meetingId`/`lastRecordedAt`/`phase`。行タップは `meetingId` で商談詳細へ、各行に「完了」ボタン(`markMeetingFollowedUp`)。
  - **旧仕様の破棄(2026-09-08)**: `FollowupReason`(NEEDS_EMAIL/STALE)・`FollowupItem.daysSince`/`reason`・「フォロー済みでも14日放置で再表示(STALE)」・`hasUpcomingMeeting` チェック・`THRESHOLD_DAYS` は**廃止**(「完了したのに再び出る」混乱と初回導入時の大量表示への対策)。
  - **`FollowupListScreen` は2タブ**(UI 用語は「フォロー」→「ToDo」に統一。コード識別子 `Followup*` は据え置き): 「ToDo」(`FollowupRules.compute`)と「完了」(`MeetingDao.observeFollowedUpMeetings` → `FollowedUpMeeting` 射影、`followedUpAt` 降順)。完了行の「ToDoに戻す」= `clearMeetingFollowedUp`(null)。ホームの `FollowupBoard` も見出し「ToDo」、各行「完了」ボタン、行の説明は「最終 M/d・フェーズ・メール連絡」。ヘッダー右に「すべて表示」リンク(下線)を常時表示。`HomeScreen` に `onOpenMeeting` 追加
- **商談の管理番号(No.)**: `MeetingDetailScreen` のヘッダーに `No. yyyyMMddHHmm`(録音開始 `recordedAt` から都度生成、DB 保存なし。同一分の録音は同番号)。`meetingNo()` は `MeetingDetailScreen.kt` 内の private 関数
- **フォローアップ下書き(2026-09-07)**: 基本は要約時に一緒に生成。`/summarize` の tool スキーマに `followupDraft`(丁寧体1本、ToDo を「次のアクションのお願い」に織り込む)を追加。ただし **`required` には入れない**(長い商談で切り詰められて欠落しても要約全体を失敗させない。アプリは null 許容)。`MeetingSummary.followupDraft` / `SummaryDto` / `toDomain` / `saveMeeting` 対応、**`MeetingEntity.followupDraft`(DB version 10 + `MIGRATION_9_10`、`10.json`、`MigrationTest` 9→10)**。付いていない商談(旧データ / Worker 未更新)は商談詳細で **1回だけ** 後追い生成できる: Worker `POST /followup`(`{summary}` = 要約のプレーンテキスト、`FOLLOWUP_SYSTEM`、`generateText` 経由)、`AnthropicClient.generateFollowup`、`MeetingDetailViewModel.generateFollowup` → `repository.setMeetingFollowupDraft`(`MeetingDao.updateFollowupDraft`)で保存。以後は再生成不可(`FollowupState`: `Idle`/`Loading`/`Ready`/`Empty`/`Error`。トーン切替なし)。Worker `MAX_TOKENS` 2000→2400。**Worker 再デプロイ必要**
- **フェーズタグの見た目統一(2026-09-07)**: `DealPhaseChip` は「枠あり + 塗りつぶし」の1スタイルに固定(`RoundedCornerShape(6dp)`、`labelMedium`)。予定(`UpcomingBoard`/`ScheduleScreen`)・商談詳細・アーカイブ・フォロー一覧すべて同じ見た目。一時追加した `outlined` パラメータは撤去。**配色は `ui/theme/PhaseTagColors.kt`**: アプリ全体が M3 紫系で埋もれるため、各フェーズに別系統色(初回接触=スレート/ヒアリング=琥珀/提案=青/見積提示=ティール/検討中=オレンジ/成約=緑/保留=温グレー/失注=ローズ)を割り当て。色相は不変でライト/ダークの明度を入れ替え(`of(phase, darkTheme)`)。`DealPhaseChip` は `MeetingNotesApp.themeModeState`(LIGHT/DARK/SYSTEM)から実効ダークを判定
- **アーカイブ `MeetingRow` の見た目(2026-09-07)**: タイトルは太字(`titleMedium` + `SemiBold` + `onSecondaryContainer`)で、右端に `DealPhaseChip`(タップで変更)。※白帯背景を一度試したが視認性が悪く撤回。要約プレビューは `conciseSummary()`(先頭1文・最長60字)で見切れ回避
- **P2 完了**: `DealPhase`(enum、`data/model`)、`MeetingEntity` に `dealPhase`/`phaseOverride` 列、**DB version 6 + `MIGRATION_5_6`**(`app/schemas/.../6.json` コミット、`MigrationTest` androidTest 追加・通過)。Worker の tool スキーマに `dealPhase` enum + SYSTEM_PROMPT ルール9。`SummaryDto`/`toDomain`/`MeetingSummary`/`saveMeeting` 対応。`ui/common/DealPhaseChip.kt`(`DealPhaseChip` + `DealPhasePickerDialog` + `MeetingEntity.effectivePhase()`)。アーカイブの `MeetingRow` と `MeetingDetailScreen` にチップ(タップで変更)。F1 のフォロー理由にフェーズ表示、WON/LOST は除外
- **P3 完了**: (F5)Worker `POST /followup`(tool なしのプレーン補完、共通ヘルパー `guard`/`generateText`)、`AnthropicClient.generateFollowup`、`MeetingDetailViewModel.FollowupState`、商談詳細に「フォローアップの下書きを作る」+ `FollowupDialog`(丁寧/カジュアル、コピー/共有)。(F2)Worker `POST /briefing`、`AnthropicClient.generateBriefing`、`client_briefing` テーブル + `ClientBriefingDao` + **DB version 7 + `MIGRATION_6_7`**(`7.json` コミット、`MigrationTest` に 5→7 チェーン追加・通過)。`ui/briefing/`(`BriefingScreen` + `BriefingViewModel`)、`Routes.BRIEFING`。2回目以降の録音は「録音開始」→ 前回のおさらい画面 →「録音を始める」。`ClientDetailScreen` の ⋮ に「前回のおさらい」。おさらいは商談が増えたら再生成(`sourceMeetingCount`)
- **F7 予定カレンダー + リマインド 完了**(P3 に同梱): `data/model/NextMeetingTime`(ISO 日付/日時の解釈)、`MeetingDao.getNextMeetingCandidates`、`updateNextMeeting`、`observeLatestMeetingPerClient` 射影に `meetingId` 追加。`notification_log` テーブル + `NotificationLogDao` + **DB version 8 + `MIGRATION_7_8`**(`8.json` コミット、`MigrationTest` に 7→8 追加)。`notifications/`(`NotificationHelper` チャンネル `meeting_reminders`、`ReminderPrefs` 既定ON、`MeetingReminderWorker` = WorkManager 12h 周期、当日/前日で未発火のものを通知+ログ、`ReminderScheduler` を `MeetingNotesApp.onCreate` で登録)。`POST_NOTIFICATIONS` 権限。`ui/client/UpcomingBoard`(クライアント一覧の「近日の予定」カード)、`ClientListViewModel.upcoming`。商談詳細の「次回打ち合わせ」に日程の手動設定(`NextMeetingDatePickerDialog`)+「カレンダーに追加」(`util/CalendarIntent`、`ACTION_INSERT` インテント・権限不要)。クライアント一覧 TopAppBar のベルアイコン → `ui/notifications/NotificationScreen`(リマインドのON/OFF+権限リクエスト、予定リスト、通知履歴)、`Routes.NOTIFICATIONS`
- **Worker デプロイ済み(2026-09-06)**: dealPhase スキーマ(P2)+ `/briefing` `/followup`(P3)を `wrangler deploy` 済み。以降 `server/src/index.ts` を変更したら再デプロイが必要
- **ホーム画面への集約 完了**(2026-09-05): 上記「アーキテクチャ・設計上の重要事項」の「ホーム画面」項を参照。F1(フォローボード)と F7(予定カレンダー)の UI を `ClientListScreen` から独立した `ui/home/HomeScreen.kt` に統合し、「進行中のフェーズ」ダッシュボード(F3 の集計)を新設。**この変更により P1/F7 完了時点の記述にある `ClientListViewModel.followups`/`.upcoming`、クライアント一覧 TopAppBar のベルアイコン、「クライアント一覧の近日の予定カード」は廃止・移設済み**(履歴として上記は残すが現状と一致しない)。デザイン検討は Artifact「商談メモ ホーム画面デザイン」(`https://claude.ai/code/artifact/f8c9c49e-5532-452c-a2bd-5e97d1ba8752`)
- **リモート会議モード Phase 1(2026-09-08)**: PC/Web会議で相手の声を拾えない問題への対応。録音開始時に `RecordingModePicker` で「対面」/「リモート会議」を選ぶ(`MeetingType` enum、`data/model`)。
  - **対面**: 従来の `TranscriptionManager`(オンデバイス、音声は端末外に出ない)。
  - **リモート会議**: `speech/AudioFileRecorder`(`MediaRecorder` → Opus/Ogg 16kHz 24kbps mono、`AudioSource` は UNPROCESSED→CAMCORDER→MIC)でファイル録音(録音中のライブ文字起こしは無し、音声レベルのみ)。停止 → `RecordingPhase.Transcribing` → `AnthropicClient.transcribeAudio` で Worker `POST /transcribe` へバイナリ送信 → **Cloudflare Workers AI Whisper**(`@cf/openai/whisper-large-v3-turbo`、`wrangler.toml` の `[ai] binding = "AI"`)→ 文字起こし結果を既存の編集フローへ。失敗時は同じファイルで再試行(`retryTranscription`)。
  - **課金**: `ProAccess.isPro` で分岐。Pro = 月40回(`CreditPolicy.ONLINE_TRANSCRIPTION_PRO_MONTHLY`)、無料 = 月1回 + リワード広告で最大+4回(`user_credits.onlineTranscriptionsUsed/Bonus`、月次リセットは `getOrInitCredits` に同梱)。`MeetingRepository.remainingOnlineTranscriptions/consumeOnlineTranscription/grantOnlineTranscriptionBonus`。消費は文字起こし**成功時**。
  - **同意**: 初回リモート選択時に `RemoteConsentPrefs`(SharedPreferences)で「音声をサーバー送信」の同意ダイアログ。
  - `MeetingEntity.meetingType` に記録し、商談詳細のヘッダーとエクスポート(`形式: 対面/リモート会議`)に表示。`RecordingDraftStore.Draft.meetingType` も保存。**DB version 11 + `MIGRATION_10_11`**(`11.json`、`MigrationTest` 10→11)。
  - 音声上限: `AudioFileRecorder.MAX_DURATION_MS`(45分)で自動停止、Worker `MAX_AUDIO_BYTES`(12MB)。**長時間のチャンク分割は Phase 2**。
  - **Worker 再デプロイ必要**(`[ai]` バインディング追加 + `/transcribe`)。プライバシーポリシー / データセーフティの更新は Phase 2(未対応)。コスト試算: Whisper $0.0005/音声分(40分商談で ~¥3)。

- **Tier 1 CRM 補強(2026-09-08)**: 他AIの機能提案(`docs` 外)との差分埋め。**DB version 12 + `MIGRATION_11_12`**(`12.json`、`MigrationTest` 11→12)。
  - **ToDo 期限の日付解決**: Worker の tool スキーマに `todos[].deadlineDate`(ISO or null)を追加。アプリは `deadlineDate ?? TodoDueDate.parse(deadline)` を `TodoEntity.dueDate` に保存(`data/model/TodoDueDate` = "金曜"/"9/15"/"来週月曜" 等をパース、JVM テストあり)。**Worker 再デプロイ必要**
  - **ホーム「やること(期限あり)」カード**(`ui/home/DueTodoBoard`): 期限切れ + 今日 + 3日以内の未完了 ToDo。`TodoDao.observeOpenTodosWithDueDate` → `OpenTodo` 射影。チェックで完了、タップで商談詳細。アクションタイルの直下に配置
  - **ToDo 期限リマインダー**: `MeetingReminderWorker` が本日期限の未完了 ToDo も通知(`notification_log` の `scheduledFor = "todo-<id>-<date>"` で重複防止)
  - **予定表**: カレンダーの丸印に ToDo 期限日を追加。日付選択時に「この日が期限のToDo」一覧
  - **クライアント詳細**: アーカイブの上に「未完了のToDo」セクション
  - **商談詳細**: `TodoRow` に解決済み期限(M/d)を併記
- **クライアント情報 閲覧/編集の分離(2026-09-08)**: `ClientDetailScreen` の ⋮「クライアント情報」→ **`ClientInfoScreen`(閲覧専用、`Routes.CLIENT_INFO`)**: クライアント名/グループ/現在のステータス(=最新商談の実効フェーズ、`DealPhaseChip`)/メール/電話/担当者一覧/備考。TopAppBar の編集アイコン → **`ClientEditScreen`(`Routes.CLIENT_EDIT`)**: 名前/グループ/メール/電話/備考 + 担当者の追加・編集・削除。両画面とも `ClientInfoViewModel`。担当者は **`client_contacts` テーブル(氏名/役職メモ/メール/電話。DB v13 `MIGRATION_12_13` + v14 `MIGRATION_13_14`、`14.json`、`MigrationTest` 12→13/13→14)** + `ClientContactDao`。会社レベルの `clients.email/phone` とは別に**担当者ごとにメール・電話を持てる**。`ClientEntity.memo` の編集導線をここで初めて用意。`updateClientInfo` に `memo` 追加、旧「クライアント名を変更」ダイアログ・`ClientDetailViewModel.updateClientInfo` は撤去。`ClientDetailScreen` に `onOpenClientInfo` パラメータ追加
- 詳細な機能対照(実装済み/一部/未実装/不要/将来)と「案件レイヤー(Tier 2)」の検討は別途

### 未完了のタスク(優先度順)

0. **リモート会議モード Phase 2**: (a) プライバシーポリシー(`docs/privacy-policy.*`)とデータセーフティ(`docs/play-data-safety.md`)に「リモート会議モードでは音声を文字起こしのため一時的に処理・保存しない」を追記 (b) 長時間録音のチャンク分割(45分上限の緩和) (c) `ProAccess.isPro` を実購入判定に(Billing 実装後) (d) Whisper の実コスト・レイテンシ・日本語精度の実測。**Worker の `[ai]` バインディングは Cloudflare 側で Workers AI の有効化が必要な場合あり**
1. **APIキープロキシ フェーズ2 の有効化**(製品版公開前): コードは実装済み(`server/` + アプリ)。ユーザーが GCP/Play Console 設定を行って有効化する段階。(a) KV による全体日次上限 (b) Cloudflare ダッシュボードの IP レート制限 (c) **Play Integrity**(app: `IntegrityTokenProvider`、Worker: `src/integrity.ts`、`PLAY_INTEGRITY_ENABLED` で off/audit/enforce)。手順は `server/README.md` の「フェーズ2」。(d) 端末ごとのクレジット管理をサーバー側へ、は AdMob SSV・プライバシーポリシー更新を伴うため Billing と合わせて別タスク
2. Google Play Billing Library(定期購入)の実装(Play Console 側のアプリ登録・商品設定が前提)
3. 不正リセット対策フェーズ2(端末ごとのクレジット管理をサーバー側へ)。上記プロキシ フェーズ2に統合
4. 透かしのON/OFFをサブスク状態で自動判定(Billing 実装後)
4b. **エクスポート形式のサブスク制限**(Billing 実装後): 形式は実装済み(PDF/Word/Markdown=議事録全体、Excel/CSV=ToDoのみ、.ics=次回打ち合わせ)。**ロック表示の仕組みも実装済み**: `billing/ProAccess`(`gatingEnabled`/`isPro`、現状どちらも false 固定)、`ui/common/ProGate.kt`(`ProGate` = グレーアウト+金枠+左上「Pro」バッジ〈王冠アイコン、`ui/theme/ProColors.kt` の `ProGold`〉、`ProPaywallDialog`)。`ExportOptionsDialog` の非PDFチップと透かしON/OFF、**PDFパスワード保護**を `ProGate` で包み済み。**Billing + paywall が揃うまで `ProAccess.gatingEnabled` は false**(= ロックは一切表示されない)。有効化するには (1) `gatingEnabled` を購入状態に連動させる (2) `isPro` を実購入判定へ (3) `ProPaywallDialog` に登録導線を追加。追加候補の形式: HTML / 画像カード(PNG)
4c. **PDFパスワード保護(Pro機能・実装済み)**(2026-09-05): `export/PdfPasswordProtector.kt`(`com.tom-roush:pdfbox-android` 2.0.27.0)。`PdfExporter` が生成した PDF を PDFBox-Android で読み込み直し、`StandardProtectionPolicy`(ユーザー/所有者パスワード同一、AES-256)で暗号化して上書きする後処理方式。`ExportOptionsDialog` に PDF選択時のみ「パスワードを設定する」トグル(`ProGate`)+ パスワード入力欄(`PasswordVisualTransformation`)。`MeetingDetailViewModel.exportPdf` に `password: String?` / `onError` を追加(`runCatching` で暗号化失敗時は Toast)。**対象は PDF のみ**(Word/Excel/Markdown/CSV/.ics は OOXML暗号化の複雑さ・POI非使用のため技術的に非対応と判断)。APKサイズは PDFBox-Android 追加で約6MB増。パスワードはアプリに保存しない(毎回手入力)
5. PDF への画像ロゴ埋め込み(レイアウトは改善済み。テキストワードマークを画像に差し替え。アイコンSVGを流用可能)
6. **要約項目のプリセット化**(サブスク実装後・Pro機能想定): 現状は5項目固定(サマリー/決定事項/懸念点・注意点/ToDo/次回打ち合わせ)がWorkerのスキーマからRoomのカラム、表示、エクスポートまで全レイヤーにハードコード。「商談」「社内MTG」「採用面談」等のプリセットを切替できるようにする案。プリセットごとに固定スキーマを持てば prompt cache は維持可。DBは項目を可変にするため固定カラム→JSON1カラム等へ寄せる v6 マイグレーションが必要。自由入力のカスタム項目(動的スキーマ生成)はさらに大きいので当面対象外

## 既知の問題

- **【修正済み・実機再検証待ち】録音の音声認識エラー(`ERROR_CLIENT` / code=5)**: テスターの実機で発生(2026-08-31)。`TranscriptionManager` がセッション再開を `onEndOfSpeech` と `onResults`/`onError` で二重に行い、かつコールバック内から同期的に `startListening` していたのが原因。再開をメインHandler経由の遅延実行に一本化・多重起動ガード追加・code=5/8 は認識器を作り直して自動リトライ(連続5回超で打ち切り)に変更。エミュレータは日本語モデルが無く code=5 の再現不可のため、**テスターの実機での再テストが必要**
- **【修正済み・実機再検証待ち】下部ボタンがナビゲーションバーと重なる**: targetSdk 35+ の edge-to-edge 強制が原因。`ClientDetailScreen`(録音開始)/`ClientListScreen`/`ResultScreen` の `bottomBar` に `navigationBarsPadding()` を追加。エミュレータで解消を確認、実機(3ボタン/ジェスチャー両方)の確認待ち
- **【要再検証】実機での日本語音声認識の精度**: エミュレータは音声入力できないため未検証。テスターの実地確認が進行中
- **【未解決】PCスピーカーから出る相手の声が認識されない / 認識ごとの効果音**: `SpeechRecognizer` が自分でマイクを開くと近接音声向け処理(ノイズ抑制・AGC)が入り、離れた相手の声を削る。加えて多くの端末はマイクを開くたび効果音を鳴らす。試した対策と結果:
  - `EXTRA_AUDIO_SOURCE` に UNPROCESSED を指定(2026-09-06)→ extra が無視され効果なし → **revert 済み**
  - 自前 `AudioRecord` → `ParcelFileDescriptor` パイプ → `EXTRA_AUDIO_SOURCE` で recognizer に供給する `MicAudioFeeder`(2026-09-07)→ **オンデバイス認識器がパイプ入力を受け付けず、テキストが一切出なくなった(音声レベルだけ反応)ため revert**(`3299e34` 版に戻した)
  - 2026-09-07、`3299e34` ベースに (a) 録音中は `STREAM_MUSIC` の音量そのものを 0 に落として `stop()` で復元(`adjustStreamVolume(MUTE)` は SYSTEM/NOTIFICATION には DND 権限が無いと効かないため)。**元の音量を SharedPreferences(`audio_mute`)にも保存し、録音中にプロセスが落ちても次回起動時に `restoreLeftoverMediaVolume()`(`MeetingNotesApp.onCreate`)で復元** (b) `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` 等に 20秒を指定してセッションが沈黙で切れる頻度を下げる(端末が無視する場合あり) (c) `onPartialResults` を live 表示に反映。**効果音を端末レベルで完全に消すには DND 権限が必要で未実装**
- **診断ログ(2026-09-07)**: `util/DiagnosticsLog`(端末内・上限64KB・外部送信なし)。`MeetingNotesApp.onCreate` で `init()` + キャッチされない例外を既定ハンドラに委譲しつつ記録。`TranscriptionManager` が録音1回ごとに結果(長さ・文字数・再開回数・エラーコード別回数、正常終了か打ち切りか)を1行追記。ヘルプ画面(`HelpScreen`)末尾の「診断情報」カードから内容を「共有」/「消去」できる。音声・商談内容は含まない
- **CI(2026-09-07)**: `.github/workflows/android.yml`(push: main/feature/**、PR: main → `testDebugUnitTest lintDebug assembleDebug`。Android SDK は runner 同梱、`local.properties` 不要)。`MigrationIntegrityTest`(JVM)= `app/schemas` の最大バージョンを現行 DB バージョンとみなし、v5→現行まで各1段の Migration が `databaseMigrations` にあること・全て+1ステップ・重複なし・最終到達バージョン一致 を検証(実 SQL は androidTest の `MigrationTest`)
  - Zoom/Teams の文字起こし連携は不可(サードパーティにライブ字幕を公開していない)。`MediaProjection` の再生音キャプチャも VoIP 音声(`USAGE_VOICE_COMMUNICATION`)は仕様上キャプチャ不可。残る手は「自前 AudioRecord の PCM をアプリ内 STT(要ネット・プライバシー方針と非両立)」か「スピーカーフォン運用+マイクを近づける」の案内のみ
- (対応済み)DBマイグレーション: v5以降は正式Migrationを書く方式に変更済み(上記「アーキテクチャ・設計上の重要事項」参照)。v1〜v4からの更新のみ破壊的
