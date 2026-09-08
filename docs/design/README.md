# docs/design — 画面デザイン調整用の資料

個人的に UI デザインを Figma などで見直すための下敷き。

| ファイル | 中身 |
|---|---|
| `screen-inventory.md` | 全画面のルート・遷移・構成要素・ダイアログ一覧 |
| `design-tokens.md` | M3 baseline の使用状況 + アプリ独自の色・タイポ・余白・シェイプ |
| `shot.sh` | `source` して `shot <名前>` で1枚ずつスクショ(→ `screens/`、git 管理外)。**まずこちら** |
| `capture-screens.sh` | 31画面を順番に案内する対話式版(慣れたら) |
| `style-guide.html` | 上記2つをまとめた閲覧用スタイルガイド(Artifact として公開可) |

## スクショの撮り方(かんたん版)

```bash
cd /c/projects/meetingnotes
source docs/design/shot.sh     # shot コマンドが使えるようになる
adb-check                      # 端末が "device" と出るか確認(初回はUSBデバッグ許可)
# ↓ スマホで撮りたい画面を出してから、日本語の名前をつけて撮る
shot ホーム
shot クライアント一覧
shot 商談詳細
```

保存先は `docs/design/screens/<名前>.png`。撮れたか確認: `ls docs/design/screens/`

どの画面を撮ればいいか迷ったら **`bash docs/design/capture-screens.sh`**
（全31画面を日本語の説明つきで1つずつ案内。スマホで説明どおりの画面を出して Enter するだけ）。

## Figma での進め方

1. 上記でスクショを撮る（または `bash docs/design/capture-screens.sh` で順番に案内）
2. [Material 3 Design Kit](https://www.figma.com/community/file/1035203688168086460) を複製
3. `design-tokens.md` の「独自の色」をローカル変数として追加
4. スクショを参照に貼りながら再デザイン(ライト/ダーク両方)
5. できたフレーム(または赤字仕様)を渡す → Compose に実装

## 変えない前提

- M3 テーマ機構(`light/darkColorScheme()`)
- 独自色のテーマ非追従方針(`CreateActionBlue` / `PhaseTagColors` / `PhaseTrackerColors` / `ProGold`)
- 広告枠の位置(収益に直結)
- 画面下端の `navigationBarsPadding()`
