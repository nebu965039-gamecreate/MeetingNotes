package com.meetingnotes.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 次回打ち合わせの日付(＋任意で時刻)を選ぶダイアログ(F7)。
 * [initial] が時刻付き(!= 00:00 または [initialHasTime] = true)なら「時刻を指定する」が初期ON。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextMeetingDateTimeDialog(
    initial: LocalDateTime?,
    initialHasTime: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (dateTime: LocalDateTime, hasTime: Boolean) -> Unit,
    title: String = "次回打ち合わせ"
) {
    val base = initial ?: LocalDate.now().plusWeeks(1).atTime(10, 0)
    var date by remember { mutableStateOf(base.toLocalDate()) }
    var hasTime by remember { mutableStateOf(initialHasTime) }
    var hour by remember { mutableIntStateOf(base.hour) }
    var minute by remember { mutableIntStateOf(base.minute) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldRow(
                    label = "日付",
                    value = "${date.year}年${date.monthValue}月${date.dayOfMonth}日",
                    onClick = { showDatePicker = true }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("時刻を指定する")
                    Switch(checked = hasTime, onCheckedChange = { hasTime = it })
                }
                if (hasTime) {
                    FieldRow(
                        label = "時刻",
                        value = "%02d:%02d".format(hour, minute),
                        onClick = { showTimePicker = true }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val dt = if (hasTime) date.atTime(hour, minute) else date.atStartOfDay()
                onConfirm(dt, hasTime)
            }) { Text("設定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } }
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        var textInput by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("時刻を選択")
                    IconButton(onClick = { textInput = !textInput }) {
                        if (textInput) {
                            Icon(Icons.Filled.Schedule, contentDescription = "ホイール入力に切り替え")
                        } else {
                            Icon(Icons.Filled.Keyboard, contentDescription = "キーボード入力に切り替え")
                        }
                    }
                }
            },
            text = { if (textInput) TimeInput(state = timeState) else TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    hour = timeState.hour
                    minute = timeState.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("キャンセル") } }
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}
