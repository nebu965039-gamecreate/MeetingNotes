# Play Console クローズドテスト提出 チェックリスト — 商談メモ

最終更新: 2026-08-30

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
- [ ] AAB をアップロード、リリースノート記入
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
| 署名付きリリースビルドの実機動作確認 | 未実施 | **必須**(minify なしでも AAB の実機起動は確認する) |
