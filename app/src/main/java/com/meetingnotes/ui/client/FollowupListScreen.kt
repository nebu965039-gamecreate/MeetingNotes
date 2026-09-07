package com.meetingnotes.ui.client

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.FollowedUpMeeting
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.ui.common.DealPhaseChip
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowupListScreen(
    repository: MeetingRepository,
    onBack: () -> Unit,
    onOpenMeeting: (Long) -> Unit
) {
    val viewModel: FollowupListViewModel = viewModel(factory = FollowupListViewModel.factory(repository))
    val pending by viewModel.followups.collectAsState()
    val done by viewModel.followedUp.collectAsState()

    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("フォロー") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        bottomBar = { BannerAdView(Modifier.navigationBarsPadding()) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("未フォロー (${pending.size})") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("フォロー済み (${done.size})") }
                )
            }

            if (tab == 0) {
                PendingTab(
                    items = pending,
                    onOpen = onOpenMeeting,
                    onMark = { viewModel.markFollowedUp(it) },
                    onMarkAll = { viewModel.markAllFollowedUp() }
                )
            } else {
                DoneList(
                    items = done,
                    onOpen = onOpenMeeting,
                    onUnmark = { viewModel.unmarkFollowedUp(it) }
                )
            }
        }
    }
}

@Composable
private fun PendingTab(
    items: List<FollowupItem>,
    onOpen: (Long) -> Unit,
    onMark: (List<Long>) -> Unit,
    onMarkAll: () -> Unit
) {
    if (items.isEmpty()) {
        EmptyMessage("フォローが必要な商談はありません。")
        return
    }

    // チェック状態(meetingId -> checked)。リスト内容が変わったら整理する。
    val checked = remember { mutableStateMapOf<Long, Boolean>() }
    val validIds = items.map { it.meetingId }.toSet()
    checked.keys.retainAll(validIds)
    val selectedIds = checked.filterValues { it }.keys.toList()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.meetingId }) { item ->
                FollowupCard(
                    name = item.client.name,
                    subtitle = followupSubtitle(item),
                    phase = item.phase,
                    onClick = { onOpen(item.meetingId) },
                    leading = {
                        Checkbox(
                            checked = checked[item.meetingId] == true,
                            onCheckedChange = { checked[item.meetingId] = it }
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onMark(selectedIds) },
                    enabled = selectedIds.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (selectedIds.isEmpty()) "選択をフォロー済み"
                        else "選択${selectedIds.size}件をフォロー済み"
                    )
                }
                TextButton(onClick = onMarkAll) {
                    Text("すべてフォロー済み")
                }
            }
        }
    }
}

@Composable
private fun DoneList(
    items: List<FollowedUpMeeting>,
    onOpen: (Long) -> Unit,
    onUnmark: (Long) -> Unit
) {
    if (items.isEmpty()) {
        EmptyMessage("フォロー済みの商談はまだありません。")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.meetingId }) { item ->
            FollowupCard(
                name = item.clientName,
                subtitle = "フォロー ${monthDay(item.followedUpAt)}・${item.title}",
                phase = DealPhase.fromWire(item.phaseOverride ?: item.dealPhase),
                onClick = { onOpen(item.meetingId) },
                trailing = {
                    TextButton(onClick = { onUnmark(item.meetingId) }) {
                        Text("未フォローに戻す", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }
    }
}

@Composable
private fun FollowupCard(
    name: String,
    subtitle: String,
    phase: DealPhase?,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (leading != null) 4.dp else 16.dp,
                    top = 8.dp, bottom = 8.dp, end = 8.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (phase != null) {
                        Spacer(Modifier.width(6.dp))
                        DealPhaseChip(phase = phase)
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing()
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val monthDayFormatter = DateTimeFormatter.ofPattern("M/d")

private fun monthDay(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(monthDayFormatter)
