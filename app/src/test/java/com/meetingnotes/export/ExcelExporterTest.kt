package com.meetingnotes.export

import com.meetingnotes.data.local.TodoEntity
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcelExporterTest {

    private val todos = listOf(
        TodoEntity(id = 1, meetingId = 1, task = "見積書を送付", assignee = "山田", deadline = "2026-09-01", isDone = true),
        TodoEntity(id = 2, meetingId = 1, task = "契約書 <案> を確認", assignee = "未定", deadline = "未定", isDone = false)
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
        val entries = readEntries(ExcelExporter.buildXlsx(todos))
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
    fun `sheet is a todo table with header row and a done marker`() {
        val rows = ExcelExporter.buildRows(todos)
        assertEquals(listOf("タスク", "担当", "期限", "完了"), rows.first())
        assertEquals(listOf("見積書を送付", "山田", "2026-09-01", "済"), rows[1])
        assertEquals(listOf("契約書 <案> を確認", "未定", "未定", ""), rows[2])
    }

    @Test
    fun `empty todos yields header only`() {
        assertEquals(listOf(listOf("タスク", "担当", "期限", "完了")), ExcelExporter.buildRows(emptyList()))
    }

    @Test
    fun `xml special characters are escaped`() {
        val sheet = readEntries(ExcelExporter.buildXlsx(todos))["xl/worksheets/sheet1.xml"]!!
        assertTrue(sheet.contains("契約書 &lt;案&gt; を確認"))
        assertTrue(!sheet.contains("契約書 <案>"))
    }
}
