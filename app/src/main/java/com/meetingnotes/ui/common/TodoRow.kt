package com.meetingnotes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** ToDo 1行の表示に必要な最小限のデータ。エンティティから詰め替えて渡す。 */
data class TodoRowData(
    val id: Long,
    val task: String,
    val dueDate: String?,          // ISO(yyyy-MM-dd)。解決できていなければ null
    val deadlineText: String = "", // dueDate が無いときの原文フォールバック
    val done: Boolean = false,
    val snoozedUntil: Long? = null,
    val clientName: String? = null // 横断 ToDo 一覧でのみ表示。クライアント詳細内では null
)

private val mdFormatter = DateTimeFormatter.ofPattern("M/d")

fun todoIsSnoozed(snoozedUntil: Long?, nowMillis: Long = System.currentTimeMillis()): Boolean =
    snoozedUntil != null && snoozedUntil > nowMillis

/**
 * ToDo 1行(チェックボックス + 本文 + サブ行 + スヌーズチップ + trailing)。
 * クライアント詳細の ToDo タブと、ToDo 画面の横断一覧で共有する。
 */
@Composable
fun TodoRow(
    data: TodoRowData,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCheckbox: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val snoozed = !data.done && todoIsSnoozed(data.snoozedUntil)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCheckbox) {
            Checkbox(checked = data.done, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                data.task,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textDecoration = if (data.done) TextDecoration.LineThrough else null,
                color = if (snoozed && !data.done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sub = buildList {
                    data.clientName?.let { add(it) }
                    dueLabel(data.dueDate, data.deadlineText)?.let { add(it) }
                }.joinToString(" ・ ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (snoozed) {
                    if (sub.isNotEmpty()) Spacer(Modifier.width(6.dp))
                    SnoozeChip(data.snoozedUntil!!)
                }
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SnoozeChip(untilMillis: Long) {
    val d = Instant.ofEpochMilli(untilMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            "😴 ${d.monthValue}/${d.dayOfMonth}まで",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun dueLabel(dueDate: String?, deadlineText: String): String? {
    val parsed = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return when {
        parsed != null -> "期限 ${parsed.format(mdFormatter)}"
        deadlineText.isNotBlank() && deadlineText != "未定" -> "期限 $deadlineText"
        else -> null
    }
}
