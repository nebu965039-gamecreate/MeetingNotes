package com.meetingnotes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.ui.theme.ThemeMode
import com.meetingnotes.ui.theme.TodoDueSoonColor
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
    /**
     * スヌーズ中の 😴 M/d まで チップを表示するか。ToDo 画面(`FollowupListScreen`)の「ToDo」タブは
     * クライアント名も同じ行にあり、チップまで出すと横幅が足りずレイアウトが崩れるため false で使う
     * (2026-09-15。スヌーズ状態の確認は専用の「スヌーズ」タブで行う)。既定は true。
     */
    showSnoozeChip: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val snoozed = !data.done && todoIsSnoozed(data.snoozedUntil)
    val app = LocalContext.current.applicationContext as MeetingNotesApp
    val darkTheme = when (app.themeModeState.value) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    // 期限の表示色(2026-09-14): 完了済みは強調しない。期限切れ = error(赤)、3日以内 = 黄色、
    // それ以外は従来どおり onSurfaceVariant(グレー)。境界判定は ToDo タブのフィルタと同じ関数を再利用。
    val dueColor = when {
        data.done -> MaterialTheme.colorScheme.onSurfaceVariant
        matchesDueFilter(data.dueDate, TodoDueFilter.OVERDUE) -> MaterialTheme.colorScheme.error
        matchesDueFilter(data.dueDate, TodoDueFilter.DUE_SOON) -> TodoDueSoonColor.of(darkTheme)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 行自体を白いカードにする(2026-09-13): ページ地がティールに統一されたため、
    // 背景指定の無いテキストのみの行だと地に溶けて見えてしまっていた。ClientRow/MeetingRow と同じ
    // surfaceContainer(白)の角丸カードに揃える。
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    val due = dueLabel(data.dueDate, data.deadlineText)
                    if (data.clientName != null) {
                        Text(
                            data.clientName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (due != null) {
                            Text(
                                " ・ ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (due != null) {
                        Text(
                            due,
                            style = MaterialTheme.typography.bodySmall,
                            color = dueColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (snoozed && showSnoozeChip) {
                        if (data.clientName != null || due != null) Spacer(Modifier.width(6.dp))
                        SnoozeChip(data.snoozedUntil!!)
                    }
                }
            }
            trailing?.invoke()
        }
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
