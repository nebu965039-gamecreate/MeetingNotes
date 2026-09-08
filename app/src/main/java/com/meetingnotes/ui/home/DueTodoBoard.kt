package com.meetingnotes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.TaskAlt
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meetingnotes.data.local.OpenTodo
import java.time.LocalDate

/** ホーム「やること(期限あり)」。期限切れ + 今日 + 3日以内の未完了 ToDo。 */
@Composable
fun DueTodoBoard(
    items: List<OpenTodo>,
    onOpen: (meetingId: Long) -> Unit,
    onComplete: (todoId: Long) -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.TaskAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("やること（期限あり）", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${items.size}件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items.forEach { t ->
                    val due = runCatching { LocalDate.parse(t.dueDate) }.getOrNull()
                    val overdue = due != null && due.isBefore(today)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(t.meetingId) }
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
