package com.meetingnotes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meetingnotes.data.local.OpenTodo
import com.meetingnotes.ui.common.SectionHeading
import java.time.LocalDate

/** 一覧に表示する最大件数。残りは「すべて表示」で ToDo タブへ。 */
private const val MAX_VISIBLE = 5

/** ホーム「期限切れ」カード。期限切れの未完了 ToDo(最大5件)。 */
@Composable
fun OverdueTodoBoard(
    items: List<OpenTodo>,
    onOpen: (todo: OpenTodo) -> Unit,
    onComplete: (todoId: Long) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    DueTodoBoardCard(
        title = "期限切れ",
        items = items,
        onOpen = onOpen,
        onComplete = onComplete,
        onShowAll = onShowAll,
        modifier = modifier
    )
}

/** ホーム「3日以内のToDo」カード。今日〜3日以内が期限の未完了 ToDo(最大5件)。 */
@Composable
fun DueSoonTodoBoard(
    items: List<OpenTodo>,
    onOpen: (todo: OpenTodo) -> Unit,
    onComplete: (todoId: Long) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    DueTodoBoardCard(
        title = "3日以内のToDo",
        items = items,
        onOpen = onOpen,
        onComplete = onComplete,
        onShowAll = onShowAll,
        modifier = modifier
    )
}

@Composable
private fun DueTodoBoardCard(
    title: String,
    items: List<OpenTodo>,
    onOpen: (todo: OpenTodo) -> Unit,
    onComplete: (todoId: Long) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeading(
                title = title,
                count = "${items.size}件",
                trailing = {
                    Text(
                        "すべて表示",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable(onClick = onShowAll)
                    )
                }
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items.take(MAX_VISIBLE).forEach { t ->
                    val due = runCatching { LocalDate.parse(t.dueDate) }.getOrNull()
                    val overdue = due != null && due.isBefore(today)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(t) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onComplete(t.todoId) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.CheckCircleOutline,
                                contentDescription = "完了にする",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                t.task,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${t.clientName}・${dueLabel(due, today)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (overdue) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun dueLabel(due: LocalDate?, today: LocalDate): String {
    if (due == null) return ""
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
    return when {
        days < 0 -> "期限切れ（${-days}日）"
        days == 0L -> "今日まで"
        days == 1L -> "明日まで"
        else -> "${due.monthValue}/${due.dayOfMonth}まで"
    }
}
