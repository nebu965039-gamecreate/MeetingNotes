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
import com.meetingnotes.ui.theme.CreateActionAmber
import java.time.LocalDate
import java.time.ZoneId

/**
 * ToDo 1件をスヌーズ(=催促する画面から一時的に外す)するメニュー。
 * [snoozed] が false(未スヌーズ)なら 1日/3日/1週間/1ヶ月/日付指定を選べるオレンジのボタン。
 * [snoozed] が true のときの挙動は [disableWhenSnoozed] で分岐:
 * - false(既定、クライアント詳細の ToDo タブ用): 従来どおりタップで「スヌーズを解除」だけを出す
 * - true(ToDo 画面の「ToDo」タブ用、2026-09-13〜): 専用の「スヌーズ」タブに解除導線があるため、
 *   ここは非活性(グレーアウト・タップ不可)にして二重の解除導線を作らない
 * 再表示日時(epoch millis)は [onSnooze] に、解除は [onClearSnooze] に渡す。
 */
@Composable
fun SnoozeMenu(
    snoozed: Boolean,
    onSnooze: (untilMillis: Long) -> Unit,
    onClearSnooze: () -> Unit,
    modifier: Modifier = Modifier,
    disableWhenSnoozed: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val locked = snoozed && disableWhenSnoozed

    fun daysFromNow(days: Long): Long =
        LocalDate.now().plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    Box(modifier) {
        IconButton(onClick = { open = true }, enabled = !locked) {
            Icon(
                if (snoozed) Icons.Filled.Snooze else Icons.Outlined.Snooze,
                contentDescription = when {
                    locked -> "スヌーズ中(スヌーズタブから解除)"
                    snoozed -> "スヌーズ中"
                    else -> "スヌーズ"
                },
                tint = when {
                    locked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    snoozed -> MaterialTheme.colorScheme.primary
                    else -> CreateActionAmber
                }
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
