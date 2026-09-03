package com.meetingnotes.ui.client

import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoEntity
import java.text.Collator
import java.util.Locale

/** アーカイブ一覧の並び替え。 */
enum class MeetingSortOrder(val label: String) {
    NEWEST("新しい順"),
    OLDEST("古い順"),
    TITLE("タイトル順")
}

/** 検索結果。`snippet` が空文字ならタイトル一致(追加表示なし)、非空なら一致箇所のプレビュー。 */
data class MeetingSearchResult(val meeting: MeetingEntity, val snippet: String)

/**
 * アーカイブの検索・並び替えロジック(LIKE 相当の部分一致をメモリ上で実行)。
 * ViewModel から切り出して単体テスト可能にしている。
 */
object MeetingArchiveSearch {

    private val jaCollator: Collator = Collator.getInstance(Locale.JAPANESE)

    fun sort(meetings: List<MeetingEntity>, order: MeetingSortOrder): List<MeetingEntity> = when (order) {
        MeetingSortOrder.NEWEST -> meetings.sortedByDescending { it.recordedAt }
        MeetingSortOrder.OLDEST -> meetings.sortedBy { it.recordedAt }
        MeetingSortOrder.TITLE -> meetings.sortedWith(compareBy(jaCollator) { it.title })
    }

    fun search(
        meetings: List<MeetingEntity>,
        todosByMeeting: Map<Long, List<TodoEntity>>,
        query: String,
        order: MeetingSortOrder
    ): List<MeetingSearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val matched = meetings.mapNotNull { m ->
            match(m, todosByMeeting[m.id].orEmpty(), q)?.let { MeetingSearchResult(m, it) }
        }
        val sortedMeetings = sort(matched.map { it.meeting }, order)
        val bySnippet = matched.associateBy { it.meeting.id }
        return sortedMeetings.mapNotNull { bySnippet[it.id] }
    }

    /** 一致すれば表示用プレビュー(タイトル一致なら "")、しなければ null。 */
    fun match(meeting: MeetingEntity, todos: List<TodoEntity>, query: String): String? {
        val q = query.trim()
        if (q.isEmpty()) return null
        if (meeting.title.contains(q, ignoreCase = true)) return ""
        snippet(meeting.summary, q)?.let { return "サマリー: $it" }
        meeting.decisions.firstNotNullOfOrNull { snippet(it, q) }?.let { return "決定事項: $it" }
        meeting.concerns.firstNotNullOfOrNull { snippet(it, q) }?.let { return "懸念点: $it" }
        todos.firstNotNullOfOrNull { snippet("${it.task} ${it.assignee} ${it.deadline}", q) }
            ?.let { return "ToDo: $it" }
        snippet(meeting.transcript, q)?.let { return "文字起こし: $it" }
        return null
    }

    /** text 内の query 周辺を切り出す。無ければ null。 */
    fun snippet(text: String, query: String, radius: Int = 30): String? {
        val idx = text.indexOf(query, ignoreCase = true)
        if (idx < 0) return null
        val start = (idx - radius).coerceAtLeast(0)
        val end = (idx + query.length + radius).coerceAtMost(text.length)
        val body = text.substring(start, end).replace(Regex("\\s+"), " ").trim()
        return (if (start > 0) "…" else "") + body + (if (end < text.length) "…" else "")
    }
}
