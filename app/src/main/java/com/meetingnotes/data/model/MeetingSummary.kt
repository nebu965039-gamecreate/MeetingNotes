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
    val summary: String
)
