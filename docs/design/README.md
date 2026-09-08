# docs/design — 画面デザイン調整用の資料

個人的に UI デザインを Figma などで見直すための下敷き。

| ファイル | 中身 |
|---|---|
| `screen-inventory.md` | 全画面のルート・遷移・構成要素・ダイアログ一覧 |
| `design-tokens.md` | M3 baseline の使用状況 + アプリ独自の色・タイポ・余白・シェイプ |
| `capture-screens.sh` | 各画面のスクショを対話式で撮る adb スクリプト(→ `screens/`、git 管理外) |
| `style-guide.html` | 上記2つをまとめた閲覧用スタイルガイド(Artifact として公開可) |

## Figma での進め方

1. `bash docs/design/capture-screens.sh` で現状の画面を撮る
2. [Material 3 Design Kit](https://www.figma.com/community/file/1035203688168086460) を複製
3. `design-tokens.md` の「独自の色」をローカル変数として追加
4. スクショを参照に貼りながら再デザイン(ライト/ダーク両方)
5. できたフレーム(または赤字仕様)を渡す → Compose に実装

## 変えない前提

- M3 テーマ機構(`light/darkColorScheme()`)
- 独自色のテーマ非追従方針(`CreateActionBlue` / `PhaseTagColors` / `PhaseTrackerColors` / `ProGold`)
- 広告枠の位置(収益に直結)
- 画面下端の `navigationBarsPadding()`
