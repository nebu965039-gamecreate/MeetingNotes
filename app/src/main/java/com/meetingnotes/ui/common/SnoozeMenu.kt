package com.meetingnotes.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId

/**
 * ToDo 1件をスヌーズ(=催促する画面から一時的に外す)するメニュー。
 * [snoozed] が true なら「スヌーズ解除」だけを出す。false なら 1日/3日/1週間/1ヶ月/日付指定。
 * いずれも再表示日時(epoch millis)を [onSnooze] に渡す。解除は [onClearSnooze]。
 */
@Composable
fun SnoozeMenu(
    snoozed: Boolean,
    onSnooze: (untilMillis: Long) -> Unit,
    onClearSnooze: () -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    fun daysFromNow(days: Long): Long =
        LocalDate.now().plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(
                if (snoozed) Icons.Filled.Snooze else Icons.Outlined.Snooze,
                contentDescription = if (snoozed) "スヌーズ中" else "スヌーズ",
                tint = if (snoozed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (snoozed) {
                DropdownMenuItem(
                    text = { Text("スヌーズを解除") },
                    onClick = { onClearSnooze(); open = false }
                )
            } else {
                listOf("1日後" to 1L, "3日後" to 3L, "1週間後" to 7L, "1ヶ月後" to 30L).forEach { (label, days) ->
                    DropdownMenuItem(
                        text = { Text("$label に再表示") },
                        onClick = { onSnooze(daysFromNow(days)); open = false }
                    )
                }
                DropdownMenuItem(
                    text = { Text("日付を指定…") },
                    onClick = { open = false; showPicker = true }
                )
            }
        }
    }

    if (showPicker) {
        PickDateDialog(
            initial = LocalDate.now().plusWeeks(1),
            title = "再表示する日",
            onDismiss = { showPicker = false },
            onConfirm = { d ->
                onSnooze(d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                showPicker = false
            }
        )
    }
}
