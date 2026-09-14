package com.meetingnotes.data.remote

import com.meetingnotes.data.model.Concern
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.data.model.Decision
import com.meetingnotes.data.model.MeetingSummary
import com.meetingnotes.data.model.NextMeeting
import com.meetingnotes.data.model.TodoItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** アプリ → 要約プロキシ Worker へのリクエスト本文。 */
@Serializable
data class SummarizeRequest(
    val transcript: String
)

/** F5 フォローアップ下書き(要約時に付かなかった商談向けの後追い生成、1回のみ)。 */
@Serializable
data class FollowupRequest(val summary: String)

/** followup のレスポンス。 */
@Serializable
data class TextResponse(val text: String = "")

@Serializable
data class MessagesResponse(
    val content: List<ContentBlock> = emptyList(),
    /** 2026-09-21〜: 1日あたりのトークン消費上限チェックに使う。Workerがレスポンスをそのまま
     *  中継するため、既存のパース処理に影響なく追加できる(未知フィールドは無視される設定)。 */
    val usage: Usage? = null
)

@Serializable
data class Usage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
    /** プロンプトキャッシュのヒット分。コスト計算では input_tokens とは別料金だが、
     *  トークン消費量の上限チェックとしては合算して問題ない(キャッシュ書込分の概算)。 */
    @SerialName("cache_creation_input_tokens") val cacheCreationInputTokens: Int = 0,
    @SerialName("cache_read_input_tokens") val cacheReadInputTokens: Int = 0
) {
    val total: Int get() = inputTokens + outputTokens + cacheCreationInputTokens + cacheReadInputTokens
}

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
    val dealPhase: String? = null,
    val followupDraft: String? = null
)

@Serializable
data class DecisionDto(val content: String)

@Serializable
data class TodoDto(
    val task: String,
    val assignee: String = "未定",
    val deadline: String = "未定",
    val deadlineDate: String? = null
)

@Serializable
data class NextMeetingDto(
    val date: String? = null,
    val originalText: String? = null
)

@Serializable
data class ConcernDto(val content: String)

fun SummaryDto.toDomain(): MeetingSummary = MeetingSummary(
    decisions = decisions.map { Decision(it.content.stripLlmControlTokens()) },
    todos = todos.map {
        TodoItem(
            it.task.stripLlmControlTokens(),
            it.assignee.stripLlmControlTokens(),
            it.deadline.stripLlmControlTokens(),
            it.deadlineDate?.takeIf { d -> d.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
        )
    },
    nextMeeting = NextMeeting(nextMeeting?.date, nextMeeting?.originalText),
    concerns = concerns.map { Concern(it.content.stripLlmControlTokens()) },
    summary = summary.stripLlmControlTokens(),
    dealPhase = DealPhase.fromWire(dealPhase),
    followupDraft = followupDraft?.stripLlmControlTokens()?.takeIf { it.isNotEmpty() }
)
