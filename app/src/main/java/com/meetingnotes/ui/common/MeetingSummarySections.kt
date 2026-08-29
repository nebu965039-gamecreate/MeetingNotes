package com.meetingnotes.ui.common

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

fun LazyListScope.meetingSummarySections(
    summaryText: String,
    decisions: List<String>,
    concerns: List<String>
) {
    item {
        Text(text = "サマリー", style = MaterialTheme.typography.titleMedium)
        Text(text = summaryText)
    }

    item { Text(text = "決定事項", style = MaterialTheme.typography.titleMedium) }
    if (decisions.isEmpty()) {
        item { Text("(なし)") }
    } else {
        items(decisions) { Text(text = "・$it") }
    }

    item { Text(text = "懸念点・注意点", style = MaterialTheme.typography.titleMedium) }
    if (concerns.isEmpty()) {
        item { Text("(なし)") }
    } else {
        items(concerns) { Text(text = "・$it") }
    }
}

/** 次回打ち合わせセクション。ToDoも含めた全項目の最後に表示する。 */
fun LazyListScope.nextMeetingSection(display: String) {
    item {
        Text(text = "次回打ち合わせ", style = MaterialTheme.typography.titleMedium)
        Text(text = display)
    }
}
