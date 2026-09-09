package com.meetingnotes.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.billing.ProAccess
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.common.FolderTab
import com.meetingnotes.ui.common.FolderTabRow
import com.meetingnotes.ui.common.ProLockedContent
import com.meetingnotes.ui.common.ProPaywallDialog
import com.meetingnotes.ui.common.TabTopBar
import com.meetingnotes.ui.sales.SalesReportTab
import com.meetingnotes.ui.theme.AnalyticsChartColors
import com.meetingnotes.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(repository: MeetingRepository, onHome: () -> Unit) {
    val isPro by ProAccess.isProFlow.collectAsState()
    val locked = ProAccess.gatingEnabled && !isPro
    var showPaywall by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val tabs = listOf("売上", "活動", "顧客", "フォロー")

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TabTopBar(icon = Icons.Filled.BarChart, title = "分析", onHome = onHome)
                if (!locked) {
                    FolderTabRow(
                        tabs = tabs.map { FolderTab(it) },
                        selectedIndex = tab,
                        onSelect = { tab = it }
                    )
                }
            }
        }
    ) { padding ->
        if (locked) {
            ProLockedContent(
                title = "分析は Pro 限定の機能です",
                message = "案件・商談・フォローの数値を月次で振り返れます。",
                onLearnMore = { showPaywall = true },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            val viewModel: AnalyticsViewModel =
                viewModel(factory = AnalyticsViewModel.factory(repository))
            val contentMod = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
            val app = LocalContext.current.applicationContext as MeetingNotesApp
            val darkTheme = when (app.themeModeState.value) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            when (tab) {
                0 -> SalesReportTab(repository, contentMod)
                1 -> {
                    val stats by viewModel.activity.collectAsState()
                    ActivityTab(stats, AnalyticsChartColors.activity(darkTheme), contentMod)
                }
                2 -> {
                    val stats by viewModel.customers.collectAsState()
                    CustomerTab(stats, AnalyticsChartColors.customers(darkTheme), contentMod)
                }
                else -> {
                    val stats by viewModel.follow.collectAsState()
                    FollowTab(
                        stats,
                        todoColor = AnalyticsChartColors.followTodo(darkTheme),
                        emailColor = AnalyticsChartColors.followEmail(darkTheme),
                        modifier = contentMod
                    )
                }
            }
        }
    }

    if (showPaywall) {
        ProPaywallDialog(featureName = "分析", onDismiss = { showPaywall = false })
    }
}

// ---- shared bits ----

@Composable
private fun AnalyticsTabList(modifier: Modifier, content: @Composable () -> Unit): Unit =
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) { item { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { content() } } }

@Composable
private fun StatCard(label: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
private fun KeyValueRow(k: String, v: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            v,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (emphasize) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 月次トレンド1枚。上に「今月 / 前月比 / 月平均」の要約、下に各バーの上へ数値を載せた棒グラフ。
 * バーは細め、横軸の月ラベルは「M月」。最新月はアウトラインで強調。
 */
@Composable
private fun MonthlyTrend(series: List<MonthCount>, unit: String, barColor: Color) {
    val values = series.map { it.count }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val thisMonth = values.lastOrNull() ?: 0
    val lastMonth = values.getOrElse(values.size - 2) { 0 }
    val delta = thisMonth - lastMonth
    val avg = if (values.isNotEmpty()) values.sum().toDouble() / values.size else 0.0
    val lastIndex = values.lastIndex

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SummaryStat("今月", "$thisMonth $unit")
            SummaryStat(
                "前月比",
                (if (delta >= 0) "+$delta" else "$delta") + " $unit",
                color = when {
                    delta > 0 -> barColor
                    delta < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            SummaryStat("月平均", String.format(java.util.Locale.JAPAN, "%.1f", avg))
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            values.forEachIndexed { i, v ->
                val isLast = i == lastIndex
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        v.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                        color = if (isLast) barColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .height((4 + 66 * v / max).dp)
                            .background(barColor, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .then(
                                if (isLast) Modifier.border(
                                    1.5.dp, barColor, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                ) else Modifier
                            )
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            series.forEachIndexed { i, mc ->
                Text(
                    "${mc.month.monthValue}月",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    maxLines = 1,
                    softWrap = false,
                    color = if (i == lastIndex) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun HBar(fraction: Float, color: Color) {
    Box(
        Modifier.fillMaxWidth().height(8.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp)
                .background(color, RoundedCornerShape(999.dp))
        )
    }
}

@Composable
private fun EmptyTab(text: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp)
        )
    }
}

// ---- 活動 ----

@Composable
private fun ActivityTab(stats: ActivityStats?, barColor: Color, modifier: Modifier) {
    if (stats == null || (stats.inPerson + stats.remote) == 0) {
        EmptyTab("まだ商談の記録がありません。", modifier); return
    }
    AnalyticsTabList(modifier) {
        StatCard("月次の商談数（直近12ヶ月）") {
            MonthlyTrend(stats.monthlyMeetings, unit = "件", barColor = barColor)
        }
        StatCard("実施形態・時間") {
            KeyValueRow("対面", "${stats.inPerson} 件")
            KeyValueRow("リモート会議", "${stats.remote} 件")
            stats.avgDurationMin?.let { KeyValueRow("平均録音時間", "$it 分") }
            KeyValueRow("合計録音時間", "${stats.totalHours} 時間")
        }
    }
}

// ---- 顧客 ----

@Composable
private fun CustomerTab(stats: CustomerStats?, barColor: Color, modifier: Modifier) {
    if (stats == null) { EmptyTab("集計中…", modifier); return }
    AnalyticsTabList(modifier) {
        StatCard("新規クライアント数（直近12ヶ月）") {
            MonthlyTrend(stats.monthlyNewClients, unit = "社", barColor = barColor)
        }
        if (stats.leadSourceCounts.isNotEmpty()) {
            StatCard("流入経路の内訳") {
                val max = stats.leadSourceCounts.maxOf { it.count }.coerceAtLeast(1)
                stats.leadSourceCounts.forEach {
                    KeyValueRow(it.label, "${it.count} 社")
                    HBar(it.count.toFloat() / max, barColor)
                }
            }
        }
        if (stats.leadSourceWinRates.isNotEmpty()) {
            StatCard("流入経路別の成約率") {
                stats.leadSourceWinRates.forEach {
                    KeyValueRow(it.label, (it.rate?.let { r -> "$r%" } ?: "—") + "（成約${it.won}/失注${it.lost}）")
                    HBar((it.rate ?: 0) / 100f, barColor)
                }
            }
        }
        if (stats.topReferrers.isNotEmpty()) {
            StatCard("紹介元ランキング") {
                stats.topReferrers.forEach { KeyValueRow(it.label, "${it.count} 社") }
            }
        }
        if (stats.leadSourceCounts.isEmpty() && stats.topReferrers.isEmpty()) {
            Text(
                "クライアントの「情報を編集」で流入経路・紹介元を入力すると、ここに集計されます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---- フォロー ----

@Composable
private fun FollowTab(stats: FollowStats?, todoColor: Color, emailColor: Color, modifier: Modifier) {
    if (stats == null || stats.todoTotal == 0) {
        EmptyTab("まだ ToDo がありません。", modifier); return
    }
    AnalyticsTabList(modifier) {
        StatCard("ToDo の消化") {
            KeyValueRow("完了率", stats.todoDoneRate?.let { "$it%" } ?: "—")
            HBar((stats.todoDoneRate ?: 0) / 100f, todoColor)
            KeyValueRow("完了 / 全体", "${stats.todoDone} / ${stats.todoTotal}")
            KeyValueRow("期限切れの未完了", "${stats.overdueOpen} 件", emphasize = stats.overdueOpen > 0)
        }
        StatCard("フォローアップ") {
            KeyValueRow("お礼メール送信率", stats.emailFollowRate?.let { "$it%" } ?: "—")
            HBar((stats.emailFollowRate ?: 0) / 100f, emailColor)
            stats.avgFollowDays?.let {
                KeyValueRow("平均フォロー日数", String.format(java.util.Locale.JAPAN, "%.1f 日", it))
            }
        }
    }
}
