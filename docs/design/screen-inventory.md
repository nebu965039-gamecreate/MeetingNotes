# 画面インベントリ — 商談メモ

Figma で画面を起こすときの下敷き。ルート・遷移・各画面の構成要素・ダイアログを一覧化。
コードは `app/src/main/java/com/meetingnotes/ui/`。ナビゲーションは `ui/Navigation.kt`(`Routes`)。

- 共有 ViewModel: `MeetingViewModel`(録音〜要約〜保存)は NavHost の外側で1つだけ生成
- テーマ: `MaterialTheme(colorScheme = light/darkColorScheme())` のみ。M3 baseline(紫)。Typography / Shapes は M3 デフォルト
- 広告: `ads/BannerAdView`(`AnchoredAdaptive` 帯 / `MediumRectangle` 300×250)。位置は下表「広告」列
- 画面下端は `Modifier.navigationBarsPadding()`(ジェスチャーバー対策)

---

## ナビゲーション地図

```
HOME (起動時) ─┬─ RECORDING_UNASSIGNED ── RESULT ──▶ CLIENT_DETAIL
               ├─ CLIENT_LIST ── CLIENT_DETAIL ─┬─ RECORDING ── RESULT
               │                                ├─ BRIEFING ── RECORDING
               │                                ├─ MEETING_DETAIL
               │                                ├─ CLIENT_INFO ── CLIENT_EDIT
               │                                └─ (folder/group ダイアログ)
               ├─ SCHEDULE
               ├─ NOTIFICATIONS
               ├─ FOLLOWUP_LIST ── CLIENT_DETAIL
               ├─ SETTINGS
               └─ HELP
```

---

## 画面一覧

| ルート | ファイル | TopAppBar | 広告 | FAB |
|---|---|---|---|---|
| `home` | `home/HomeScreen.kt` | なし(独自ヘッダー) | 下 帯 | — |
| `clientList` | `client/ClientListScreen.kt` | 「クライアント一覧」+ People アイコン + ? ヘルプ | 下 帯 | ◯ クライアント追加(青) |
| `clientDetail/{id}` | `client/ClientDetailScreen.kt` | 2段(クライアント名 / 検索・フォルダ作成・⋮) | 下 帯 | — |
| `clientInfo/{id}` | `client/ClientInfoScreen.kt` | 「クライアント情報」+ 編集アイコン | — | — |
| `clientEdit/{id}` | `client/ClientEditScreen.kt` | 「クライアント情報を編集」+ 保存 | — | — |
| `briefing/{id}` | `briefing/BriefingScreen.kt` | 「前回のおさらい」 | 下 帯 | — |
| `recording/{id}` / `recordingUnassigned` | `recording/RecordingScreen.kt` | 「録音」 | — | — |
| `result` | `result/ResultScreen.kt` | 「要約結果」 | 下 帯 | — |
| `meetingDetail/{id}` | `meeting/MeetingDetailScreen.kt` | 商談タイトル | 300×250(本文中) | — |
| `schedule` | `schedule/ScheduleScreen.kt` | 「予定表」+ CalendarMonth アイコン | 下 帯 | ◯ 予定を追加 |
| `notifications` | `notifications/NotificationScreen.kt` | 「通知」+ Notifications アイコン | 下 帯 | — |
| `followupList` | `client/FollowupListScreen.kt` | 「ToDo」 | 下 帯 | — |
| `settings` | `settings/SettingsScreen.kt` | 「設定」 | 下 帯 | — |
| `help` | `help/HelpScreen.kt` | 「使い方・ヘルプ」 | — | — |

ヘッダー配色の統一: `clientList` / `schedule` / `notifications` / `clientDetail` は
`TopAppBarDefaults.topAppBarColors(containerColor = primaryContainer)`。

---

## 画面別の構成要素

### home — ホーム
1. **ヘッダー**(独自): 日付 + 挨拶(「おはようございます」等、時刻で変化) + 設定(歯車) + ヘルプ(?)
2. **下書き復元カード**(あれば): 「未完了の商談メモがあります」+ 録音時刻 → タップで録音画面へ復帰
3. **アクションタイル**(`surfaceContainerLow` 枠): 録音を始める / クライアント一覧 / 予定表 / 通知(2×2、通知は未読で赤ドット)
4. **やること（期限あり）**(`DueTodoBoard`、`dueTodos` 非空時のみ): 期限切れ+今日+3日以内の未完了 ToDo。行頭チェック、タップで商談詳細
5. **直近の予定**(`UpcomingBoard`): `secondaryContainer` カード、プレビュー3件、ヘッダー右「すべて表示」
6. **ToDo**(`FollowupBoard`): `tertiaryContainer` 内カード、`heightIn(max=152dp)` スクロール。ヘッダー件数=未完了 ToDo 合計。行=クライアント名 + `TodoCountBadge`(未完了数) + 「最終 M/d・フェーズ」。行タップでクライアント詳細
7. **進行中のフェーズ**(`PhaseTrackerSection`): ステップトラッカー。ヒアリング/提案/見積提示/検討中の件数(色は `PhaseTrackerColors` 固定)

リンク文字は `TextDecoration.Underline`(件数表示と区別)。

### clientList — クライアント一覧
- 検索バー、グループの折りたたみ(未分類は常時表示)、クライアント行
- FAB「クライアントを追加」(`CreateActionBlue`)、TopAppBar に フォルダ作成(`CreateNewFolder` 青)/ 検索 / ?
- ダイアログ: 「クライアントを追加」(名前 + グループ選択 `LabeledDropdownField`)、「グループに移動」(選択 + 変更ボタン)

### clientDetail — 商談アーカイブ
- topBar 2段: 1段目=クライアント名(`maxLines=2`)、2段目=`Surface(primaryContainer)` 右詰めに 検索 / `CreateNewFolder`(青) / ⋮
- ⋮: 前回のおさらい / クライアント情報 / クライアントを削除
- 本文: **未完了のToDo → 「ToDoリスト」**(行頭 `Checkbox`、タップで商談詳細) → フォルダ折りたたみ → 商談行(`MeetingRow`: タイトル太字 + `DealPhaseChip` 右詰め、要約プレビュー1文60字、検索時はフラット結果)

### clientInfo — クライアント情報(閲覧専用)
- `ElevatedCard` の `InfoRow`: 名前 / グループ / 現在のステータス(`DealPhaseChip`) / メール / 電話
- 担当者カード一覧(氏名 + 役職メモ + ✉ + ☎、`SelectionContainer`)
- 備考

### clientEdit — クライアント情報 編集
- 名前 / グループ(`LabeledDropdownField`) / メール / 電話 / 備考(複数行)
- 担当者: 各行 `ContactEditRow`(氏名/メモ/メール/電話 + 削除)、「担当者を追加」カード
- TopAppBar「保存」

### briefing — 前回のおさらい
- AI 生成の「これまでの流れ」テキスト、「録音を始める」ボタン。商談が増えたら再生成

### recording — 録音
- 起動時: 下書きあり→復元ダイアログ / なし→`RecordingModePicker`「この商談は？」(リモート会議 / 対面)
- カウントダウン → 録音中(経過時間、音声レベル、live 文字起こし、50分警告、`SlideToStop`)→ Stopping → (リモートは Transcribing「文字起こし中...」) → 編集(文字数カウンタ、上限超で要約無効)
- BackHandler で中止確認
- ダイアログ: リモート会議モードについて(同意) / 上限 / 前回の録音が途中です / 録音を中止しますか？

### result — 要約結果
- タイトル入力、直接録音時は「既存から選ぶ / 新規登録」`FilterChip`
- 要約中は段階メッセージ(「決定事項を抽出中...」等)
- 要約表示: サマリー→決定事項→懸念点・注意点→ToDo→次回打ち合わせ(`MeetingSummarySections`)
- 保存ボタン(bottomBar)

### meetingDetail — 商談詳細
- ヘッダー: `No. yyyyMMddHHmm` / 録音日時 / 形式(対面・リモート会議) / `DealPhaseChip`(タップで変更)
- 本文: 要約セクション(画面表示順と同じ) → 次回打ち合わせ(手動設定 `NextMeetingPickerDialog` + カレンダー追加) → **300×250 バナー** → フォローアップ下書き → エクスポート
- `TodoRow`: タスク(完了は打消し線) + 「担当 / 期限（M/d）」
- ダイアログ: フォローアップの下書き / エクスポート(形式3グループ + 透かし + PDFパスワード、`ProGate`) / 商談タイトル変更 / 商談を削除

### schedule — 予定表
- 月カレンダー(`MonthCalendar`、日曜始まり、予定日/ToDo期限日に丸)、`${year}年${month}月`
- 下: 日付タップで絞り込む一覧。「この日が期限のToDo」
- `ScheduleRow` ⋮: 日程を変更 / 削除(`nextMeetingDate` を null。商談は消さない)
- FAB「予定を追加」(既存クライアント選択 → 次回予定を新規設定)

### notifications — 通知
- リマインド ON/OFF は設定へ移設。ここは 予定リスト + 通知履歴のみ

### followupList — ToDo(全件)
- 2タブ: 「ToDo (n)」(未完了 ToDo のあるクライアント、行タップでクライアント詳細、`TodoCountBadge` + `DealPhaseChip`) / 「完了 (n)」(`followedUpAt` 済み、「ToDoに戻す」)

### settings — 設定
- (1) 打ち合わせのリマインド ON/OFF(+ `POST_NOTIFICATIONS` 権限リクエスト)
- (2) 背景(テーマ): ダイアログで ライト / ダーク / システム

### help — 使い方・ヘルプ
- カード + アイコン + セクション別アクセント(`AccentPurple #6750A4` / `AccentBlue #1565C0` / `AccentGreen #2E7D32` / `AccentSlate #455A64`)
- トピック: 基本的な流れ / AIがまとめる項目 / 無料枠と広告 / 長い商談のコツ / 文字起こしのコツ / 対面・リモート会議 / 整理のしかた / エクスポート / プライバシー
- 末尾に「診断情報」カード(共有 / 消去)

---

## 共通コンポーネント(`ui/common/`)

| 名前 | 用途 |
|---|---|
| `DealPhaseChip` | フェーズタグ(枠+塗り、`RoundedCornerShape(6dp)`、`labelMedium`、色は `PhaseTagColors`) + `DealPhasePickerDialog` |
| `LabeledDropdownField` | ラベル付きセレクトボックス(総称型) |
| `NextMeetingPickerDialog` | 次回打ち合わせの日付/時刻ピッカー |
| `ConfirmDialog` / `TextInputDialog` | 確認 / 単一テキスト入力 |
| `ProGate` / `ProPaywallDialog` | Pro ロック表示(グレーアウト + 金枠 + 王冠バッジ。現状 `gatingEnabled=false` で非表示) |
| `MeetingSummarySections` | 要約の画面表示(エクスポートと同項目順) |
| `DateLabels.relativeDateTimeLabel` | 本日/明日/M-d ラベル(ホーム・予定表共有) |

`FollowupBoard` / `UpcomingBoard` / `DueTodoBoard` はホーム専用カード(`ui/client` `ui/home`)。
