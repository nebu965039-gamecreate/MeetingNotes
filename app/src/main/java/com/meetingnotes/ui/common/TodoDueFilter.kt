package com.meetingnotes.ui.common

import java.time.LocalDate

/**
 * ToDo の期限による絞り込み。ホームの「期限切れ」「3日以内」の各セクションから
 * ToDo タブ(`FollowupListScreen`)へ「すべて表示」で渡す絞り込み状態と、
 * ToDo タブ内のフィルタチップの両方で使う共通の区分。
 */
enum class TodoDueFilter(val label: String) {
    ALL("すべて"),
    DUE_SOON("期限が3日以内"),
    OVERDUE("期限切れ")
}

/** [dueDate](ISO 8601、`yyyy-MM-dd`)が [filter] に合致するか。期限未設定は ALL 以外では常に false。 */
fun matchesDueFilter(dueDate: String?, filter: TodoDueFilter, today: LocalDate = LocalDate.now()): Boolean {
    if (filter == TodoDueFilter.ALL) return true
    val due = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    return when (filter) {
        TodoDueFilter.OVERDUE -> due.isBefore(today)
        TodoDueFilter.DUE_SOON -> !due.isBefore(today) && !due.isAfter(today.plusDays(3))
        TodoDueFilter.ALL -> true
    }
}
