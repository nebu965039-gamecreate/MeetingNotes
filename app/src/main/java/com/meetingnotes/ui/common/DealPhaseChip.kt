package com.meetingnotes.ui.common

import androidx.compose.foundation.BorderStroke
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
import com.meetingnotes.ui.theme.PhaseTagColors

/** 表示に使う実効フェーズ = ユーザー上書き ?: AI 推定。 */
fun MeetingEntity.effectivePhase(): DealPhase? =
    DealPhase.fromWire(phaseOverride ?: dealPhase)

/**
 * 商談フェーズのチップ。予定・フォローアップなど他機能のタグと見た目を統一するため、
 * 「枠あり + 塗りつぶし」の1スタイルに固定。[onClick] が渡されたらタップで変更できる。
 */
@Composable
fun DealPhaseChip(
    phase: DealPhase?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = PhaseTagColors.of(phase)

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = colors.container,
        border = BorderStroke(1.dp, colors.content.copy(alpha = 0.35f)),
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    ) {
        Text(
            text = phase?.label ?: "フェーズ未設定",
            style = MaterialTheme.typography.labelMedium,
            color = colors.content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
