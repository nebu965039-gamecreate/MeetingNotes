package com.meetingnotes.data.debug

import com.meetingnotes.data.local.ClientContactEntity
import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientGroupEntity
import com.meetingnotes.data.local.ClientProjectEntity
import com.meetingnotes.data.local.EmailTemplateEntity
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.MeetingNotesDatabase
import com.meetingnotes.data.local.ScheduleEntity
import com.meetingnotes.data.local.TodoEntity
import com.meetingnotes.data.model.DealPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * レビュー・動作確認用のサンプルデータ生成(デバッグビルド専用)。
 * 既存データを **全消去してから** 過去1年ぶんのクライアント/商談/案件/ToDo/予定を投入する。
 * DAO を直接使い `recordedAt` などの時刻を意図的に散らして、分析グラフ・パイプライン・
 * 停滞案件・カレンダーがひととおり埋まるようにしている。本番フローには一切関与しない。
 */
object SampleDataSeeder {

    data class Result(
        val clients: Int,
        val meetings: Int,
        val projects: Int,
        val todos: Int,
        val schedules: Int
    ) {
        val summary: String
            get() = "サンプルを投入しました: クライアント$clients / 商談$meetings / 案件$projects / ToDo$todos / 予定$schedules"
    }

    private const val DAY = 86_400_000L
    private val GROUPS = listOf("IT・SaaS", "製造・メーカー", "士業・コンサル")

    private data class ClientDef(
        val name: String,
        val group: Int?,
        val ageDays: Int,
        val lead: String?,
        val ref: String?
    )

    private val CLIENTS = listOf(
        ClientDef("アクメ商事", 0, 352, "Web検索", null),
        ClientDef("丹羽製作所", 1, 331, "紹介", "山田会計事務所"),
        ClientDef("さくらリテール", 0, 318, "問い合わせフォーム", null),
        ClientDef("大和エンジニアリング", 1, 300, "イベント・展示会", null),
        ClientDef("グリーンフーズ", null, 286, "SNS", null),
        ClientDef("ほし行政書士事務所", 2, 262, "紹介", "田中太郎"),
        ClientDef("ミナト物流", 1, 241, "Web検索", null),
        ClientDef("クレストデザイン", 0, 213, "既存顧客からの追加", "アクメ商事"),
        ClientDef("東和メディカル", null, 195, "広告", null),
        ClientDef("ブルースカイ観光", 0, 168, "SNS", null),
        ClientDef("北条コンサルティング", 2, 142, "紹介", "佐藤花子"),
        ClientDef("ヤマト精密", 1, 118, "イベント・展示会", null),
        ClientDef("フェリシア化粧品", 0, 89, "Web検索", null),
        ClientDef("三葉不動産", null, 61, "問い合わせフォーム", null),
        ClientDef("オリオンソフト", 0, 34, "紹介", "北条コンサルティング"),
        ClientDef("結び法律事務所", 2, 12, "Web検索", null),
    )

    private val SUMMARIES = listOf(
        "現行業務の課題を一通りヒアリング。属人化した進捗管理と二重入力の解消が最優先とのこと。予算感は年間300〜500万円。",
        "提案内容の説明を実施。基幹システムとの連携範囲について先方情報システム部と追加すり合わせが必要。",
        "見積を提示。金額は概ね想定内との反応。稟議は次月の役員会にかける想定で、意思決定者は管理本部長。",
        "デモを実施。現場担当者の反応は良好。一方で運用開始時の教育コストを懸念しており、導入支援メニューの詳細を求められた。",
        "契約条件の最終確認。検収条件と保守範囲を調整し、大筋合意。発注書は月内に発行予定。",
        "キックオフ。体制・マイルストーン・コミュニケーションルールを確認。第一フェーズは要件定義8週間。",
        "定例。先方社内の優先順位変更により、スコープを一部後ろ倒し。追加要望が2件あり見積へ反映予定。",
        "競合製品と比較検討中とのこと。差別化ポイント(サポート体制・カスタマイズ性)を再説明した。",
        "紹介いただいた案件の初回訪問。課題は明確だが決裁は親会社マター。年度予算のタイミングが鍵。",
        "トラブル対応の振り返り。原因は連携先APIの仕様変更。恒久対策と監視強化を提案し了承を得た。",
    )
    private val DECISIONS = listOf(
        "次回までに先方が現行フローの資料を共有する",
        "見積は通貨別・オプション別の内訳を明記して再提出する",
        "PoC は2週間・対象部署は営業部のみで実施",
        "契約書ドラフトを法務レビューに回す",
        "導入支援は標準プラン(オンライン4回)を前提とする",
    )
    private val CONCERNS = listOf(
        "決裁者との接点がまだ弱い",
        "先方の情報システム部門の工数が確保できるか不透明",
        "予算年度の締めまで時間がない",
        "競合が価格で攻めてくる可能性",
        "運用定着まで伴走できる体制が必要",
    )
    private val TASKS = listOf(
        "議事録と次アクションをメールで送付",
        "見積書(改訂版)を作成して送付",
        "先方情シスと連携仕様の打ち合わせを設定",
        "導入事例の資料を共有",
        "稟議用のROI試算メモを作成",
        "保守契約のドラフトを準備",
        "PoC の環境準備状況を確認",
    )
    private val LOST_REASONS = listOf(
        "価格・予算が合わない", "競合他社に決定", "社内で見送り・保留", "時期・タイミングが合わない"
    )
    private val ACTIVE_PHASES = listOf(
        DealPhase.FIRST_CONTACT, DealPhase.HEARING, DealPhase.PROPOSAL,
        DealPhase.QUOTED, DealPhase.CONSIDERING, DealPhase.ON_HOLD
    )

    suspend fun seed(db: MeetingNotesDatabase): Result = withContext(Dispatchers.IO) {
        db.clearAllTables()

        val rnd = Random(42)
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        fun daysAgo(n: Int) = now - n * DAY
        fun iso(offsetDays: Int) = today.plusDays(offsetDays.toLong()).toString()
        fun isoFromMillis(millis: Long) =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

        val groupDao = db.clientGroupDao()
        val clientDao = db.clientDao()
        val contactDao = db.clientContactDao()
        val projectDao = db.clientProjectDao()
        val meetingDao = db.meetingDao()
        val todoDao = db.todoDao()
        val scheduleDao = db.scheduleDao()
        val templateDao = db.emailTemplateDao()

        val groupIds = GROUPS.map { groupDao.insert(ClientGroupEntity(name = it, createdAt = daysAgo(360))) }

        // --- クライアント + 担当者 ---
        val clientIds = CLIENTS.mapIndexed { i, d ->
            val id = clientDao.insert(
                ClientEntity(
                    name = d.name,
                    groupId = d.group?.let { groupIds[it] },
                    createdAt = daysAgo(d.ageDays),
                    email = "contact${i + 1}@example.co.jp",
                    phone = "03-%04d-%04d".format(1000 + i * 3, 5000 + i * 7),
                    leadSource = d.lead,
                    referredBy = d.ref,
                    memo = if (i % 4 == 0) "紹介元との関係良好。レスポンス早め。" else null
                )
            )
            if (i % 3 == 0) {
                contactDao.insert(
                    ClientContactEntity(
                        clientId = id,
                        name = listOf("鈴木", "高橋", "伊藤", "渡辺", "小林")[i % 5] + " 部長",
                        note = "管理本部 / 決裁者",
                        email = "buyer${i + 1}@example.co.jp",
                        phone = "090-%04d-%04d".format(2000 + i, 6000 + i),
                        createdAt = daysAgo((d.ageDays - 2).coerceAtLeast(1))
                    )
                )
            }
            id
        }

        // --- 案件(プロジェクト) ---
        var projectCount = 0
        val projectByClient = HashMap<Long, Long>()

        suspend fun addProject(
            clientIdx: Int, name: String, phase: DealPhase, createdDaysAgo: Int,
            currency: String = "JPY", estimated: Long? = null, won: Long? = null,
            wonDaysAgo: Int? = null, lostReason: String? = null,
            phaseChangedDaysAgo: Int = createdDaysAgo, probability: Int? = null,
            expectedCloseInDays: Int? = null
        ) {
            val clientId = clientIds[clientIdx]
            val id = projectDao.insert(
                ClientProjectEntity(
                    clientId = clientId, name = name, createdAt = daysAgo(createdDaysAgo),
                    phase = phase.wireValue, currency = currency,
                    estimatedAmount = estimated, wonAmount = won,
                    wonAt = wonDaysAgo?.let { daysAgo(it) },
                    lostReason = lostReason,
                    expectedCloseAt = expectedCloseInDays?.let { now + it * DAY },
                    probability = probability,
                    phaseChangedAt = daysAgo(phaseChangedDaysAgo)
                )
            )
            projectCount++
            projectByClient.putIfAbsent(clientId, id)
        }

        // 成約(月をまたいで散らす)
        addProject(0, "受注管理システム 構築", DealPhase.WON, 340, estimated = 4_200_000, won = 3_900_000, wonDaysAgo = 300)
        addProject(1, "生産ラインIoT 第1期", DealPhase.WON, 300, estimated = 6_500_000, won = 6_500_000, wonDaysAgo = 250)
        addProject(3, "設計データ管理基盤", DealPhase.WON, 250, estimated = 2_800_000, won = 2_500_000, wonDaysAgo = 190)
        addProject(7, "コーポサイト刷新", DealPhase.WON, 160, currency = "USD", estimated = 22_000, won = 20_000, wonDaysAgo = 120)
        addProject(10, "業務フロー診断コンサル", DealPhase.WON, 110, estimated = 1_500_000, won = 1_500_000, wonDaysAgo = 70)
        addProject(12, "ECサイト構築", DealPhase.WON, 70, estimated = 3_300_000, won = 3_100_000, wonDaysAgo = 28)

        // 失注
        addProject(2, "POSデータ連携", DealPhase.LOST, 260, estimated = 1_800_000, lostReason = LOST_REASONS[0])
        addProject(4, "在庫最適化ツール", DealPhase.LOST, 210, estimated = 2_200_000, lostReason = LOST_REASONS[1])
        addProject(6, "配車システム更改", DealPhase.LOST, 150, estimated = 4_000_000, lostReason = LOST_REASONS[2])
        addProject(9, "予約管理アプリ", DealPhase.LOST, 90, currency = "USD", estimated = 15_000, lostReason = LOST_REASONS[3])

        // 進行中(各フェーズ・一部は停滞 = phaseChangedDaysAgo > 21)
        addProject(5, "電子契約の導入支援", DealPhase.PROPOSAL, 40, estimated = 900_000, probability = 45, expectedCloseInDays = 25, phaseChangedDaysAgo = 8)
        addProject(8, "医療系CRM 評価", DealPhase.HEARING, 55, estimated = 3_500_000, probability = 25, expectedCloseInDays = 60, phaseChangedDaysAgo = 38)
        addProject(11, "精密加工 見積自動化", DealPhase.QUOTED, 48, estimated = 2_600_000, probability = 60, expectedCloseInDays = 20, phaseChangedDaysAgo = 6)
        addProject(13, "賃貸管理システム", DealPhase.CONSIDERING, 30, estimated = 5_200_000, probability = 75, expectedCloseInDays = 18, phaseChangedDaysAgo = 12)
        addProject(14, "社内ポータル刷新", DealPhase.FIRST_CONTACT, 20, estimated = 1_200_000, probability = 10, expectedCloseInDays = 75, phaseChangedDaysAgo = 20)
        addProject(15, "契約書レビュー支援", DealPhase.HEARING, 10, currency = "USD", estimated = 12_000, probability = 20, expectedCloseInDays = 45, phaseChangedDaysAgo = 10)
        addProject(3, "生産ラインIoT 第2期", DealPhase.ON_HOLD, 60, estimated = 4_800_000, probability = 15, expectedCloseInDays = 90, phaseChangedDaysAgo = 52)
        addProject(0, "受注管理システム 保守拡張", DealPhase.PROPOSAL, 25, estimated = 800_000, probability = 45, expectedCloseInDays = 30, phaseChangedDaysAgo = 9)

        // --- 商談(過去12ヶ月に散らす) ---
        var meetingCount = 0
        var todoCount = 0
        val meetingClientCycle = listOf(0, 1, 3, 5, 8, 11, 13, 14, 15, 0, 5, 11)

        for (m in 0 until 52) {
            val ci = (meetingClientCycle[m % meetingClientCycle.size] + m / meetingClientCycle.size) % clientIds.size
            val clientId = clientIds[ci]
            val recordedDaysAgo = (350 - m * 350 / 52 - rnd.nextInt(0, 5)).coerceAtLeast(2)
            val recordedAt = daysAgo(recordedDaysAgo)
            val durationMs = (20 + rnd.nextInt(0, 55)) * 60_000L
            val remote = m % 3 == 0
            val phase = ACTIVE_PHASES[m % ACTIVE_PHASES.size]
            val recent = recordedDaysAgo < 45
            val projectId = projectByClient[clientId]?.takeIf { m % 5 != 0 }
            val nextDate = if (recent && m % 2 == 0) iso(3 + rnd.nextInt(0, 18)) else null

            val meetingId = meetingDao.insert(
                MeetingEntity(
                    clientId = clientId,
                    title = "${CLIENTS[ci].name} 打ち合わせ (${m + 1})",
                    recordedAt = recordedAt,
                    endedAt = recordedAt + durationMs,
                    transcript = "（サンプル用の文字起こしプレースホルダ。実際の商談音声は保存されません。）",
                    summary = SUMMARIES[m % SUMMARIES.size],
                    decisions = listOf(DECISIONS[m % DECISIONS.size], DECISIONS[(m + 2) % DECISIONS.size]),
                    concerns = if (m % 3 == 0) listOf(CONCERNS[m % CONCERNS.size]) else emptyList(),
                    nextMeetingDate = nextDate,
                    nextMeetingOriginalText = nextDate?.let { "次回は ${it} に" },
                    dealPhase = phase.wireValue,
                    followedUpAt = if (recordedDaysAgo > 30) recordedAt + (1 + rnd.nextInt(0, 3)) * DAY else null,
                    followupDraft = "お世話になっております。本日はお時間をいただきありがとうございました。ご相談いただいた点について、次のとおり進めさせていただきます。…",
                    meetingType = if (remote) "remote" else "in_person",
                    projectId = projectId
                )
            )
            meetingCount++

            val followupDone = recordedDaysAgo > 20
            todoDao.insert(
                TodoEntity(
                    meetingId = meetingId, clientId = clientId,
                    task = "お礼・フォローアップのメールを送る", assignee = "自分",
                    deadline = "翌日", dueDate = isoFromMillis(recordedAt + DAY),
                    isDone = followupDone, isFollowupEmail = true
                )
            )
            todoCount++

            val taskN = if (recent) 2 + rnd.nextInt(0, 2) else 1
            repeat(taskN) { k ->
                val due = if (recent) iso(-4 + rnd.nextInt(0, 24)) else isoFromMillis(recordedAt + (2 + k) * DAY)
                val snoozed = recent && m % 7 == 0 && k == 0
                todoDao.insert(
                    TodoEntity(
                        meetingId = meetingId, clientId = clientId,
                        task = TASKS[(m + k) % TASKS.size], assignee = "自分",
                        deadline = "", dueDate = due,
                        isDone = !recent || rnd.nextInt(0, 10) < 3,
                        isFollowupEmail = false,
                        snoozedUntil = if (snoozed) now + (3 + rnd.nextInt(0, 12)) * DAY else null
                    )
                )
                todoCount++
            }
        }

        // 手動 ToDo(商談に紐付かない)
        listOf(0, 5, 11, 13).forEachIndexed { i, ci ->
            todoDao.insert(
                TodoEntity(
                    meetingId = null, clientId = clientIds[ci],
                    task = listOf("契約書の押印を依頼", "請求書を発行", "先方の年度予算を確認", "紹介のお礼を連絡")[i],
                    assignee = "自分", deadline = "", dueDate = iso(-2 + i * 3),
                    isDone = false, isFollowupEmail = false,
                    snoozedUntil = if (i == 3) now + 9 * DAY else null
                )
            )
            todoCount++
        }

        // --- 予定 ---
        var scheduleCount = 0
        suspend fun addSchedule(
            ci: Int, atDaysFromNow: Int, hasTime: Boolean, title: String,
            phase: DealPhase?, url: String? = null, loc: String? = null, participants: String = ""
        ) {
            val base = today.plusDays(atDaysFromNow.toLong()).atStartOfDay(zone)
            val start = if (hasTime) base.plusHours(10).plusMinutes(30) else base
            scheduleDao.insert(
                ScheduleEntity(
                    clientId = clientIds[ci], sourceMeetingId = null,
                    startAtMillis = start.toInstant().toEpochMilli(), hasTime = hasTime,
                    title = title, note = "", participants = participants,
                    phase = phase?.wireValue, meetingUrl = url, location = loc,
                    createdAt = now - 5 * DAY
                )
            )
            scheduleCount++
        }
        addSchedule(13, 0, true, "契約条件のすり合わせ", DealPhase.CONSIDERING, url = "https://example.zoom.us/j/123456789", participants = "先方: 鈴木部長")
        addSchedule(11, 2, true, "見積レビュー", DealPhase.QUOTED, loc = "先方本社 3F 会議室B")
        addSchedule(5, 5, true, "電子契約デモ", DealPhase.PROPOSAL, url = "https://meet.example.com/abc-defg-hij")
        addSchedule(0, 9, true, "保守拡張の提案", DealPhase.PROPOSAL, loc = "当社オフィス")
        addSchedule(8, 16, false, "評価結果の共有", DealPhase.HEARING)
        addSchedule(14, 23, true, "キックオフ日程の相談", DealPhase.FIRST_CONTACT, url = "https://example.zoom.us/j/987654321")
        addSchedule(1, -6, true, "定例(実施済み)", DealPhase.PROPOSAL, loc = "先方工場")
        addSchedule(3, -2, true, "第2期の再開判断", DealPhase.ON_HOLD, url = "https://meet.example.com/xyz")

        // --- メール文面テンプレート ---
        listOf(
            "お礼メール" to "{クライアント名} ご担当者様\n\n本日はお打ち合わせのお時間をいただき、ありがとうございました。\n内容を整理し、次のアクションを進めてまいります。\n引き続きよろしくお願いいたします。",
            "日程調整" to "{クライアント名} ご担当者様\n\nお世話になっております。次回のお打ち合わせについて、下記のご都合はいかがでしょうか。\n・{次回打ち合わせ}\nご確認のほどよろしくお願いいたします。",
            "見積送付" to "{クライアント名} ご担当者様\n\nお世話になっております。ご依頼いただいたお見積書を添付いたします({日付}時点)。\nご不明点がございましたらお知らせください。"
        ).forEach { (n, b) ->
            templateDao.insert(EmailTemplateEntity(name = n, body = b, createdAt = now - 30 * DAY))
        }

        Result(
            clients = clientIds.size,
            meetings = meetingCount,
            projects = projectCount,
            todos = todoCount,
            schedules = scheduleCount
        )
    }
}
