package com.meetingnotes.data.model

data class Decision(
    val content: String
)

data class TodoItem(
    val task: String,
    val assignee: String,
    val deadline: String
)

data class NextMeeting(
    val date: String?,
    val originalText: String?
)

data class Concern(
    val content: String
)

data class MeetingSummary(
    val decisions: List<Decision>,
    val todos: List<TodoItem>,
    val nextMeeting: NextMeeting,
    val concerns: List<Concern>,
    val summary: String,
    val dealPhase: DealPhase? = null,
    /** 要約と同時に生成した、商談直後に相手へ送るフォローアップ文面の下書き(丁寧体)。 */
    val followupDraft: String? = null
)
