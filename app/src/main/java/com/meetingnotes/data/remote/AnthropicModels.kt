package com.meetingnotes.data.remote

import com.meetingnotes.data.model.Concern
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.data.model.Decision
import com.meetingnotes.data.model.MeetingSummary
import com.meetingnotes.data.model.NextMeeting
import com.meetingnotes.data.model.TodoItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** アプリ → 要約プロキシ Worker へのリクエスト本文。 */
@Serializable
data class SummarizeRequest(
    val transcript: String
)

/** F2 ブリーフィング(過去要約 → ここまでの流れ)。 */
@Serializable
data class BriefingRequest(val summaries: List<String>)

/** F5 フォローアップ下書き。tone = "polite" | "casual"。 */
@Serializable
data class FollowupRequest(val summary: String, val tone: String)

/** briefing / followup の共通レスポンス。 */
@Serializable
data class TextResponse(val text: String = "")

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
    val summary: String = "",
    val dealPhase: String? = null
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
    summary = summary,
    dealPhase = DealPhase.fromWire(dealPhase)
)
