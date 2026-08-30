# Play Console クローズドテスト提出 チェックリスト — 商談メモ

最終更新: 2026-08-30

---

## あなたの作業手順(この順番で進める)

### STEP 0. Play Console デベロッパーアカウント(未登録なら)
- [ ] https://play.google.com/console で登録($25 / 本人確認。個人アカウントは登録から数日かかる場合あり)
- ※ 2023年11月以降作成の個人アカウントは、**製品版公開の前に「テスター20人が14日間継続」**が必要。クローズドテストの実施自体には不要だが、本番公開までのスケジュールに影響する。

### STEP 1. プライバシーポリシーを公開する(コードは確定済み)
- [ ] GitHub: リポジトリ Settings > Pages > Build and deployment > Source = "Deploy from a branch"、Branch = `main` / `/docs` を選択して Save
- [ ] 数分後 `https://nebu965039-gamecreate.github.io/MeetingNotes/privacy-policy.html` をブラウザで開いて表示を確認
- [ ] このURLを控える(STEP 5 で使う)

### STEP 2. アップロード鍵を作る
- [ ] プロジェクトルートで実行(パスワードと組織情報を対話入力):
  ```
  keytool -genkeypair -v -keystore upload-keystore.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
  ```
- [ ] `keystore.properties.example` を `keystore.properties` にコピーし、`storePassword` / `keyPassword` を入力した値に、`keyAlias=upload` に設定
- [ ] **`upload-keystore.jks` とパスワードを安全な場所にバックアップ**(パスワードマネージャ等。紛失すると更新版を出せなくなる)
- [ ] AAB をビルド:
  ```
  ./gradlew.bat bundleRelease
  ```
  → `app/build/outputs/bundle/release/app-release.aab`(STEP 6 でアップロード)

### STEP 3. グラフィック素材(`docs/store-assets/` に作成済み)
- [x] アプリアイコン 512×512 → `アプリアイコン-512.png`(アプリ本体のランチャーアイコンも差し替え済み)
- [x] フィーチャーグラフィック 1024×500 → `フィーチャーグラフィック-1024x500.png`
- [x] スクリーンショット 4枚 → `01`〜`04`
- [ ] 録音中の音声レベル可視化画面を**実機で1枚撮り直し**(エミュレータは認識エラー表示になる)
- 編集して調整したい場合はデザインキャンバス(要約とともに送付したリンク)で色・文言を変えて PNG 書き出し可

### STEP 4. Play Console でアプリを作成する
- [ ] 「アプリを作成」→ アプリ名「商談メモ」、デフォルトの言語=日本語、アプリ、無料
- [ ] 宣言事項(デベロッパー プログラム ポリシー / 米国輸出法)にチェック

### STEP 5. 「アプリのコンテンツ」を全部埋める(左メニュー)
`docs/play-store-listing.md` と `docs/play-data-safety.md` を見ながら:
- [ ] プライバシーポリシー = STEP 1 のURL
- [ ] アプリのアクセス権 = 「すべての機能が制限なしで利用可能」(ログイン不要)
- [ ] 広告 = 「はい、広告が含まれています」
- [ ] コンテンツのレーティング = IARC 質問票に回答(暴力・性的表現等すべて「なし」)
- [ ] ターゲットユーザーと子ども = 対象年齢 18歳以上、子ども向けではない
- [ ] データセーフティ = `docs/play-data-safety.md` の通りに入力
- [ ] 政府によるアプリではない / 金融商品ではない 等の申告

### STEP 6. ストアの掲載情報 + クローズドテスト作成
- [ ] 「ストアの掲載情報」: アプリ名・簡単な説明・詳しい説明を `docs/play-store-listing.md` からコピー、STEP 3 の画像をアップロード
- [ ] 「テスト > クローズドテスト」: トラックを作成(または既定の「Alpha」を使用)
- [ ] テスターを追加(メールアドレスのリスト または Google グループ)
- [ ] 「新しいリリースを作成」→ STEP 2 の `app-release.aab` をアップロード、リリースノート記入
- [ ] Play App Signing の同意(初回アップロード時に表示。デフォルトで有効化)
- [ ] 「保存」→「リリースのレビュー」→「クローズドテストへの公開を開始」
- [ ] 審査通過後(通常数時間〜数日)、テスターに **オプトインURL** を共有

### STEP 7. テスト実施中に並行して進める
- [ ] Anthropic: クローズドテスト専用の API キーを発行し、コンソールで**支出上限を低く設定**
- [ ] API キーのバックエンドプロキシ化(本番公開のブロッカー。最小構成で約1日 / Integrity検証込みで2〜4日)
- [ ] 本番公開前の残タスクは下記「セクション9」を参照

---

## 1. リリース署名(コード側は対応済み)

- [x] `app/build.gradle.kts` に `signingConfigs` を追加。`keystore.properties`(gitignore対象)から読む方式。
- [x] `keystore.properties.example` を追加。
- [x] `.gitignore` に `keystore.properties` / `*.jks` / `*.keystore` を追加。
- [ ] **アップロード鍵を生成**(プロジェクトルートで実行):
  ```
  keytool -genkeypair -v -keystore upload-keystore.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
  ```
- [ ] `keystore.properties.example` を `keystore.properties` にコピーし、パスワード等を記入。
- [ ] **Play App Signing を有効化**(Play Console の新規アプリはデフォルト有効)。アプリ署名鍵は Google が管理し、上記アップロード鍵で署名した AAB をアップロードする。
- [ ] リリースビルド確認:
  ```
  ./gradlew.bat bundleRelease
  # 出力: app/build/outputs/bundle/release/app-release.aab
  ```
  ※ `keystore.properties` 未設定だと未署名 AAB になる(ビルドは通るが Console にアップロード不可)。

## 2. バージョン

- 現在: `versionCode = 1` / `versionName = "0.1.0"`(初回提出はこのままでよい)
- 次回アップロード時は `versionCode` をインクリメントする。

## 3. `android:allowBackup`【決定済み: false】

- [x] `AndroidManifest.xml` を `android:allowBackup="false"` に変更。
- [x] `res/xml/data_extraction_rules.xml` を追加し、クラウドバックアップ + 端末間転送の両方を除外。
      (`allowBackup="false"` 単体では Android 12+ の端末間転送は止まらないため `<device-transfer>` の除外が必要)
- [x] プライバシーポリシー第4項を「バックアップ・転送の対象外」の記述に確定(md/html 両方)。

## 4. プライバシーポリシー

- [x] `docs/privacy-policy.md` / `docs/privacy-policy.html` を作成。
- [x] 提供者名(manaapps)・連絡先(contact.manaapps@gmail.com)・バックアップ記述を確定。
- [ ] 公開URLを用意する。最も簡単なのは **GitHub Pages**:
  - リポジトリ Settings > Pages > Source を `main` / `/docs` に設定 → `https://nebu965039-gamecreate.github.io/MeetingNotes/privacy-policy.html`
- [ ] そのURLを Play Console >「アプリのコンテンツ」>「プライバシーポリシー」に登録。

## 5. データセーフティ フォーム

- [x] 記入ガイド `docs/play-data-safety.md` を作成。
- [ ] ガイドに沿って Console で入力。
- [ ] 「D. 未確定・要判断ポイント」(allowBackup / Anthropic 保持 / AdMob項目)を確定。

## 6. ストア掲載情報

- [x] テキスト素材ドラフト `docs/play-store-listing.md` を作成。
- [ ] アプリ名・簡単な説明・詳しい説明を Console に入力。
- [ ] **グラフィック素材を作成**(コードでは用意不可):
  - [ ] アプリアイコン 512×512(現在テンプレートアイコン。差し替え推奨)
  - [ ] フィーチャーグラフィック 1024×500
  - [ ] スクリーンショット 2枚以上(録音 / 要約結果 / クライアント一覧 / エクスポート)

## 7. アプリのコンテンツ(Console の必須項目)

- [ ] コンテンツ レーティング(IARC 質問票)
- [ ] ターゲット ユーザー(18歳以上想定、子ども向けではない)
- [ ] 広告の申告:「広告を含む」にチェック(AdMob 使用)
- [ ] データセーフティ(上記5)
- [ ] 政府製アプリではない / 金融商品ではない 等の申告
- [ ] アプリのアクセス権(ログイン不要 = テスターが全機能にアクセス可能、を明記)

## 8. クローズドテスト トラックの作成

- [ ] Console >「テスト」>「クローズドテスト」でトラック作成
- [ ] テスターのメールアドレスリスト(Google グループ or 個別)を登録
- [ ] AAB をアップロード、リリースノート記入(`docs/release-notes.md` の初回クローズドテスト用をコピー)
- [ ] オプトインURLを友人テスターへ共有

## 9. 提出前に残る既知の懸念(クローズドテスト中は許容、正式公開前に必須)

| 項目 | 状態 | 公開前の必須度 |
|---|---|---|
| APIキーがクライアント埋め込み(`ANTHROPIC_API_KEY` が BuildConfig) | 未対応 | **必須**(バックエンドプロキシ化) |
| AdMob が全て Google テストID(App ID・各広告ユニット) | 未対応 | 本番配信前に本番ID化。※テストIDのまま本番トラック配信は AdMob ポリシー違反 |
| `fallbackToDestructiveMigration(true)`(更新でローカルデータ消失) | 未対応 | 正式公開前に Migration 実装 or 方針決定 |
| `isMinifyEnabled = false`(難読化なし) | 現状維持 | 任意。有効化する場合 kotlinx.serialization / Room / AdMob の keep ルールが必要 |
| `GEMINI_API_KEY` / `GeminiClient.kt`(未使用の検証コード) | リリースに同梱される | 任意。公開前に削除推奨 |
| カスタムアプリアイコン | 未対応 | ストア審査は通るが商用品質としては差し替え推奨 |
| 署名付きリリースビルドの実機動作確認 | 済(2026-08-30、使い捨て鍵でエミュレータ起動・署名検証を確認) | 本番アップロード鍵での AAB を実機で最終確認しておくこと |
