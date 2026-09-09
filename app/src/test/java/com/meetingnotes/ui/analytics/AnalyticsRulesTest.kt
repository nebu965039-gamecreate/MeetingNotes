package com.meetingnotes.ui.analytics

import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientProjectEntity
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoEntity
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.data.model.MeetingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class AnalyticsRulesTest {

    private val zone = ZoneId.of("Asia/Tokyo")
    private val now = YearMonth.of(2026, 9)
    private val today = LocalDate.of(2026, 9, 9)

    private fun ms(y: Int, m: Int, d: Int = 15): Long =
        LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun meeting(
        id: Long, month: Int, year: Int = 2026,
        type: MeetingType? = null, durationMin: Int? = null, followedUpDays: Long? = null
    ): MeetingEntity {
        val rec = ms(year, month)
        return MeetingEntity(
            id = id, clientId = 1, title = "M$id", recordedAt = rec,
            endedAt = durationMin?.let { rec + it * 60_000L },
            transcript = "", summary = "", decisions = emptyList(), concerns = emptyList(),
            nextMeetingDate = null, nextMeetingOriginalText = null,
            meetingType = type?.wireValue,
            followedUpAt = followedUpDays?.let { rec + it * 86_400_000L }
        )
    }

    private fun client(id: Long, createdMonth: Int, lead: String? = null, ref: String? = null) =
        ClientEntity(id = id, name = "C$id", createdAt = ms(2026, createdMonth), leadSource = lead, referredBy = ref)

    private fun project(clientId: Long, phase: DealPhase) =
        ClientProjectEntity(id = clientId * 10, clientId = clientId, name = "P", createdAt = 0L, phase = phase.wireValue)

    @Test
    fun `activity counts months, types, duration, MoM`() {
        val meetings = listOf(
            meeting(1, 9, type = MeetingType.IN_PERSON, durationMin = 40),
            meeting(2, 9, type = MeetingType.REMOTE, durationMin = 20),
            meeting(3, 8, type = MeetingType.IN_PERSON, durationMin = 60)
        )
        val a = AnalyticsRules.activity(meetings, now, zone)
        assertEquals(12, a.monthlyMeetings.size)
        assertEquals(now, a.monthlyMeetings.last().month)
        assertEquals(2, a.meetingsThisMonth)
        assertEquals(1, a.meetingsLastMonth)
        assertEquals(1, a.monthOverMonth)
        assertEquals(2, a.inPerson)
        assertEquals(1, a.remote)
        assertEquals(40, a.avgDurationMin)  // (40+20+60)/3
        assertEquals(2, a.totalHours)       // 120 分 = 2h
    }

    @Test
    fun `customers lead source counts and win rates`() {
        val clients = listOf(
            client(1, 9, lead = "紹介"), client(2, 9, lead = "紹介"),
            client(3, 8, lead = "Web検索"), client(4, 8, ref = "田中さん"), client(5, 8, ref = "田中さん")
        )
        val projects = listOf(
            project(1, DealPhase.WON), project(2, DealPhase.LOST), project(3, DealPhase.LOST)
        )
        val c = AnalyticsRules.customers(clients, projects, now, zone)
        assertEquals(listOf("紹介" to 2, "Web検索" to 1), c.leadSourceCounts.map { it.label to it.count })
        val shokai = c.leadSourceWinRates.first { it.label == "紹介" }
        assertEquals(50, shokai.rate)  // 1 won / (1 won + 1 lost)
        assertEquals(listOf("田中さん" to 2), c.topReferrers.map { it.label to it.count })
    }

    @Test
    fun `follow done rate, overdue, email rate, avg days`() {
        val todos = listOf(
            TodoEntity(id = 1, clientId = 1, task = "a", isDone = true),
            TodoEntity(id = 2, clientId = 1, task = "b", isDone = false, dueDate = "2026-09-01"),   // 期限切れ
            TodoEntity(id = 3, clientId = 1, task = "c", isDone = false, dueDate = "2026-12-01"),   // まだ
            TodoEntity(id = 4, clientId = 1, task = "メール", isDone = true, isFollowupEmail = true),
            TodoEntity(id = 5, clientId = 1, task = "メール", isDone = false, isFollowupEmail = true)
        )
        val meetings = listOf(
            meeting(1, 9, followedUpDays = 1), meeting(2, 9, followedUpDays = 3), meeting(3, 9)
        )
        val f = AnalyticsRules.follow(todos, meetings, today)
        assertEquals(40, f.todoDoneRate)       // 2/5
        assertEquals(1, f.overdueOpen)
        assertEquals(50, f.emailFollowRate)    // 1/2
        assertEquals(2.0, f.avgFollowDays!!, 0.001)  // (1+3)/2
    }

    @Test
    fun `empty inputs are safe`() {
        val a = AnalyticsRules.activity(emptyList(), now, zone)
        assertEquals(0, a.meetingsThisMonth)
        assertNull(a.avgDurationMin)
        val f = AnalyticsRules.follow(emptyList(), emptyList(), today)
        assertNull(f.todoDoneRate)
        assertNull(f.avgFollowDays)
    }
}
