package com.meetingnotes.export

import com.meetingnotes.data.local.TodoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    @Test
    fun `header row is always present`() {
        val csv = CsvExporter.buildCsv(emptyList())
        assertEquals("タスク,担当,期限,完了\r\n", csv)
    }

    @Test
    fun `each todo becomes a row with a done marker`() {
        val csv = CsvExporter.buildCsv(
            listOf(
                TodoEntity(id = 1, meetingId = 1, task = "見積書を送付", assignee = "山田", deadline = "2026-09-01", isDone = true),
                TodoEntity(id = 2, meetingId = 1, task = "契約書を確認", assignee = "未定", deadline = "未定", isDone = false)
            )
        )
        assertTrue(csv.contains("見積書を送付,山田,2026-09-01,済\r\n"))
        assertTrue(csv.contains("契約書を確認,未定,未定,\r\n"))
    }

    @Test
    fun `fields containing comma or quote are escaped`() {
        val csv = CsvExporter.buildCsv(
            listOf(
                TodoEntity(id = 1, meetingId = 1, task = "A, B \"C\"", assignee = "山田", deadline = "未定")
            )
        )
        assertTrue(csv.contains("\"A, B \"\"C\"\"\",山田,未定,"))
    }
}
