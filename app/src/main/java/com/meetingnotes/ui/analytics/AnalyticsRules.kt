package com.meetingnotes.ui.analytics

import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientProjectEntity
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoEntity
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.data.model.MeetingType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class MonthCount(val month: YearMonth, val count: Int)
data class LabelCount(val label: String, val count: Int)
data class LabelRate(val label: String, val won: Int, val lost: Int) {
    val total: Int get() = won + lost
    val rate: Int? get() = total.takeIf { it > 0 }?.let { won * 100 / it }
}

/** 「活動」タブの集計。 */
data class ActivityStats(
    val monthlyMeetings: List<MonthCount>,   // 直近12ヶ月
    val meetingsThisMonth: Int,
    val meetingsLastMonth: Int,
    val inPerson: Int,
    val remote: Int,
    val avgDurationMin: Int?,                 // endedAt-recordedAt の平均(分)。不明は null
    val totalHours: Int
) {
    val monthOverMonth: Int get() = meetingsThisMonth - meetingsLastMonth
}

/** 「顧客」タブの集計。 */
data class CustomerStats(
    val monthlyNewClients: List<MonthCount>,
    val leadSourceCounts: List<LabelCount>,       // 流入経路 → クライアント数(降順)
    val leadSourceWinRates: List<LabelRate>,      // 流入経路 → 成約/失注(rate 降順)
    val topReferrers: List<LabelCount>            // 紹介元 → 件数(降順・上位)
)

/** 「フォロー」タブの集計。 */
data class FollowStats(
    val todoTotal: Int,
    val todoDone: Int,
    val overdueOpen: Int,                         // 期限切れの未完了 ToDo
    val emailFollowTotal: Int,
    val emailFollowDone: Int,
    val avgFollowDays: Double?                    // 録音 → followedUpAt の平均日数
) {
    val todoDoneRate: Int? get() = todoTotal.takeIf { it > 0 }?.let { todoDone * 100 / it }
    val emailFollowRate: Int? get() = emailFollowTotal.takeIf { it > 0 }?.let { emailFollowDone * 100 / it }
}

object AnalyticsRules {

    private const val DAY_MS = 86_400_000L

    private fun monthOf(millis: Long, zone: ZoneId): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))

    private fun monthSeries(
        now: YearMonth,
        pick: (YearMonth) -> Int
    ): List<MonthCount> = (0 until 12).map { i ->
        val m = now.minusMonths((11 - i).toLong())
        MonthCount(m, pick(m))
    }

    fun activity(
        meetings: List<MeetingEntity>,
        now: YearMonth = YearMonth.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ActivityStats {
        val byMonth = meetings.groupingBy { monthOf(it.recordedAt, zone) }.eachCount()
        val lastMonth = now.minusMonths(1)
        val durations = meetings.mapNotNull { m ->
            m.endedAt?.let { end -> (end - m.recordedAt).takeIf { it in 1_000..24 * 60 * 60_000L } }
        }
        val remoteWire = MeetingType.REMOTE.wireValue
        return ActivityStats(
            monthlyMeetings = monthSeries(now) { byMonth[it] ?: 0 },
            meetingsThisMonth = byMonth[now] ?: 0,
            meetingsLastMonth = byMonth[lastMonth] ?: 0,
            remote = meetings.count { it.meetingType == remoteWire },
            inPerson = meetings.count { it.meetingType != remoteWire },
            avgDurationMin = durations.takeIf { it.isNotEmpty() }
                ?.let { (it.sum() / it.size / 60_000L).toInt() },
            totalHours = (durations.sum() / 3_600_000L).toInt()
        )
    }

    fun customers(
        clients: List<ClientEntity>,
        projects: List<ClientProjectEntity>,
        now: YearMonth = YearMonth.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): CustomerStats {
        val newByMonth = clients.groupingBy { monthOf(it.createdAt, zone) }.eachCount()

        val sourceByClient = clients.associate { it.id to it.leadSource?.trim()?.takeIf(String::isNotEmpty) }
        val leadCounts = clients
            .mapNotNull { it.leadSource?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }.eachCount()
            .map { (label, count) -> LabelCount(label, count) }
            .sortedByDescending { it.count }

        val winBySource = HashMap<String, IntArray>() // [won, lost]
        for (p in projects) {
            val src = sourceByClient[p.clientId] ?: continue
            val slot = winBySource.getOrPut(src) { IntArray(2) }
            when (DealPhase.fromWire(p.phase)) {
                DealPhase.WON -> slot[0]++
                DealPhase.LOST -> slot[1]++
                else -> {}
            }
        }
        val leadRates = winBySource
            .map { (label, s) -> LabelRate(label, s[0], s[1]) }
            .filter { it.total > 0 }
            .sortedByDescending { it.rate ?: -1 }

        val referrers = clients
            .mapNotNull { it.referredBy?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }.eachCount()
            .map { (label, count) -> LabelCount(label, count) }
            .sortedByDescending { it.count }
            .take(5)

        return CustomerStats(
            monthlyNewClients = monthSeries(now) { newByMonth[it] ?: 0 },
            leadSourceCounts = leadCounts,
            leadSourceWinRates = leadRates,
            topReferrers = referrers
        )
    }

    fun follow(
        todos: List<TodoEntity>,
        meetings: List<MeetingEntity>,
        today: LocalDate = LocalDate.now()
    ): FollowStats {
        val todayIso = today.toString()
        val emailTodos = todos.filter { it.isFollowupEmail }
        val followDurations = meetings.mapNotNull { m ->
            m.followedUpAt?.let { ((it - m.recordedAt) / DAY_MS).coerceAtLeast(0) }
        }
        return FollowStats(
            todoTotal = todos.size,
            todoDone = todos.count { it.isDone },
            overdueOpen = todos.count { t -> !t.isDone && (t.dueDate?.let { it < todayIso } == true) },
            emailFollowTotal = emailTodos.size,
            emailFollowDone = emailTodos.count { it.isDone },
            avgFollowDays = followDurations.takeIf { it.isNotEmpty() }
                ?.let { it.sum().toDouble() / it.size }
        )
    }
}
