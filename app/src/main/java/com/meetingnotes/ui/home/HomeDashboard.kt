package com.meetingnotes.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.ui.theme.PhaseChartColors
import com.meetingnotes.ui.theme.ThemeMode

/**
 * ホーム画面 最上部のダッシュボード。進行中フェーズのドーナツ + 数字3つ +
 * (母数があれば)全期間の成約率。表示のみでタップ動作は持たない。
 */
@Composable
fun HomeDashboardCard(data: HomeDashboard, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as MeetingNotesApp
    val darkTheme = when (app.themeModeState.value) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PhaseDonut(data.phase, darkTheme, Modifier.size(88.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LegendRow("ヒアリング", data.phase.hearing, PhaseChartColors.of(DealPhase.HEARING, darkTheme))
                    LegendRow("提案", data.phase.proposal, PhaseChartColors.of(DealPhase.PROPOSAL, darkTheme))
                    LegendRow("見積提示", data.phase.quoted, PhaseChartColors.of(DealPhase.QUOTED, darkTheme))
                    LegendRow("検討中", data.phase.considering, PhaseChartColors.of(DealPhase.CONSIDERING, darkTheme))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCell("クライアント", data.clientCount.toString(), Modifier.weight(1f))
                StatCell("今月の商談", data.meetingsThisMonth.toString(), Modifier.weight(1f))
                StatCell(
                    "未完了ToDo",
                    data.openTodoTotal.toString(),
                    Modifier.weight(1f),
                    valueColor = if (data.openTodoTotal > 0) MaterialTheme.colorScheme.error else null
                )
            }

            data.winRate?.let { rate ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        "全期間の成約率",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$rate%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(7.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(rate / 100f)
                                .height(7.dp)
                                .background(
                                    PhaseChartColors.of(DealPhase.WON, darkTheme),
                                    RoundedCornerShape(999.dp)
                                )
                        )
                    }
                    Text(
                        "成約${data.wonCount} / 失注${data.lostCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (data.wonAmountThisMonth.isNotEmpty() || data.pipelineAmount.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AmountRow("今月の成約", data.wonAmountThisMonth)
                AmountRow("パイプライン", data.pipelineAmount)
            }
        }
    }
}

@Composable
private fun PhaseDonut(phase: PhaseTrackerCounts, darkTheme: Boolean, modifier: Modifier) {
    val empty = MaterialTheme.colorScheme.outlineVariant
    val segments = listOf(
        DealPhase.HEARING to phase.hearing,
        DealPhase.PROPOSAL to phase.proposal,
        DealPhase.QUOTED to phase.quoted,
        DealPhase.CONSIDERING to phase.considering
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = modifier) {
            val strokeW = 14.dp.toPx()
            val inset = strokeW / 2f
            val arcSize = Size(size.width - strokeW, size.height - strokeW)
            val topLeft = Offset(inset, inset)
            if (phase.total == 0) {
                drawArc(
                    color = empty, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(strokeW), topLeft = topLeft, size = arcSize
                )
            } else {
                var start = -90f
                segments.forEach { (ph, count) ->
                    if (count > 0) {
                        val sweep = count.toFloat() / phase.total * 360f
                        drawArc(
                            color = PhaseChartColors.of(ph, darkTheme),
                            startAngle = start, sweepAngle = sweep, useCenter = false,
                            style = Stroke(strokeW, cap = StrokeCap.Butt),
                            topLeft = topLeft, size = arcSize
                        )
                        start += sweep
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${phase.total}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                "進行中",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AmountRow(label: String, amounts: Map<String, Long>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        val text = if (amounts.isEmpty()) "—" else amounts.entries
            .sortedByDescending { it.value }
            .joinToString(" / ") { com.meetingnotes.util.formatMoney(it.value, it.key) }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LegendRow(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        Text(
            "$count",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier, valueColor: Color? = null) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(11.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
