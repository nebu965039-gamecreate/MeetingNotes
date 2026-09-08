# デザイントークン — 商談メモ

現状の見た目の元。**このアプリは Material 3 の baseline テーマをそのまま使用**しており、
色・タイポ・角丸の「正」は M3 baseline。Figma の
[Material 3 Design Kit(Google 公式 Community ファイル)](https://www.figma.com/community/file/1035203688168086460)
を複製すれば同じ値がすべて入っている。ここではそれに加えて **アプリ独自の値** と
**実際にどのトークンをどこで使っているか** をまとめる。

---

## 1. カラー

### 1.1 M3 baseline(`lightColorScheme()` / `darkColorScheme()`、上書きなし)

`MainActivity.kt` は `MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme())` のみ。
カスタム `ColorScheme` は無い。下表は M3 baseline の代表値(参考。**正確な hex は Figma M3 Kit / Material Theme Builder で確認**。
Compose Material3 のバージョンで微差あり)。

| ロール | Light | Dark | 主な用途(このアプリ) |
|---|---|---|---|
| primary | `#6750A4` | `#D0BCFF` | 強調テキスト、アイコン tint、リンク(16回) |
| onPrimary | `#FFFFFF` | `#381E72` | primary 上のテキスト |
| primaryContainer | `#EADDFF` | `#4F378B` | TopAppBar 背景(一覧系)、バッジ |
| onPrimaryContainer | `#21005D` | `#EADDFF` | 上記の前景(21回) |
| secondaryContainer | `#E8DEF8` | `#4A4458` | 「直近の予定」カード |
| onSecondaryContainer | `#1D192B` | `#E8DEF8` | アーカイブ行タイトル |
| tertiaryContainer | `#FFD8E4` | `#633B48` | 「ToDo」カード内、空状態 |
| onTertiaryContainer | `#31111D` | `#FFD8E4` | 区切り線 |
| error | `#B3261E` | `#F2B8B5` | 期限切れ、削除、警告(13回) |
| surface | `#FEF7FF` 前後 | `#141218` 前後 | 画面地 |
| onSurface | `#1D1B20` 前後 | `#E6E1E5` 前後 | 本文 |
| onSurfaceVariant | `#49454F` | `#CAC4D0` | サブテキスト・キャプション(**最頻・50回**) |
| surfaceContainerLow | `#F7F2FA` 前後 | `#1D1B20` 前後 | ホームの各セクション枠、カード |
| outline / outlineVariant | `#79747E` / `#CAC4D0` | `#938F99` / `#49454F` | 枠線 |

> ライト/ダークで役割が入れ替わるトークン(container 系)に注意。デザイン時は必ず両方確認。

### 1.2 アプリ独自の色(テーマ非追従・意図的に固定)

コードにハードコードされている値。ライト/ダークで**変えない**もの(明記あるものを除く)。

#### `ui/theme/CreateActionColors.kt` — 「作成」系アクション
| 名前 | Hex |
|---|---|
| `CreateActionBlue` | `#1565C0` |
| `OnCreateActionBlue` | `#FFFFFF` |

用途: クライアント追加 FAB、フォルダ/グループ作成アイコン(`CreateNewFolder`)。

#### `ui/theme/ProColors.kt` — Pro バッジ
| 名前 | Hex |
|---|---|
| `ProGold` | `#FFB300` |
| `OnProGold` | `#3E2C00` |

#### `ui/theme/PhaseTagColors.kt` — フェーズタグ(`DealPhaseChip`)
`data class PhaseTagColor(container, content)`。**色相は固定、ライト/ダークで明度のみ入れ替え**(`of(phase, darkTheme)`)。

| フェーズ | 系統 | Light 背景 / 文字 | Dark 背景 / 文字 |
|---|---|---|---|
| 初回接触 FIRST_CONTACT | スレート | `#E2E8F0` / `#334155` | `#33414F` / `#CBD5E1` |
| ヒアリング HEARING | 琥珀 | `#FEF0C7` / `#854D0E` | `#4A3A12` / `#FCE29B` |
| 提案 PROPOSAL | 青 | `#DBEAFE` / `#1E40AF` | `#1E355C` / `#B6D0F5` |
| 見積提示 QUOTED | ティール | `#CCFBF1` / `#115E59` | `#12433E` / `#9DEBDE` |
| 検討中 CONSIDERING | オレンジ | `#FFEDD5` / `#9A3412` | `#4E2A12` / `#F9C99B` |
| 成約 WON | 緑 | `#DCFCE7` / `#166534` | `#17402A` / `#9FE3B8` |
| 保留 ON_HOLD | 温グレー | `#EDE9E3` / `#57534E` | `#3B3733` / `#D8D2CB` |
| 失注 LOST | ローズ | `#FFE4E6` / `#9F1239` | `#4C1F2B` / `#F6AEBD` |
| 未設定 | ニュートラル | `#ECEAEF` / `#5B5568` | `#3A3742` / `#C9C4D0` |

#### `ui/theme/PhaseTrackerColors.kt` — ホーム「進行中のフェーズ」(明暗共通)
`data class PhaseTrackerColor(dot, track, label)`。

| フェーズ | dot | track | label |
|---|---|---|---|
| ヒアリング | `#F5C518` | `#FCF3D0` | `#7A5C00` |
| 提案 | `#D4E157` | `#EFF6D6` | `#56660F` |
| 見積提示 | `#8BC34A` | `#E3F2DA` | `#33691E` |
| 検討中 | `#16A34A` | `#DCF0E3` | `#1B5E20` |

#### `ui/help/HelpScreen.kt` — ヘルプのセクションアクセント(private)
| 名前 | Hex |
|---|---|
| `AccentPurple` | `#6750A4` |
| `AccentBlue` | `#1565C0` |
| `AccentGreen` | `#2E7D32` |
| `AccentSlate` | `#455A64` |

---

## 2. タイポグラフィ

M3 baseline `Typography()`(上書きなし)。フォントは端末デフォルト(日本語は各社の Sans)。
実際に使われているスタイルと出現数:

| スタイル | 使用数 | 主な用途 |
|---|---|---|
| `bodySmall` | 39 | キャプション、サブテキスト、日付ラベル |
| `bodyMedium` | 36 | 本文、リスト行 |
| `titleMedium` | 31 | セクション見出し(カードのタイトル) |
| `titleSmall` | 15 | 小見出し(設定の項目名など) |
| `bodyLarge` | 11 | 強調本文、リスト主行 |
| `labelSmall` | 10 | バッジ、極小ラベル |
| `titleLarge` | 5 | 画面レベルの見出し |
| `labelMedium` | 5 | チップ(`DealPhaseChip`)、ボタン内 |
| `labelLarge` | 3 | ボタン、リンク |
| `headlineSmall` / `headlineMedium` / `displayLarge` | 各 1〜3 | ホームの日付・大きな数値など限定的 |

M3 baseline の値(参考): bodySmall 12/16、bodyMedium 14/20、bodyLarge 16/24、
labelSmall 11、labelMedium 12、labelLarge 14、titleSmall 14、titleMedium 16、titleLarge 22、
headlineSmall 24、headlineMedium 28、displayLarge 57。ウェイトは Regular / Medium 中心。

---

## 3. スペーシング

型はないが慣習値。頻度順:

| 値 | 使いどころ |
|---|---|
| `16.dp` | 画面の左右パディング、カード内パディングの標準(最頻) |
| `14.dp` | ホームのカード内パディング |
| `8.dp` | 要素間の標準ギャップ、縦方向の詰め |
| `24.dp` | ダイアログ内、ゆとりを持たせる区切り |
| `4.dp` / `2.dp` | 密なリスト行の縦詰め |
| `6.dp` / `9.dp` / `10.dp` | 行内の微調整 |

`Arrangement.spacedBy(8.dp)` が縦積みの標準。

---

## 4. シェイプ(角丸)

M3 baseline `Shapes()` + 個別の `RoundedCornerShape`:

| 値 | 使いどころ |
|---|---|
| `16.dp` | カード内の内側コンテナ(ホームの各ボード)(最頻) |
| `12.dp` | 中カード |
| `8.dp` | `TodoCountBadge` など小バッジ |
| `6.dp` | `DealPhaseChip` |
| `2.dp` | 極小マーカー |
| `CircleShape` | アイコン背景、カレンダーの日付丸、音声レベル |

M3 baseline: extraSmall 4 / small 8 / medium 12 / large 16 / extraLarge 28。

---

## 5. コンポーネントの使い方(M3)

- **カード**: `Card` / `ElevatedCard`。`containerColor` に `surfaceContainerLow` / `secondaryContainer` / `tertiaryContainer` を役割で使い分け
- **TopAppBar**: 一覧系は `topAppBarColors(containerColor = primaryContainer)`。詳細系はデフォルト
- **ボタン**: `Button`(主要)/ `OutlinedButton`(副次)/ `TextButton`(行内・ダイアログ)
- **入力**: `OutlinedTextField`。セレクトは `LabeledDropdownField`(内部 `ExposedDropdownMenuBox`)
- **チェック**: `Checkbox`(クライアント画面の ToDoリスト)。完了トグルはチェックボックス表記
- **チップ**: 要約結果の保存先切替は `FilterChip`。フェーズは `DealPhaseChip`(独自)
- **ダイアログ**: `AlertDialog`。破壊的操作は `ConfirmDialog`
- **FAB**: `FloatingActionButton`(`CreateActionBlue`)。クライアント一覧・予定表のみ
- **バナー広告**: `BannerAdView`(`AnchoredAdaptive` 帯 = `bottomBar`、`MediumRectangle` 300×250 = 商談詳細の本文中)

---

## 6. Figma へ持っていくときのメモ

1. Material 3 Design Kit を複製 → baseline(purple)テーマがそのまま入っている
2. 上の「1.2 独自の色」をローカルスタイル/変数として追加
3. フォントは "Roboto"(Kit デフォルト)で作業してよい。実機の日本語は端末フォントになる前提
4. ライト/ダーク両方のフレームを作る(container 系トークンは役割が入れ替わる)
5. 変えてはいけない前提: M3 テーマ機構、独自色の「非追従」方針、広告枠の位置(収益)、`navigationBarsPadding`
