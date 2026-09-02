package com.meetingnotes.export

import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoEntity
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcelExporterTest {

    private val meeting = MeetingEntity(
        id = 1,
        clientId = 1,
        title = "契約条件の打ち合わせ",
        recordedAt = 1_756_368_000_000L,
        transcript = "文字起こし本文",
        summary = "契約条件について合意した。",
        decisions = listOf("月額プランで契約する"),
        concerns = listOf("導入コスト<高>"),
        nextMeetingDate = "2026-09-10",
        nextMeetingOriginalText = null
    )

    private val todos = listOf(
        TodoEntity(id = 1, meetingId = 1, task = "見積書を送付する", assignee = "山田", deadline = "2026-09-01", isDone = true)
    )

    private fun readEntries(bytes: ByteArray): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return entries
    }

    @Test
    fun `xlsx contains the required OOXML parts`() {
        val entries = readEntries(ExcelExporter.buildXlsx(null, meeting, todos))
        assertEquals(
            setOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/worksheets/sheet1.xml"
            ),
            entries.keys
        )
    }

    @Test
    fun `sheet contains a todo table with headers and rows`() {
        val sheet = readEntries(ExcelExporter.buildXlsx("テスト株式会社", meeting, todos))["xl/worksheets/sheet1.xml"]!!
        assertTrue(sheet.contains("タスク"))
        assertTrue(sheet.contains("担当"))
        assertTrue(sheet.contains("見積書を送付する"))
        assertTrue(sheet.contains("山田"))
        assertTrue(sheet.contains("テスト株式会社"))
    }

    @Test
    fun `xml special characters are escaped`() {
        val sheet = readEntries(ExcelExporter.buildXlsx(null, meeting, todos))["xl/worksheets/sheet1.xml"]!!
        assertTrue(sheet.contains("導入コスト&lt;高&gt;"))
        assertTrue(!sheet.contains("導入コスト<高>"))
    }

    @Test
    fun `empty sections fall back to placeholder`() {
        val bare = meeting.copy(decisions = emptyList(), concerns = emptyList())
        val rows = ExcelExporter.buildRows(null, bare, emptyList())
        assertTrue(rows.any { it == listOf("(なし)") })
    }
}
