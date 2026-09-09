package com.meetingnotes.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.billing.ProAccess
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.common.ProLockedContent
import com.meetingnotes.ui.common.ProPaywallDialog
import com.meetingnotes.ui.sales.SalesReportTab

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
                TopAppBar(
                    title = { Text("分析", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onHome) {
                            Icon(Icons.Filled.Home, contentDescription = "ホーム")
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (!locked) {
                    PrimaryTabRow(selectedTabIndex = tab) {
                        tabs.forEachIndexed { i, t ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                        }
                    }
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
            val contentMod = Modifier.fillMaxSize().padding(padding)
            when (tab) {
                0 -> SalesReportTab(repository, contentMod)
                1 -> {
                    val stats by viewModel.activity.collectAsState()
                    ActivityTab(stats, contentMod)
                }
                2 -> {
                    val stats by viewModel.customers.collectAsState()
                    CustomerTab(stats, contentMod)
                }
                else -> {
                    val stats by viewModel.follow.collectAsState()
                    FollowTab(stats, contentMod)
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

@Composable
private fun MiniBars(values: List<Int>, labels: List<String>) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(70.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            values.forEach { v ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height((6 + (64 * v / max)).dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HBar(fraction: Float) {
    Box(
        Modifier.fillMaxWidth().height(8.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp))
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

private fun monthLabels(months: List<java.time.YearMonth>): List<String> =
    months.map { "${it.monthValue}" }

// ---- 活動 ----

@Composable
private fun ActivityTab(stats: ActivityStats?, modifier: Modifier) {
    if (stats == null || (stats.inPerson + stats.remote) == 0) {
        EmptyTab("まだ商談の記録がありません。", modifier); return
    }
    AnalyticsTabList(modifier) {
        StatCard("月次の商談数（直近12ヶ月）") {
            MiniBars(stats.monthlyMeetings.map { it.count }, monthLabels(stats.monthlyMeetings.map { it.month }))
        }
        StatCard("今月の商談") {
            KeyValueRow("今月", "${stats.meetingsThisMonth} 件")
            val d = stats.monthOverMonth
            KeyValueRow("前月比", if (d >= 0) "+$d 件" else "$d 件")
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
private fun CustomerTab(stats: CustomerStats?, modifier: Modifier) {
    if (stats == null) { EmptyTab("集計中…", modifier); return }
    AnalyticsTabList(modifier) {
        StatCard("新規クライアント数（直近12ヶ月）") {
            MiniBars(stats.monthlyNewClients.map { it.count }, monthLabels(stats.monthlyNewClients.map { it.month }))
        }
        if (stats.leadSourceCounts.isNotEmpty()) {
            StatCard("流入経路の内訳") {
                val max = stats.leadSourceCounts.maxOf { it.count }.coerceAtLeast(1)
                stats.leadSourceCounts.forEach {
                    KeyValueRow(it.label, "${it.count} 社")
                    HBar(it.count.toFloat() / max)
                }
            }
        }
        if (stats.leadSourceWinRates.isNotEmpty()) {
            StatCard("流入経路別の成約率") {
                stats.leadSourceWinRates.forEach {
                    KeyValueRow(it.label, (it.rate?.let { r -> "$r%" } ?: "—") + "（成約${it.won}/失注${it.lost}）")
                    HBar((it.rate ?: 0) / 100f)
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
private fun FollowTab(stats: FollowStats?, modifier: Modifier) {
    if (stats == null || stats.todoTotal == 0) {
        EmptyTab("まだ ToDo がありません。", modifier); return
    }
    AnalyticsTabList(modifier) {
        StatCard("ToDo の消化") {
            KeyValueRow("完了率", stats.todoDoneRate?.let { "$it%" } ?: "—")
            HBar((stats.todoDoneRate ?: 0) / 100f)
            KeyValueRow("完了 / 全体", "${stats.todoDone} / ${stats.todoTotal}")
            KeyValueRow("期限切れの未完了", "${stats.overdueOpen} 件", emphasize = stats.overdueOpen > 0)
        }
        StatCard("フォローアップ") {
            KeyValueRow("お礼メール送信率", stats.emailFollowRate?.let { "$it%" } ?: "—")
            HBar((stats.emailFollowRate ?: 0) / 100f)
            stats.avgFollowDays?.let {
                KeyValueRow("平均フォロー日数", String.format(java.util.Locale.JAPAN, "%.1f 日", it))
            }
        }
    }
}
