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
    fun `a recent, not-yet-completed meeting is a todo`() {
        val items = FollowupRules.compute(listOf(client(1)), listOf(latest(1, daysAgo = 2)), now)
        assertEquals(listOf(1L), items.map { it.client.id })
    }

    @Test
    fun `completed meeting never appears, even much later`() {
        // 2日前の商談を「完了」にした。20日後(今)も出ない。
        val m = latest(1, daysAgo = 20).copy(followedUpAt = now - 18 * day)
        val items = FollowupRules.compute(listOf(client(1)), listOf(m), now)
        assertTrue(items.isEmpty())
    }

    @Test
    fun `a completed meeting with no next plan does not resurface`() {
        val items = FollowupRules.compute(
            listOf(client(1)),
            listOf(latest(1, daysAgo = 25, next = null, followedUp = true)),
            now
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun `meetings older than the recent window are not todos`() {
        val items = FollowupRules.compute(listOf(client(1)), listOf(latest(1, daysAgo = 40)), now)
        assertTrue(items.isEmpty())
    }

    @Test
    fun `a scheduled next meeting does not remove the todo`() {
        // 打ち合わせが決まっていても、お礼メール等の連絡は別途必要。
        val future = java.time.Instant.ofEpochMilli(now + 10 * day)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        val items = FollowupRules.compute(listOf(client(1)), listOf(latest(1, 2, next = future)), now)
        assertEquals(listOf(1L), items.map { it.client.id })
    }

    @Test
    fun `won and lost deals are not todos`() {
        val items = FollowupRules.compute(
            listOf(client(1), client(2), client(3)),
            listOf(
                latest(1, 3, phase = "won"),
                latest(2, 3, phase = "lost"),
                latest(3, 3, phase = "quoted")
            ),
            now
        )
        assertEquals(listOf(3L), items.map { it.client.id })
        assertEquals(DealPhase.QUOTED, items[0].phase)
    }

    @Test
    fun `client with no meetings is skipped`() {
        val items = FollowupRules.compute(listOf(client(1), client(2)), listOf(latest(2, 5)), now)
        assertEquals(listOf(2L), items.map { it.client.id })
    }

    @Test
    fun `results are sorted newest recording first`() {
        val items = FollowupRules.compute(
            listOf(client(1), client(2), client(3)),
            listOf(latest(1, 1), latest(2, 20), latest(3, 8)),
            now
        )
        assertEquals(listOf(1L, 3L, 2L), items.map { it.client.id })
    }
}
