# 商談メモ (MeetingNotes)

フリーランス・個人事業主向けの商談録音・要約Androidアプリ。商談を録音し、オンデバイス音声認識で文字起こしした後、Claude APIで「サマリー・決定事項・ToDo・次回打ち合わせ・懸念点」を自動的に構造化して記録する。

元の設計仕様書: `C:\projects\商談メモアプリ_設計仕様書.md`

## 主な機能

- **録音 → 文字起こし → 要約**: 録音開始ボタン押下後に3秒カウントダウン→録音(音声レベル可視化バー付き)→停止→文字起こしテキストを手動編集可能→Claude APIで構造化要約
- **クライアント管理**: クライアントの追加・リネーム・削除、手動作成したグループへの分類(フォルダ風デザインで表示、展開/折りたたみ可)
- **商談(議事録)管理**: クライアントごとに商談を記録。クライアント内で手動フォルダに整理可能(展開/折りたたみ、デフォルト未展開)。商談タイトルの設定・変更
- **ToDo管理**: 商談ごとに抽出されたToDoの完了チェック
- **エクスポート**: PDF(透かしオプション付き)/Word(.docx)/メール文面(プレーンテキスト)。それぞれ「共有」と「保存して終了」(SAFファイルピッカー)を選択可能
- **無料枠クレジット制**: 月3回まで無料で要約可能。リワード広告視聴で追加クレジット獲得。要約失敗時は自動返却
- **広告**: AdMob(リワード広告・インタースティシャル広告・アダプティブバナー広告)。release ビルドは本番の広告ユニットID、debug ビルドはGoogle公式テストIDを使用(`build.gradle.kts` の `buildTypes` で分岐)

## 技術スタック

| 分類 | 内容 |
|---|---|
| 言語 | Kotlin 2.4.0 |
| UI | Jetpack Compose(Material3)、Compose BOM 2026.08.00 |
| 画面遷移 | Navigation Compose 2.9.8 |
| DB | Room 2.8.4(KSPでコード生成) |
| 通信 | OkHttp 5.5.0 + kotlinx.serialization.json 1.11.0(Retrofit不使用、手組みのHTTPクライアント) |
| 要約AI | Anthropic Claude API(Messages API、tool_use機能呼び出し、モデル: `claude-haiku-4-5-20251001`) |
| 音声認識 | Android標準`SpeechRecognizer`のオンデバイス版(`createOnDeviceSpeechRecognizer`)。ML Kitではない |
| 広告 | AdMob(`play-services-ads` 25.4.0) |
| ビルド | AGP 9.1.0 / Gradle 9.3.1 / KSP 2.3.11 |
| DI | 無し(手動DI、`MeetingNotesApp`でRepository等をlazy生成) |

- `minSdk = 33`(Android 13以上、オンデバイス音声認識の都合)
- `targetSdk = compileSdk = 37`

## ディレクトリ構成

```
app/src/main/java/com/meetingnotes/
├── MainActivity.kt              # エントリーポイント。MaterialThemeでNavHostをラップ
├── MeetingNotesApp.kt           # Applicationクラス。Room DB・Repository・AdMob初期化(lazy)
├── ads/                         # AdMob広告(RewardedAdController/InterstitialAdController/BannerAdView)
├── data/
│   ├── CreditPolicy.kt          # 無料枠クレジットの月次リセット判定
│   ├── MeetingRepository.kt     # 全DAOを束ねる単一のリポジトリ
│   ├── local/                   # Room: Entity/DAO/Database(ClientEntity, MeetingEntity, FolderEntity,
│   │                             #        ClientGroupEntity, TodoEntity, UserCreditsEntity 等)
│   ├── model/                   # ドメインモデル(MeetingSummary等、DTOと分離)
│   └── remote/                  # AnthropicClient(本番で使用)、GeminiClient(一時検証用、現在未使用)
├── export/                      # PDF(PdfExporter)/Word(WordExporter)/メール文面のエクスポート
├── speech/                      # TranscriptionManager(SpeechRecognizerラッパー)、TranscriptPreprocessor
├── ui/
│   ├── MeetingViewModel.kt      # 録音〜要約〜保存フローを管理する中心的ViewModel
│   ├── Navigation.kt            # 画面遷移(NavHost)の定義
│   ├── client/                  # クライアント一覧画面・クライアント詳細(商談アーカイブ)画面
│   ├── common/                  # 共通ダイアログ(TextInputDialog/ConfirmDialog)・共有Composable
│   ├── meeting/                 # 商談詳細画面(要約表示・ToDo・エクスポート)
│   ├── recording/                # 録音画面(カウントダウン→録音→停止→編集)
│   └── result/                   # 要約結果画面(保存前のタイトル編集)
└── util/                        # DeviceIdentifier(端末ID匿名化)等

app/src/test/java/...            # JVMユニットテスト(gradlewで実行、エミュレータ不要)
app/src/androidTest/java/...     # インストルメンテーションテスト(実機/エミュレータが必要)
```

## セットアップ

1. `local.properties.example` を `local.properties` にコピーし、以下を設定する(このファイルはgitignore対象):
   ```
   sdk.dir=<Android SDKのパス>
   ANTHROPIC_API_KEY=<Anthropic APIキー>
   ```
2. Android Studio または `gradlew` でビルド

## ビルド・実行

```bash
./gradlew.bat assembleDebug          # デバッグAPKビルド
./gradlew.bat testDebugUnitTest      # JVMユニットテスト
./gradlew.bat connectedDebugAndroidTest  # インストルメンテーションテスト(実機/エミュレータ必須)
```

エミュレータへのインストール:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 開発方針・現在の状況

Claude Codeでの開発を継続する場合は `CLAUDE.md` を参照。現在の実装状況・未完了タスク・既知の問題もそちらにまとめている。
