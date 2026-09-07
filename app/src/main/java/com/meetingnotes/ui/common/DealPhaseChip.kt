package com.meetingnotes.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.ui.theme.PhaseTagColors
import com.meetingnotes.ui.theme.ThemeMode

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
    val app = LocalContext.current.applicationContext as MeetingNotesApp
    val darkTheme = when (app.themeModeState.value) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = PhaseTagColors.of(phase, darkTheme)

    // ラベル長がバラつく(「提案」〜「初回接触」)ので幅を揃え、折り返さないようにする。
    val base = Modifier.widthIn(min = 84.dp)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = colors.container,
        border = BorderStroke(1.dp, colors.content.copy(alpha = 0.35f)),
        modifier = if (onClick != null) base.then(modifier).clickable(onClick = onClick) else base.then(modifier)
    ) {
        Text(
            text = phase?.label ?: "未設定",
            style = MaterialTheme.typography.labelMedium,
            color = colors.content,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
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
