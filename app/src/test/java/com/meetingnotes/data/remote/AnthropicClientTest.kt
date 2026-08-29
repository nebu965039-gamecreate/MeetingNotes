package com.meetingnotes.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `tool_use response is mapped to MeetingSummary domain model`() {
        val responseBody = """
            {
              "content": [
                {
                  "type": "tool_use",
                  "id": "toolu_1",
                  "name": "extract_meeting_summary",
                  "input": {
                    "decisions": [{"content": "月額プランで契約する"}],
                    "todos": [
                      {"task": "見積書を送付する", "assignee": "山田", "deadline": "2026-09-01"},
                      {"task": "要件を再確認する", "assignee": "未定", "deadline": "未定"}
                    ],
                    "nextMeeting": {"date": null, "originalText": "また来週あたり"},
                    "concerns": [{"content": "導入コストが気になる"}],
                    "summary": "契約条件について合意し、詳細は来週詰める。"
                  }
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString(MessagesResponse.serializer(), responseBody)
        val toolUse = response.content.first { it.type == "tool_use" }
        val dto = json.decodeFromJsonElement(SummaryDto.serializer(), toolUse.input!!)
        val summary = dto.toDomain()

        assertEquals(1, summary.decisions.size)
        assertEquals("月額プランで契約する", summary.decisions[0].content)

        assertEquals(2, summary.todos.size)
        assertEquals("山田", summary.todos[0].assignee)
        assertEquals("未定", summary.todos[1].assignee)
        assertEquals("未定", summary.todos[1].deadline)

        assertNull(summary.nextMeeting.date)
        assertEquals("また来週あたり", summary.nextMeeting.originalText)

        assertEquals(1, summary.concerns.size)
        assertTrue(summary.summary.contains("契約条件"))
    }

    @Test
    fun `missing optional fields default to empty without throwing`() {
        val responseBody = """
            {
              "content": [
                {
                  "type": "tool_use",
                  "name": "extract_meeting_summary",
                  "input": {
                    "decisions": [],
                    "todos": [],
                    "nextMeeting": {"date": null, "originalText": null},
                    "concerns": [],
                    "summary": "特に進展なし。"
                  }
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString(MessagesResponse.serializer(), responseBody)
        val toolUse = response.content.first { it.type == "tool_use" }
        val dto = json.decodeFromJsonElement(SummaryDto.serializer(), toolUse.input!!)
        val summary = dto.toDomain()

        assertTrue(summary.decisions.isEmpty())
        assertTrue(summary.todos.isEmpty())
        assertTrue(summary.concerns.isEmpty())
        assertNull(summary.nextMeeting.date)
        assertNull(summary.nextMeeting.originalText)
    }
}
