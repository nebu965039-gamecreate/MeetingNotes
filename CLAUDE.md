# CLAUDE.md

Claude Codeがこのリポジトリで作業する際のガイド。ユーザー向けの説明は `README.md` を参照。

## プロジェクト概要

フリーランス・個人事業主向けの商談録音・要約Androidアプリ「商談メモ」。録音→オンデバイス音声認識で文字起こし→Claude APIで構造化要約(サマリー/決定事項/ToDo/次回打ち合わせ/懸念点)→クライアント別に保存、が中心機能。元の設計仕様書は `C:\projects\商談メモアプリ_設計仕様書.md`(このリポジトリの外にある)。

## 技術スタック

Kotlin 2.4.0 / Jetpack Compose(Material3、BOM 2026.08.00) / Navigation Compose / Room 2.8.4(KSP) / OkHttp+kotlinx.serialization(Retrofit不使用の手組みHTTP) / AGP 9.1.0 / play-services-ads 25.4.0。DIコンテナは使わず`MeetingNotesApp`(Applicationクラス)で手動DI。詳細は `README.md` の技術スタック表を参照。

## 重要なディレクトリ・ファイル

- `app/src/main/java/com/meetingnotes/MeetingNotesApp.kt` — DB/Repository/AdMob初期化の起点。新しいDAOやリポジトリメソッドを追加したら、ここでの配線漏れがないか確認する
- `app/src/main/java/com/meetingnotes/ui/MeetingViewModel.kt` — 録音〜要約〜保存の状態機械(`RecordingPhase`: Countdown/Recording/Stopping/Editing)。録音画面・結果画面の両方から参照される共有ViewModel(`Navigation.kt`のNavHost外側で1つだけ生成)
- `app/src/main/java/com/meetingnotes/data/remote/AnthropicClient.kt` — 要約クライアント(Claude Messages API、tool_use)
- `app/src/main/java/com/meetingnotes/data/MeetingRepository.kt` — 全DAOを束ねる単一リポジトリ。新機能を足す時はまずここにメソッドを足す
- `app/src/main/java/com/meetingnotes/data/local/MeetingNotesDatabase.kt` — Room DB定義。現在 version = 5

## アーキテクチャ・設計上の重要事項

- **フォルダ/グループのパターンが2箇所ある**: 商談を整理する「フォルダ」(`FolderEntity`、クライアントに紐付く)と、クライアントを整理する「グループ」(`ClientGroupEntity`、独立エンティティ)。どちらも「手動作成→対象に1つだけ割り当て→デフォルト未展開の折りたたみUI→未分類/未展開は常時表示→削除時は対象を消さずSET_NULLで未分類に戻す」という同じ設計パターンを踏襲している。片方に機能を足すときはもう片方への横展開が必要か確認する(実際に「フォルダに名前変更・削除UIが無い」という抜けが過去に発生した)
- **DBスキーマ変更時は正式なMigrationを書く**(2026-09-01〜。以前の`fallbackToDestructiveMigration(true)`全面適用から変更した): `MeetingNotesApp.kt`は`fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4)`のみ。**version 5(クローズドテスト初回配信)以降は破壊的フォールバックしない**ため、v5→v6以降の変更で`data/local/Migrations.kt`に`Migration`を書かないとアプリ更新時にクラッシュする(=書き忘れ防止)。手順: (1) Entity変更+`MeetingNotesDatabase`の`version`を+1 (2) ビルドで`app/schemas/<db>/<新version>.json`が生成される→**コミット** (3) `Migrations.kt`に`MIGRATION_x_y`を追加し`databaseMigrations`に含める (4) `app/src/androidTest`に`MigrationTestHelper`でテスト追加。`exportSchema = true`。v1〜v4は開発中・旧テストビルドのみなので破壊的マイグレーション許容
- **共有ViewModelのライフサイクル**: `MeetingViewModel`はNavHost外側で生成され画面をまたいで生存する。録音中の状態(カウントダウンJob等)を持つため、画面遷移で離脱する経路(戻るボタン等)では必ず`cancelRecordingFlow()`を呼んで後始末すること
- **エクスポートと画面表示の項目構成は揃える設計**: `MeetingExportContentBuilder.kt`(PDF/Word/メール共通)と`ui/common/MeetingSummarySections.kt`(画面表示)は意図的に同じ項目順序(サマリー→決定事項→懸念点・注意点→ToDo→次回打ち合わせ)を保つ。片方の順序を変えたらもう片方も確認する

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

## 現在の開発状況(2026-08-31時点)

MVP相当の機能は一通り実装済み。Google Play Console でのクローズドテストを開始し、テスター報告の不具合対応を回している段階。すべての変更はコミット・push 済み(`origin/main`、`github.com/nebu965039-gamecreate/MeetingNotes`、**Public**)。

現在 `versionCode = 3` / `versionName = "0.1.2"`。Play Console 提出の詳細な進捗・手順は `docs/` を参照(`play-console-checklist.md` の「あなたの作業手順 STEP 0-7」、`play-data-safety.md`、`play-store-listing.md`、`release-notes.md`)。

### 対応済み(Play Console クローズドテスト準備)

- リリース署名設定(`signingConfigs`、`keystore.properties` 方式、Play App Signing)。ユーザーがアップロード鍵 `upload-keystore.jks` を生成済み(ローカルのみ・未コミット)。`./gradlew.bat bundleRelease` で署名済み AAB が出る
- プライバシーポリシー(`docs/privacy-policy.md/html`)。GitHub Pages で公開: `https://nebu965039-gamecreate.github.io/MeetingNotes/privacy-policy.html`
- データセーフティ フォームの記入内容(`docs/play-data-safety.md`)
- ストア掲載テキスト(`docs/play-store-listing.md`)+ アイコン512×512 / フィーチャーグラフィック1024×500 / スクリーンショット4枚(`docs/store-assets/`)
- **アプリアイコン刷新**(AGPテンプレート→「ふきだし+メモ罫線」/ M3 baseline purple `#6750A4`。`ic_launcher_foreground/monochrome/background`)
- `applicationId` を `com.manaapps.meetingnotes` に確定(公開後変更不可)。namespace は `com.meetingnotes` のまま
- `allowBackup="false"` + `data_extraction_rules.xml`(クラウドバックアップ・端末間転送を除外)
- **AdMob 本番ユニットID登録済み・切替はフラグ制御**(2026-08-31〜09-01): 本番アカウント `ca-app-pub-7474417689976149`。`build.gradle.kts` は `local.properties` の `ADMOB_USE_PRODUCTION_ADS`(既定 false)で本番/テストを切替。**クローズドテスト・ローカル開発はすべてテスト広告のまま**(Google公式テストID)= 無効トラフィックのリスクなし。本番/オープンテスト用の AAB をビルドするときだけ `ADMOB_USE_PRODUCTION_ADS=true` にする。App ID は `manifestPlaceholders["admobAppId"]`、ユニットIDは `BuildConfig.ADMOB_*_UNIT_ID`。本番切替後に実機テスターへテスト広告を出す用の `ADMOB_TEST_DEVICE_IDS`(カンマ区切り)→ `MeetingNotesApp` で `RequestConfiguration.setTestDeviceIds`

### 未完了のタスク(優先度順)

1. **APIキー保護のバックエンドプロキシ**(公開前に必須、ホスティング先未決定): 現状 `ANTHROPIC_API_KEY` は BuildConfig 埋め込みでリバースエンジニアリング可能。クローズドテスト中はユーザー判断で保留中(専用キー + Anthropic コンソールの支出上限で緩和)。オープンテスト/本番公開の前に必須
2. Google Play Billing Library(定期購入)の実装(Play Console 側のアプリ登録・商品設定が前提)
3. 不正リセット対策フェーズ2(匿名照合サーバー、ホスティング先未決定)。プロキシ化と相乗り可能
4. 透かしのON/OFFをサブスク状態で自動判定(Billing 実装後)
5. PDF への画像ロゴ埋め込み(レイアウトは改善済み。テキストワードマークを画像に差し替え。アイコンSVGを流用可能)

## 既知の問題

- **【修正済み・実機再検証待ち】録音の音声認識エラー(`ERROR_CLIENT` / code=5)**: テスターの実機で発生(2026-08-31)。`TranscriptionManager` がセッション再開を `onEndOfSpeech` と `onResults`/`onError` で二重に行い、かつコールバック内から同期的に `startListening` していたのが原因。再開をメインHandler経由の遅延実行に一本化・多重起動ガード追加・code=5/8 は認識器を作り直して自動リトライ(連続5回超で打ち切り)に変更。エミュレータは日本語モデルが無く code=5 の再現不可のため、**テスターの実機での再テストが必要**
- **【修正済み・実機再検証待ち】下部ボタンがナビゲーションバーと重なる**: targetSdk 35+ の edge-to-edge 強制が原因。`ClientDetailScreen`(録音開始)/`ClientListScreen`/`ResultScreen` の `bottomBar` に `navigationBarsPadding()` を追加。エミュレータで解消を確認、実機(3ボタン/ジェスチャー両方)の確認待ち
- **【要再検証】実機での日本語音声認識の精度**: エミュレータは音声入力できないため未検証。テスターの実地確認が進行中
- (対応済み)DBマイグレーション: v5以降は正式Migrationを書く方式に変更済み(上記「アーキテクチャ・設計上の重要事項」参照)。v1〜v4からの更新のみ破壊的
