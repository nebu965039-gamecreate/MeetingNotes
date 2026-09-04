package com.meetingnotes.ui.common

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 次回打ち合わせの日付を選ぶ(F7)。時刻の手動指定は当面なし(AI 抽出は日時対応)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextMeetingDatePickerDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val initialMillis = (initialDate ?: LocalDate.now().plusWeeks(1))
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text("設定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}
