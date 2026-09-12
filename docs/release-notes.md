# リリースノート — 商談メモ

Play Console >「リリース」>「テスト > クローズドテスト」>「新しいリリースを作成」の
「リリースノート」欄に記入する文章。

## 形式・制限

- **1 言語あたり 500 文字以内**。
- Console の UI では言語タブ(日本語)にプレーンテキストを直接入力するだけでよい。
- API や複数言語で管理する場合は `<ja-JP> ... </ja-JP>` のように言語タグで囲む。
- クローズドテストでは、この文章は**テスターにも表示される**(ストアのオプトインページ / アプリ更新時)。
  そのため初回は「何を試してほしいか」「フィードバックの送り先」を書くとよい。

---

## 初回クローズドテスト (versionName 0.1.0 / versionCode 1)

```
商談メモ 初回テスト版です。ご協力ありがとうございます。

■ 試していただきたいこと
・商談を録音し、文字起こし → AI要約(サマリー・決定事項・懸念点・ToDo・次回打ち合わせ)が生成されるか
・実機での日本語音声認識の精度(静かな場所 / 少し雑音のある場所)
・クライアント / フォルダ / グループでの整理
・PDF・Word・メール文面でのエクスポート

■ フィードバック送付先
contact.manaapps@gmail.com
不具合や使いにくい点は、些細なことでもお知らせください。スクリーンショットや、起きた操作手順を添えていただけると助かります。
```

(約 210 文字)

### 英語も登録する場合 (en-US)

```
Thanks for testing 商談メモ (MeetingNotes) — a meeting recorder & AI summarizer for freelancers.

Please try:
- Record a meeting, then check the transcript and AI summary (decisions, concerns, to-dos, next meeting)
- On-device Japanese speech recognition accuracy
- Organizing clients into folders / groups
- Export to PDF / Word / email text

Send feedback to contact.manaapps@gmail.com — bug reports, rough edges, anything. Steps to reproduce and screenshots help a lot.
```

---

## クローズドテスト 2回目 (versionName 0.1.1 / versionCode 2)

```
テスターの皆さま、ご報告ありがとうございます。以下を修正しました。

【修正】
・録音中に「音声認識エラー(code=5)」が出て文字起こしが止まることがある問題を修正(自動で復帰するようにしました)
・一部の端末で「録音開始」ボタンが画面下部のナビゲーションバーと重なる問題を修正

引き続き、実機での日本語音声認識の精度を中心にフィードバックをお願いします。
不具合は contact.manaapps@gmail.com へ(操作手順・端末名を添えていただけると助かります)。
```

---

## クローズドテスト 3回目 (versionName 0.1.5 / versionCode 6)

```
テスターの皆さま、いつもありがとうございます。修正と内部改善を入れました。

【修正】
・録音中に「音声認識エラー(code=5)」で文字起こしが止まる問題を修正(自動で復帰します)
・一部の端末で「録音開始」ボタンが画面下部のナビゲーションバーと重なる問題を修正

【内部改善】
・要約処理を経由サーバー方式に変更(使い方は変わりません。ごくまれに数秒遅くなる場合があります)
・データベースの更新処理を安定化

引き続き、実機での日本語音声認識の精度を中心にフィードバックをお願いします。
不具合は contact.manaapps@gmail.com へ(操作手順・端末名を添えていただけると助かります)。
```

---

## クローズドテスト 5回目 (versionName 0.1.8 / versionCode 9)

前回配信以降の変更をまとめて配信します。0.1.5〜0.1.7 の内容を含みます。

### Play Console 貼り付け用(約400字)

```
テスターの皆さま、いつもありがとうございます。多くの改善を入れました。

【修正】
・録音中の音声認識エラー(code=5)で文字起こしが止まる問題
・一部端末で下部ボタンがナビゲーションバーと重なる問題

【録音】
・「停止」を誤タップ防止のスライド操作に変更
・録音が途中で終了しても文字起こしを自動保存(ホーム画面の先頭から再開できます)
・経過時間の表示と、長時間になったら区切りをすすめる表示を追加

【要約・エクスポート】
・無料の要約回数を 月3回 → 月5回 に
・要約中の待ち時間の表示を改善、文字数上限の目安を表示(目安 約60〜80分)
・エクスポートを「共有 / 保存」→ 形式選択の順に変更。形式に Word / Markdown / Excel / CSV / カレンダー(.ics) を追加

【整理・ヘルプ】
・アーカイブに検索(タイトル・要約・文字起こしから)と並び替えを追加
・「使い方・ヘルプ」画面を追加、画面デザインを全体的に整理

引き続き、実機での日本語音声認識の精度を中心にフィードバックをお願いします。
不具合は contact.manaapps@gmail.com へ(操作手順・端末名を添えていただけると助かります)。
```

### この版に含まれる主な変更(社内メモ)

- 無料枠 月3→月5回、ヘルプ画面(追加→カード刷新)、文字起こし整形の強化、1回の上限 約20,000字
- 要約フロー: strict tool、max_tokens 2000、インタースティシャル広告を要約待ち時間に、段階メッセージ表示
- バナー広告の配置拡大・サイズ最適化、長時間録音の警告UI
- 録音「停止」のスライド操作化、要約前テキストの下書き自動保存、システムバック時の後始末
- エクスポート再構成: アクション→形式の2段選択、Markdown/Excel/CSV/.ics 追加、Excel/CSV は ToDo のみ、.ics は日時確定時のみ、透かし設定はトグルON時のみ表示
- 「作成」系ボタンを青に、アーカイブのフォルダ作成を上部アイコンに統一、クライアント名を帯で差別化
- アーカイブの検索(LIKE、タイトル/要約/文字起こし/決定事項/懸念点/ToDo)と並び替え(新しい順/古い順/タイトル順)
- サブスク(Pro)ロック表示の仕組み(`gatingEnabled=false` のため本配信では非表示)
- API 要約を Cloudflare Worker 経由に(フェーズ1)、GeminiClient 削除、Room マイグレーション正式化(v5、`exportSchema`)

---

## クローズドテスト 6回目 (versionName 0.1.9 / versionCode 10)

「記録するアプリ」から「一人商談をやりきる支援アプリ」へ、機能を大きく追加した版です。

### Play Console 貼り付け用(約480字)

```
今回は大きめのアップデートです。

【ホーム画面を刷新】
・下部メニューバー(ホーム/クライアント/予定表/ToDo)、録音は右下のボタン
・最上部にフェーズのグラフ + クライアント数・今月の商談・成約率
・直近の予定 / やること(期限あり) / ToDo もホームに集約

【商談のフォロー支援】
・商談フェーズ(初回接触〜成約/失注)をAIが自動判定、手動変更も可
・2回目以降の録音前に「前回のおさらい」を表示
・要約と同時に「お礼・フォローアップメールの下書き」を自動生成
・要約のToDoに期限をつけ、期限が近いものを通知
・月カレンダーの予定表。打ち合わせ・ToDo期限を当日/前日に通知

【リモート会議モード】
・Web会議など離れた相手の声を高精度で文字起こし(無料は月1回/Pro相当で月40回)
・このときのみ音声を文字起こし用に送信(保存なし)。ポリシーを更新しました

【その他】クライアント情報(ステータス/担当者/連絡先/備考)、ダークテーマ、設定画面

不具合は contact.manaapps@gmail.com へ(操作手順・端末名を添えて)。
```

### この版に含まれる主な変更(社内メモ)

- ホーム画面新設(`ui/home/`): フォローボード(F1)/予定(F7)/フェーズ集計(F3)/やること(期限あり)を集約
- 下部ナビ4タブ + 録音FAB(`MainTabsShell`、内側NavHost)、ホーム最上部ダッシュボード(`HomeDashboardCard`、Canvasドーナツ + 数字 + 全期間成約率)、進行中フェーズを横棒ファネルに、`PhaseChartColors`(旧`PhaseTrackerColors`削除)、`TodoDao.observeOpenTodoTotal`/`MeetingDao.observeCountRecordedSince`
- 商談フェーズ(F3、DB v6)、前回のおさらい(F2、`/briefing`、DB v7)、フォローアップ下書き(F5、`/followup`、要約時生成 + 後追い1回)
- 予定カレンダー + リマインド(F7、`notification_log` DB v8、WorkManager 12h)
- F1「ToDo」= 未完了ToDoのあるクライアント。要約完了時に「お礼・フォローアップメール」ToDoを自動起票(DB v15)。ボードの完了とメールToDoのチェックを同期
- リモート会議モード(`MeetingType`、`AudioFileRecorder` → Worker `/transcribe` → Cloudflare Workers AI Whisper、DB v10/v11、`user_credits` にオンライン回数)
- Tier 1 CRM: ToDo期限の日付解決(`TodoDueDate`、Worker `deadlineDate`)、顧客メール/電話(DB v12)、担当者テーブル(DB v13/v14)、顧客横断ToDo
- クライアント情報を閲覧(`ClientInfoScreen`)/編集(`ClientEditScreen`)に分離
- ダークテーマ(`ThemeMode` LIGHT/DARK/SYSTEM)、設定画面(`ui/settings/`)、フェーズタグ配色(`PhaseTagColors`)
- LLM制御タグ除去(`LlmTextSanitizer` + Worker `stripControlTags`)、診断ログ(`DiagnosticsLog`)、CI(GitHub Actions)
- DB マイグレーション v5→v15(各1段、`MigrationTest` / `MigrationIntegrityTest`)

---

## クローズドテスト 7回目 (versionName 0.2.0 / versionCode 11 予定・★未配信)

**★ 2026-09-12 時点でまだ Play Console に提出していない。`app/build.gradle.kts` の `versionCode`/`versionName` も
まだ 10 / 0.1.9 のまま(6回目と同じ)。配信時に versionCode を 11、versionName を `"0.2.0"` に上げること。**
以前ここに書かれていた案件・パイプラインの下書きは、実際にはパイプラインボードを試験導入した直後に
廃止(フェーズ管理を一本化)する方針変更が入ったため、内容を全面的に書き直した。「1人CRM」の
案件管理をひとまず完成させ、フェーズの管理方法を整理し、配色を刷新した版。

### 配信前にやること

- [ ] 実機で一通り確認(`docs/test-plan.md` の「実機必須」項目、特にフェーズ確認プロンプト・予定詳細ダイアログ・バックアップ復元・ネイビー配色のダーク表示)
- [ ] `app/build.gradle.kts` の `versionCode` を `11`、`versionName` を `"0.2.0"` に更新
- [ ] `./gradlew.bat bundleRelease` → 実機インストールで最終確認
- [ ] Play Console「テスト > クローズドテスト」で新しいリリースを作成、上記の文面を貼り付け

### Play Console 貼り付け用(約490字)

```
商談メモ アップデートです。多くの改善を行いました。

【フェーズ管理を刷新】
・案件のフェーズを「現在の状態」の正としました。商談を保存した直後に、案件を作成/フェーズを更新するか確認が出ます
・分かりにくかった「パイプラインボード」は廃止しました

【案件・データ】
・案件ごとに見積額・成約額・失注理由・受注確度・想定クローズ日を記録
・設定から全データをバックアップ/復元できます(パスワード任意)
・クライアントに流入経路・紹介元を記録

【予定表・クライアント画面】
・予定をタップすると詳細(クライアント・参加者・URL等)が見られるように
・カレンダーの見やすさを改善(今日の日付を左上に赤く表示)
・クライアント画面に下部メニューを追加、タブのデザインを他画面と統一
・ホームの「ToDo」表示を整理し、期限が近いものだけ分かりやすく表示するようにしました

【デザイン】
・アプリ全体の配色をネイビー基調に刷新しました

不具合は contact.manaapps@gmail.com へ(操作手順・端末名を添えて)。
```

### この版に含まれる主な変更(社内メモ)

**案件・データ(2026-09-09)**
- 案件の金額・状態(`client_projects` に `phase`/`currency`/`estimatedAmount`/`wonAmount`/`wonAt`、DB v18)+ 失注理由(`lostReason`、DB v20)+ 想定クローズ日・受注確度(`expectedCloseAt`/`probability`、DB v21、`DealPhase.defaultProbability`)
- 手動 ToDo 追加(`todos.meetingId` を nullable 化 + `clientId`、DB v19)
- 売上・実績ビュー→分析タブ「売上」に統合(`ui/sales/`・`ui/analytics/`、`SalesRules`/`AnalyticsRules`)
- よどみ検知(`client_projects.phaseChangedAt`、DB v22、`StaleDealRules`、ホーム「動いていない案件」)
- 定期フォローのスヌーズ→個別 ToDo のスヌーズに刷新(`todos.snoozedUntil`、DB v28、`FollowupListScreen` 3タブ化)
- 予定に会議URL・場所(`schedules.meetingUrl`/`location`、DB v24、カレンダー連携)/ クライアントに流入経路・紹介元(`clients.leadSource`/`referredBy`、DB v25)
- 全データのバックアップ/復元(`data/backup/BackupManager`、JSON + 任意 AES-GCM、復元は全置換え+再起動)、メール文面テンプレート(DB v26)
- Google Play Billing 基盤(`billing/BillingManager` 7.1.1)。`PRO_GATING_ENABLED` 既定 false のためロック表示なし

**フェーズ体系の一本化(2026-09-09〜11)**
- 案件パイプラインボードを試験導入(`ui/pipeline/`)→**廃止**(操作してもホームのドーナツに反映されず「必要性が感じられない」というフィードバックを受け、正式に方針転換)
- **案件フェーズ(`client_projects.phase`)を「クライアントの現在の状態」の正に統一**。商談フェーズ(`meetings.dealPhase`)はその商談時点のスナップショットに限定
- `ui/client/ClientDealPhaseRules`(代表フェーズの算出。進行中の最先端 / 無ければ成約 / 失注)、`MeetingRepository.observeClientDealPhase`
- 要約保存直後の確認プロンプト(`MeetingViewModel.PostSavePrompt`、`ResultScreen.PostSavePromptDialog`): 案件0件→作成を促す、進行中案件とAI推定フェーズが食い違う→更新を促す
- 既存クライアントの自動バックフィル(`MeetingRepository.backfillClientProjects`)、ホームのドーナツ・成約率・成約/失注数を案件フェーズ基準に変更

**予定表・クライアント画面(2026-09-10〜11)**
- 予定表: カレンダー枠を強調・今日を左上の赤三角で表示(丸枠を廃止)、予定/ToDo印を数字から離す、本日/これからの予定を`ScheduleListPanel`(ホーム「直近の予定」と共通)に統一
- 予定の詳細ダイアログ(`ScheduleDetailDialog`、公開化): 全項目を常時表示・未登録は「なし」、クライアント名がリンク、「前回の会議を開く」「カレンダーアプリに追加」。ホームの「直近の予定」タップでも同ダイアログを表示
- クライアント詳細画面: `PrimaryTabRow`→`FolderTabRow`、下部ナビ追加(`AppBottomNav`共通化)、プロジェクト絞り込みをプルダウンに(「すべてのプロジェクト」/「未定のプロジェクト」)
- `TabTopBar`のホームボタンとタイトルの間隔調整

**デザイン(2026-09-12)**
- アプリ全体の配色をネイビー基調のカスタム `ColorScheme` に(`ui/theme/NavyTheme.kt`)。作成/録音アクションの色を青からアンバーに(`CreateActionColors.kt`)
- 4タブ共通ヘッダー(`TabTopBar`)・フォルダ型タブ(`FolderTabRow`)・クライアント詳細ヘッダー・ホームダッシュボードをネイビー地に(`ui/theme/BrandNavy.kt`)

**ホームのToDo表示の整理(2026-09-12)**
- ホームの「ToDo」ボード(未完了ToDoが1件でもあるクライアントをカード表示、`ui/client/FollowupBoard.kt`)を全廃止。「期限切れ・3日以内のToDo」(`DueTodoBoard`)や下部ナビ ToDo タブと条件が異なるまま並んでいて件数の食い違いが分かりにくい、というフィードバックを受けての整理
- ホーム「やること(期限あり)」→「期限切れ・3日以内のToDo」に改称(`DueTodoBoard.kt`、表示条件は変更なし)

- DB マイグレーション v15→v28(各1段、`MigrationTest` / `MigrationIntegrityTest`)
- CI 修正(`gradlew` 実行権限)
- プライバシーポリシー: 2.5 に「データのバックアップ」(利用者操作でのファイル書き出し)を追記

---

## 次回以降のリリース用テンプレート

### 軽微な修正のみ

```
・不具合の修正と動作の安定性向上
```

### 変更内容がある場合(例)

```
【改善】
・録音中の音声レベル表示を見やすく調整
・エクスポートしたPDFのレイアウトを改善

【修正】
・特定の条件で要約が失敗する不具合を修正
・フォルダ名の変更が一覧に反映されないことがある問題を修正

ご報告ありがとうございます。引き続きフィードバックをお待ちしています(contact.manaapps@gmail.com)。
```

### 運用メモ

- コード/リソースを変更したら `app/build.gradle.kts` の `versionCode` を +1、必要なら `versionName` も更新してから `./gradlew.bat bundleRelease`。
- 「以前のリリースからコピー」を使うと前回のノートが入るので、変更点に書き換える。
- ユーザー向けに意味のある変更だけを書く(内部リファクタリングやテスト追加は書かない)。
