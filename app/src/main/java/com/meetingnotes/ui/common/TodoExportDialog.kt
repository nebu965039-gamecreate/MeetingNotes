package com.meetingnotes.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meetingnotes.billing.ProAccess
import com.meetingnotes.ui.theme.OnProGold
import com.meetingnotes.ui.theme.ProGold

/** ToDo 一覧の書き出し形式。議事録エクスポートの `ExportFormat` から Excel/CSV だけを切り出した専用版。 */
enum class TodoExportFormat { EXCEL, CSV }

/**
 * ToDo 一覧(タスク / 担当 / 期限 / 完了)だけを Excel/CSV で書き出すための軽量ダイアログ。
 * クライアント詳細の ToDo タブ(そのクライアントの ToDo)・ToDo 画面(下部ナビ、全クライアント横断)
 * の両方から共有。形式を選ぶと「共有」「保存」のどちらかを選べる(1つの選択で両方の導線を出す)。
 * Excel/CSV は議事録エクスポートと同じく Pro 限定形式のため [ProGate] で統一的にロックする。
 */
@Composable
fun TodoExportDialog(
    onDismiss: () -> Unit,
    onShare: (TodoExportFormat) -> Unit,
    onSave: (TodoExportFormat) -> Unit
) {
    var format by remember { mutableStateOf<TodoExportFormat?>(null) }
    var paywallFeature by remember { mutableStateOf<String?>(null) }
    val proLocked = ProAccess.shouldLock
    val options = listOf(TodoExportFormat.EXCEL to "Excel", TodoExportFormat.CSV to "CSV")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ToDoを書き出す") },
        text = {
            Column(
                // 形式の切り替えでモーダルの横幅が変わらないよう固定する。
                modifier = Modifier.width(300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "ToDo 一覧(タスク / 担当 / 期限 / 完了)を書き出します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = if (proLocked) Modifier.padding(top = 12.dp) else Modifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { (fmt, label) ->
                        ProGate(
                            locked = proLocked,
                            onLockedTap = { paywallFeature = "$label 形式での書き出し" }
                        ) {
                            FilterChip(
                                selected = format == fmt,
                                enabled = !proLocked,
                                onClick = { format = fmt },
                                label = { Text(label) },
                                border = if (proLocked) {
                                    BorderStroke(2.dp, ProGold)
                                } else {
                                    FilterChipDefaults.filterChipBorder(enabled = true, selected = format == fmt)
                                },
                                colors = if (proLocked) {
                                    FilterChipDefaults.filterChipColors(
                                        disabledContainerColor = ProGold.copy(alpha = 0.18f),
                                        disabledLabelColor = OnProGold.copy(alpha = 0.75f)
                                    )
                                } else {
                                    FilterChipDefaults.filterChipColors()
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { format?.let(onSave) },
                    enabled = format != null
                ) { Text("保存") }
                TextButton(
                    onClick = { format?.let(onShare) },
                    enabled = format != null
                ) { Text("共有") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )

    paywallFeature?.let { feature ->
        ProPaywallDialog(featureName = feature, onDismiss = { paywallFeature = null })
    }
}
