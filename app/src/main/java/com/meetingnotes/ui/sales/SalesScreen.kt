package com.meetingnotes.ui.sales

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.billing.ProAccess
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.ui.common.DealPhaseChip
import com.meetingnotes.ui.common.ProPaywallDialog
import com.meetingnotes.ui.theme.PhaseChartColors
import com.meetingnotes.ui.theme.ThemeMode
import com.meetingnotes.util.Currency
import com.meetingnotes.util.formatMoney
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthLabelFormatter = DateTimeFormatter.ofPattern("yy/M", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(repository: MeetingRepository, onBack: () -> Unit) {
    val locked = ProAccess.shouldLock
    var showPaywall by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("売上・実績") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        bottomBar = { BannerAdView(Modifier.navigationBarsPadding()) }
    ) { padding ->
        if (locked) {
            LockedContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onLearnMore = { showPaywall = true }
            )
        } else {
            val viewModel: SalesViewModel = viewModel(factory = SalesViewModel.factory(repository))
            val period by viewModel.period.collectAsState()
            val reports by viewModel.reports.collectAsState()
            SalesContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                period = period,
                reports = reports,
                onPeriod = viewModel::setPeriod
            )
        }
    }

    if (showPaywall) {
        ProPaywallDialog(featureName = "売上・実績", onDismiss = { showPaywall = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalesContent(
    modifier: Modifier,
    period: SalesPeriod,
    reports: List<SalesCurrencyReport>,
    onPeriod: (SalesPeriod) -> Unit
) {
    val app = LocalContext.current.applicationContext as MeetingNotesApp
    val darkTheme = when (app.themeModeState.value) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "period") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SalesPeriod.entries.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = p == period,
                        onClick = { onPeriod(p) },
                        shape = SegmentedButtonDefaults.itemShape(index, SalesPeriod.entries.size)
                    ) { Text(p.label) }
                }
            }
        }

        if (reports.isEmpty()) {
            item(key = "empty") {
                Text(
                    "成約・失注した案件がまだありません。案件フォームで金額とフェーズを登録すると、ここに集計されます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(reports, key = { it.currencyCode }) { report ->
                CurrencyReportCard(
                    report = report,
                    period = period,
                    showCurrencyHeader = reports.size > 1,
                    barColor = PhaseChartColors.of(DealPhase.WON, darkTheme)
                )
            }
        }
    }
}

@Composable
private fun CurrencyReportCard(
    report: SalesCurrencyReport,
    period: SalesPeriod,
    showCurrencyHeader: Boolean,
    barColor: Color
) {
    val code = report.currencyCode
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (showCurrencyHeader) {
                Text(Currency.of(code).label, style = MaterialTheme.typography.titleMedium)
            }

            SectionLabel("月次成約額(直近12ヶ月)")
            MonthlyBarChart(report.monthlyWon, barColor)

            SectionLabel("サマリー")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("成約額(${period.label})", formatMoney(report.wonAmount, code), Modifier.weight(1f))
                Stat("平均単価", report.avgDealSize?.let { formatMoney(it, code) } ?: "—", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("パイプライン", formatMoney(report.pipelineTotal, code), Modifier.weight(1f))
                Stat(
                    "成約率(全期間)",
                    buildString {
                        append(report.winRateByAmount?.let { "金額 $it%" } ?: "金額 —")
                        append(" / ")
                        append(report.winRateByCount?.let { "件数 $it%" } ?: "件数 —")
                    },
                    Modifier.weight(1f)
                )
            }
            Text(
                "成約 ${report.wonCountAllTime}件 / 失注 ${report.lostCount}件(全期間)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (report.lostReasonBreakdown.isNotEmpty()) {
                SectionLabel("失注理由の内訳(全期間)")
                report.lostReasonBreakdown.forEach { rc ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            rc.reason,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${rc.count}件",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (report.pipelineByPhase.isNotEmpty()) {
                SectionLabel("フェーズ別パイプライン")
                val maxPhase = report.pipelineByPhase.maxOf { it.amount }.coerceAtLeast(1)
                report.pipelineByPhase.forEach { pa ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.width(84.dp)) {
                            DealPhaseChip(phase = pa.phase)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(pa.amount.toFloat() / maxPhase)
                                    .height(8.dp)
                                    .background(barColor, RoundedCornerShape(999.dp))
                            )
                        }
                        Text(
                            formatMoney(pa.amount, code),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MonthlyBarChart(data: List<MonthlyAmount>, barColor: Color) {
    val maxAmount = data.maxOfOrNull { it.amount } ?: 0L
    val empty = MaterialTheme.colorScheme.outlineVariant
    if (maxAmount <= 0L) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "この期間の成約はありません",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(11.dp))
                .padding(8.dp)
        ) {
            val slot = size.width / data.size
            val barW = slot * 0.58f
            data.forEachIndexed { i, m ->
                val h = if (m.amount <= 0L) 0f else (m.amount.toFloat() / maxAmount) * size.height
                if (h > 0f) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(i * slot + (slot - barW) / 2f, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                    )
                } else {
                    drawRoundRect(
                        color = empty,
                        topLeft = Offset(i * slot + (slot - barW) / 2f, size.height - 2f),
                        size = Size(barW, 2f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f, 1f)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(data.first(), data[data.size / 2], data.last()).forEach {
                Text(
                    it.month.format(monthLabelFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LockedContent(modifier: Modifier, onLearnMore: () -> Unit) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.WorkspacePremium,
            contentDescription = null,
            tint = com.meetingnotes.ui.theme.ProGold
        )
        Text(
            "売上・実績は Pro 限定の機能です",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "案件の成約額・パイプライン・成約率を月次で振り返れます。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onLearnMore) { Text("Pro について") }
    }
}
