package com.meetingnotes.ui.client

import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientLatestMeeting
import com.meetingnotes.data.model.DealPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowupRulesTest {

    private val now = 1_757_000_000_000L // 固定の「現在」
    private val day = 86_400_000L

    private fun client(id: Long, name: String = "C$id") =
        ClientEntity(id = id, name = name, createdAt = 0L)

    private fun latest(
        clientId: Long,
        daysAgo: Long,
        next: String? = null,
        phase: String? = null,
        followedUp: Boolean = false
    ) = ClientLatestMeeting(
        clientId = clientId,
        meetingId = clientId,
        lastRecordedAt = now - daysAgo * day,
        nextMeetingDate = next,
        dealPhase = phase,
        followedUpAt = if (followedUp) now - daysAgo * day else null
    )

    @Test
    fun `just summarized meeting needs an email followup regardless of days`() {
        val items = FollowupRules.compute(listOf(client(1)), listOf(latest(1, daysAgo = 0)), now)
        assertEquals(1, items.size)
        assertEquals(FollowupReason.NEEDS_EMAIL, items[0].reason)
    }

    @Test
    fun `followed up recent client is not a followup`() {
        val items = FollowupRules.compute(
            listOf(client(1)),
            listOf(latest(1, daysAgo = 3, followedUp = true)),
            now
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun `followed up client with no next meeting becomes stale after threshold`() {
        val items = FollowupRules.compute(
            listOf(client(1)),
            listOf(latest(1, daysAgo = 20, followedUp = true)),
            now
        )
        assertEquals(1, items.size)
        assertEquals(FollowupReason.STALE, items[0].reason)
        assertEquals(20, items[0].daysSince)
    }

    @Test
    fun `client with a future next meeting is not a followup`() {
        val future = java.time.Instant.ofEpochMilli(now + 10 * day)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        val items = FollowupRules.compute(listOf(client(1)), listOf(latest(1, 30, next = future)), now)
        assertTrue(items.isEmpty())
    }

    @Test
    fun `client with a past next meeting date is still a followup`() {
        val items = FollowupRules.compute(listOf(client(1)), listOf(latest(1, 40, next = "2000-01-01")), now)
        assertEquals(1, items.size)
    }

    @Test
    fun `vague next meeting text counts as undecided`() {
        val items = FollowupRules.compute(listOf(client(1)), listOf(latest(1, 40, next = "来週あたり")), now)
        assertEquals(1, items.size)
    }

    @Test
    fun `client with no meetings is skipped`() {
        val items = FollowupRules.compute(listOf(client(1), client(2)), listOf(latest(2, 30)), now)
        assertEquals(listOf(2L), items.map { it.client.id })
    }

    @Test
    fun `needs-email items come before stale items`() {
        val items = FollowupRules.compute(
            listOf(client(1), client(2)),
            listOf(
                latest(1, 40, followedUp = true), // STALE
                latest(2, 2)                      // NEEDS_EMAIL
            ),
            now
        )
        assertEquals(listOf(2L, 1L), items.map { it.client.id })
    }

    @Test
    fun `stale items are sorted by days elapsed descending`() {
        val items = FollowupRules.compute(
            listOf(client(1), client(2), client(3)),
            listOf(
                latest(1, 15, followedUp = true),
                latest(2, 60, followedUp = true),
                latest(3, 30, followedUp = true)
            ),
            now
        )
        assertEquals(listOf(2L, 3L, 1L), items.map { it.client.id })
    }

    @Test
    fun `won and lost deals are not followups even when stale`() {
        val items = FollowupRules.compute(
            listOf(client(1), client(2), client(3)),
            listOf(
                latest(1, 40, phase = "won"),
                latest(2, 40, phase = "lost"),
                latest(3, 40, phase = "quoted")
            ),
            now
        )
        assertEquals(listOf(3L), items.map { it.client.id })
        assertEquals(DealPhase.QUOTED, items[0].phase)
    }

    @Test
    fun `datetime next meeting in the future is respected`() {
        val futureDate = java.time.Instant.ofEpochMilli(now + 5 * day)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        val items = FollowupRules.compute(
            listOf(client(1)),
            listOf(latest(1, 30, next = "${futureDate}T14:00")),
            now
        )
        assertTrue(items.isEmpty())
    }
}
