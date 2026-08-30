# CLAUDE.md

Claude Codeがこのリポジトリで作業する際のガイド。ユーザー向けの説明は `README.md` を参照。

## プロジェクト概要

フリーランス・個人事業主向けの商談録音・要約Androidアプリ「商談メモ」。録音→オンデバイス音声認識で文字起こし→Claude APIで構造化要約(サマリー/決定事項/ToDo/次回打ち合わせ/懸念点)→クライアント別に保存、が中心機能。元の設計仕様書は `C:\projects\商談メモアプリ_設計仕様書.md`(このリポジトリの外にある)。

## 技術スタック

Kotlin 2.4.0 / Jetpack Compose(Material3、BOM 2026.08.00) / Navigation Compose / Room 2.8.4(KSP) / OkHttp+kotlinx.serialization(Retrofit不使用の手組みHTTP) / AGP 9.1.0 / play-services-ads 25.4.0。DIコンテナは使わず`MeetingNotesApp`(Applicationクラス)で手動DI。詳細は `README.md` の技術スタック表を参照。

## 重要なディレクトリ・ファイル

- `app/src/main/java/com/meetingnotes/MeetingNotesApp.kt` — DB/Repository/AdMob初期化の起点。新しいDAOやリポジトリメソッドを追加したら、ここでの配線漏れがないか確認する
- `app/src/main/java/com/meetingnotes/ui/MeetingViewModel.kt` — 録音〜要約〜保存の状態機械(`RecordingPhase`: Countdown/Recording/Stopping/Editing)。録音画面・結果画面の両方から参照される共有ViewModel(`Navigation.kt`のNavHost外側で1つだけ生成)
- `app/src/main/java/com/meetingnotes/data/remote/AnthropicClient.kt` — 本番で使用する要約クライアント。`GeminiClient.kt`は開発中の一時検証用で**現在は未使用**(削除はしていない)
- `app/src/main/java/com/meetingnotes/data/MeetingRepository.kt` — 全DAOを束ねる単一リポジトリ。新機能を足す時はまずここにメソッドを足す
- `app/src/main/java/com/meetingnotes/data/local/MeetingNotesDatabase.kt` — Room DB定義。現在 version = 5

## アーキテクチャ・設計上の重要事項

- **フォルダ/グループのパターンが2箇所ある**: 商談を整理する「フォルダ」(`FolderEntity`、クライアントに紐付く)と、クライアントを整理する「グループ」(`ClientGroupEntity`、独立エンティティ)。どちらも「手動作成→対象に1つだけ割り当て→デフォルト未展開の折りたたみUI→未分類/未展開は常時表示→削除時は対象を消さずSET_NULLで未分類に戻す」という同じ設計パターンを踏襲している。片方に機能を足すときはもう片方への横展開が必要か確認する(実際に「フォルダに名前変更・削除UIが無い」という抜けが過去に発生した)
- **DBスキーマ変更はversion番号を上げるだけでよい**: `MeetingNotesApp.kt`で`fallbackToDestructiveMigration(true)`を設定済みのため、正式なMigrationコードは書かなくてよい(プレリリース段階のため許容している判断)。ただし本番リリース後にこの前提のままだと**アプリ更新のたびに利用者のローカルデータが消える**ので、正式公開前に見直すこと
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

## 現在の開発状況(2026-08-30時点)

MVP相当の機能は一通り実装済み。直近はGoogle Play Consoleでのクローズドテスト提出に向けた準備を進めていた。

**Gitの状態に注意**: リポジトリは初期化・イニシャルコミット済みだが、直近の以下の変更は**未コミット**(`git status`で確認可能):
- `ads/BannerAdView.kt`(アダプティブバナー化)
- `ui/client/ClientDetailScreen.kt`・`ClientDetailViewModel.kt`(フォルダの名前変更・削除UI追加)
- `ui/client/ClientListScreen.kt`(クライアントグループのフォルダ風デザイン刷新)
- `ui/recording/RecordingScreen.kt`(RECORD_AUDIO実行時権限リクエストの追加、下記の重大バグ修正)

作業を再開する際は、まずこれらの変更をコミットするかどうかユーザーに確認すること。

## 未完了のタスク

優先度順:

1. **Play Consoleクローズドテスト提出の準備**
   - リリースビルドの署名設定(`build.gradle.kts`に`signingConfigs`が未設定。Play App Signing推奨)
   - プライバシーポリシーページの作成・URL準備(提出必須)
   - データセーフティフォームの記入(収集データ・第三者提供の申告)
   - ストア掲載情報(アイコン512×512、フィーチャーグラフィック1024×500、スクリーンショット2枚以上)
2. **APIキー保護のバックエンドプロキシ**(公開前に必須、ホスティング先未決定): 現状`ANTHROPIC_API_KEY`はBuildConfigに直接埋め込まれておりリバースエンジニアリングで読み取り可能
3. AdMob本番アカウント・広告ユニットIDへの差し替え(現在は全てGoogle公式テストID。クローズドテスト中はテストIDのままでよい)
4. Google Play Billing Library(定期購入)の実装(Play Console側のアプリ登録・商品設定が前提)
5. 不正リセット対策フェーズ2(匿名照合サーバー、ホスティング先未決定)
6. カスタムアプリアイコンへの差し替え(現在AGPデフォルトのテンプレートアイコン)
7. PDFへの画像ロゴ埋め込み(レイアウト自体は改善済み、テキストのワードマークを画像に差し替え)
8. 透かしのON/OFFをサブスク状態で自動判定する仕組み(Billing実装後に対応)

## 既知の問題

- **【重大・修正済み、要再検証】RECORD_AUDIO実行時権限の実装漏れ**: マニフェストに宣言はあったが実行時リクエストが存在せず、録音機能が実機で全く動作しない状態だった(2026-08-30発見・修正)。`RecordingScreen.kt`に`ActivityResultContracts.RequestPermission`による実行時リクエストを追加し、エミュレータで権限ダイアログの表示・許可後のエラー解消までは確認済み。**ただし実機での日本語音声認識自体が正しく動くかは未検証**(エミュレータは音声入力できないため)。友人テスターでの実機確認が次のステップ
- リリースビルド用の署名設定が無い(上記「未完了のタスク」参照)
- `fallbackToDestructiveMigration(true)`のため、DBスキーマを変更するアプリ更新のたびにローカルデータが失われる(プレリリース段階の割り切り。正式公開前に要見直し)
