package com.meetingnotes.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.model.DealPhase

/** 表示に使う実効フェーズ = ユーザー上書き ?: AI 推定。 */
fun MeetingEntity.effectivePhase(): DealPhase? =
    DealPhase.fromWire(phaseOverride ?: dealPhase)

/** 商談フェーズの小さなチップ。[onClick] が渡されたらタップで変更できる見た目に。 */
@Composable
fun DealPhaseChip(
    phase: DealPhase?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isSet = phase != null
    val container =
        if (isSet) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val content =
        if (isSet) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    ) {
        Text(
            text = phase?.label ?: "フェーズ未設定",
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

/** フェーズ選択ダイアログ。選ぶと即 [onSelect] して閉じる想定。 */
@Composable
fun DealPhasePickerDialog(
    current: DealPhase?,
    onDismiss: () -> Unit,
    onSelect: (DealPhase) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("商談フェーズ") },
        text = {
            Column {
                DealPhase.entries.forEach { phase ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(phase) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == phase, onClick = { onSelect(phase) })
                        Text(phase.label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}
