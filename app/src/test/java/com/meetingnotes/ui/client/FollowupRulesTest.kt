package com.meetingnotes.ui.client

import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientLatestMeeting
import com.meetingnotes.data.model.DealPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `FollowupRules.compute` は「未完了 ToDo が1件以上あるクライアント」をボードに出す純粋関数。
 * (要約完了時に自動起票されるフォローアップメール ToDo も件数に含まれる)
 */
class FollowupRulesTest {

    private val now = 1_757_000_000_000L
    private val day = 86_400_000L

    private fun client(id: Long, name: String = "C$id") =
        ClientEntity(id = id, name = name, createdAt = 0L)

    private fun latest(clientId: Long, daysAgo: Long, phase: String? = null) = ClientLatestMeeting(
        clientId = clientId,
        meetingId = clientId,
        lastRecordedAt = now - daysAgo * day,
        nextMeetingDate = null,
        dealPhase = phase,
        followedUpAt = null
    )

    @Test
    fun `client with open todos is on the board`() {
        val items = FollowupRules.compute(
            listOf(client(1)),
            listOf(latest(1, daysAgo = 2)),
            openTodoCountByClient = mapOf(1L to 3)
        )
        assertEquals(listOf(1L), items.map { it.client.id })
        assertEquals(3, items[0].openTodoCount)
    }

    @Test
    fun `client with no open todos is not on the board`() {
        val items = FollowupRules.compute(
            listOf(client(1)),
            listOf(latest(1, daysAgo = 2)),
            openTodoCountByClient = emptyMap()
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun `old meetings still count while todos remain open`() {
        val items = FollowupRules.compute(
            listOf(client(1)),
            listOf(latest(1, daysAgo = 400)),
            openTodoCountByClient = mapOf(1L to 1)
        )
        assertEquals(listOf(1L), items.map { it.client.id })
    }

    @Test
    fun `won and lost deals still appear if they have open todos`() {
        val items = FollowupRules.compute(
            listOf(client(1), client(2)),
            listOf(latest(1, 3, phase = "won"), latest(2, 3, phase = "lost")),
            openTodoCountByClient = mapOf(1L to 1, 2L to 2)
        )
        assertEquals(setOf(1L, 2L), items.map { it.client.id }.toSet())
    }

    @Test
    fun `phase comes from the latest meeting for display`() {
        val items = FollowupRules.compute(
            listOf(client(1)),
            listOf(latest(1, 3, phase = "quoted")),
            openTodoCountByClient = mapOf(1L to 1)
        )
        assertEquals(DealPhase.QUOTED, items[0].phase)
    }

    @Test
    fun `client with a todo but no latest meeting row is tolerated`() {
        val items = FollowupRules.compute(
            listOf(client(1)),
            emptyList(),
            openTodoCountByClient = mapOf(1L to 1)
        )
        assertEquals(listOf(1L), items.map { it.client.id })
        assertEquals(null, items[0].phase)
    }

    @Test
    fun `results are sorted newest recording first`() {
        val items = FollowupRules.compute(
            listOf(client(1), client(2), client(3)),
            listOf(latest(1, 1), latest(2, 20), latest(3, 8)),
            openTodoCountByClient = mapOf(1L to 1, 2L to 1, 3L to 1)
        )
        assertEquals(listOf(1L, 3L, 2L), items.map { it.client.id })
    }
}
