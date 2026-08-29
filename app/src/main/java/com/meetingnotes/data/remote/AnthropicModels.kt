package com.meetingnotes.data.remote

import com.meetingnotes.data.model.Concern
import com.meetingnotes.data.model.Decision
import com.meetingnotes.data.model.MeetingSummary
import com.meetingnotes.data.model.NextMeeting
import com.meetingnotes.data.model.TodoItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Double,
    val system: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolDefinition>,
    @SerialName("tool_choice") val toolChoice: ToolChoice
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    @SerialName("input_schema") val inputSchema: JsonObject
)

@Serializable
data class ToolChoice(
    val type: String = "tool",
    val name: String
)

@Serializable
data class MessagesResponse(
    val content: List<ContentBlock> = emptyList()
)

@Serializable
data class ContentBlock(
    val type: String,
    val name: String? = null,
    val input: JsonElement? = null
)

@Serializable
data class SummaryDto(
    val decisions: List<DecisionDto> = emptyList(),
    val todos: List<TodoDto> = emptyList(),
    val nextMeeting: NextMeetingDto? = null,
    val concerns: List<ConcernDto> = emptyList(),
    val summary: String = ""
)

@Serializable
data class DecisionDto(val content: String)

@Serializable
data class TodoDto(
    val task: String,
    val assignee: String = "未定",
    val deadline: String = "未定"
)

@Serializable
data class NextMeetingDto(
    val date: String? = null,
    val originalText: String? = null
)

@Serializable
data class ConcernDto(val content: String)

fun SummaryDto.toDomain(): MeetingSummary = MeetingSummary(
    decisions = decisions.map { Decision(it.content) },
    todos = todos.map { TodoItem(it.task, it.assignee, it.deadline) },
    nextMeeting = NextMeeting(nextMeeting?.date, nextMeeting?.originalText),
    concerns = concerns.map { Concern(it.content) },
    summary = summary
)
