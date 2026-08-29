package com.meetingnotes.export

import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingExportContentBuilderTest {

    private val meeting = MeetingEntity(
        id = 1,
        clientId = 1,
        title = "契約条件の打ち合わせ",
        recordedAt = 1_756_368_000_000L,
        transcript = "文字起こし本文",
        summary = "契約条件について合意した。",
        decisions = listOf("月額プランで契約する"),
        concerns = listOf("導入コストが気になる"),
        nextMeetingDate = null,
        nextMeetingOriginalText = "また来週あたり"
    )

    private val todos = listOf(
        TodoEntity(id = 1, meetingId = 1, task = "見積書を送付する", assignee = "山田", deadline = "2026-09-01")
    )

    @Test
    fun `build includes client name, decisions, todos, next meeting, and concerns`() {
        val blocks = MeetingExportContentBuilder.build("テスト株式会社", meeting, todos)

        assertTrue(blocks.contains(ExportBlock.Paragraph("クライアント: テスト株式会社")))
        assertTrue(blocks.contains(ExportBlock.BulletItem("月額プランで契約する")))
        assertTrue(blocks.contains(ExportBlock.BulletItem("見積書を送付する(担当: 山田 / 期限: 2026-09-01)")))
        assertTrue(blocks.contains(ExportBlock.Paragraph("また来週あたり")))
        assertTrue(blocks.contains(ExportBlock.BulletItem("導入コストが気になる")))
    }

    @Test
    fun `empty decisions, todos, and concerns fall back to placeholder text`() {
        val emptyMeeting = meeting.copy(decisions = emptyList(), concerns = emptyList())
        val blocks = MeetingExportContentBuilder.build(null, emptyMeeting, emptyList())

        val placeholderCount = blocks.count { it == ExportBlock.Paragraph("(なし)") }
        assertEquals(3, placeholderCount)
    }

    @Test
    fun `null client name omits the client paragraph`() {
        val blocks = MeetingExportContentBuilder.build(null, meeting, todos)
        assertTrue(blocks.none { it is ExportBlock.Paragraph && it.text.startsWith("クライアント:") })
    }

    @Test
    fun `plain text version wraps headings in brackets`() {
        val text = MeetingExportContentBuilder.buildPlainText("テスト株式会社", meeting, todos)
        assertTrue(text.contains("【サマリー】"))
        assertTrue(text.contains("契約条件について合意した。"))
    }
}
