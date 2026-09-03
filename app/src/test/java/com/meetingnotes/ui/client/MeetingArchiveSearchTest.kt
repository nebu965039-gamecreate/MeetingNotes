package com.meetingnotes.ui.client

import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingArchiveSearchTest {

    private fun meeting(
        id: Long,
        title: String = "商談",
        recordedAt: Long = 0,
        summary: String = "",
        decisions: List<String> = emptyList(),
        concerns: List<String> = emptyList(),
        transcript: String = ""
    ) = MeetingEntity(
        id = id,
        clientId = 1,
        title = title,
        recordedAt = recordedAt,
        transcript = transcript,
        summary = summary,
        decisions = decisions,
        concerns = concerns,
        nextMeetingDate = null,
        nextMeetingOriginalText = null
    )

    @Test
    fun `sort orders by date and title`() {
        val a = meeting(1, title = "あ商談", recordedAt = 300)
        val b = meeting(2, title = "い商談", recordedAt = 100)
        val c = meeting(3, title = "う商談", recordedAt = 200)
        val list = listOf(a, b, c)

        assertEquals(listOf(1L, 3L, 2L), MeetingArchiveSearch.sort(list, MeetingSortOrder.NEWEST).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), MeetingArchiveSearch.sort(list, MeetingSortOrder.OLDEST).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), MeetingArchiveSearch.sort(list, MeetingSortOrder.TITLE).map { it.id })
    }

    @Test
    fun `title match returns empty snippet`() {
        val m = meeting(1, title = "A社との価格交渉")
        assertEquals("", MeetingArchiveSearch.match(m, emptyList(), "価格"))
    }

    @Test
    fun `summary and transcript matches are labeled`() {
        val m = meeting(
            1,
            title = "定例",
            summary = "月額プランで契約に合意した。",
            transcript = "……前半は雑談。担当者が見積もりの金額について説明し……"
        )
        assertTrue(MeetingArchiveSearch.match(m, emptyList(), "月額")!!.startsWith("サマリー: "))
        assertTrue(MeetingArchiveSearch.match(m, emptyList(), "見積もり")!!.startsWith("文字起こし: "))
    }

    @Test
    fun `decisions and todos are searched`() {
        val m = meeting(1, decisions = listOf("トライアル環境を提供する"))
        val todos = listOf(TodoEntity(id = 1, meetingId = 1, task = "契約書を送付", assignee = "山田", deadline = "未定"))
        assertTrue(MeetingArchiveSearch.match(m, emptyList(), "トライアル")!!.startsWith("決定事項: "))
        assertTrue(MeetingArchiveSearch.match(m, todos, "契約書")!!.startsWith("ToDo: "))
    }

    @Test
    fun `no match returns null`() {
        val m = meeting(1, title = "定例", summary = "特になし")
        assertNull(MeetingArchiveSearch.match(m, emptyList(), "存在しない語"))
    }

    @Test
    fun `search filters and keeps sort order`() {
        val a = meeting(1, title = "価格の相談", recordedAt = 100)
        val b = meeting(2, title = "契約", summary = "価格は据え置き", recordedAt = 300)
        val c = meeting(3, title = "無関係", recordedAt = 200)
        val results = MeetingArchiveSearch.search(listOf(a, b, c), emptyMap(), "価格", MeetingSortOrder.NEWEST)
        assertEquals(listOf(2L, 1L), results.map { it.meeting.id })
    }

    @Test
    fun `snippet adds ellipsis when truncated`() {
        val s = MeetingArchiveSearch.snippet("0123456789".repeat(10), "5", radius = 3)!!
        assertTrue(s.startsWith("…"))
        assertTrue(s.endsWith("…"))
    }

    @Test
    fun `blank query yields no results`() {
        val m = meeting(1, title = "何か")
        assertTrue(MeetingArchiveSearch.search(listOf(m), emptyMap(), "  ", MeetingSortOrder.NEWEST).isEmpty())
    }
}
