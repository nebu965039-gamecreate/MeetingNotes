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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.common.TabTopBar
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
    onOpenClient: (Long) -> Unit,
    onHome: () -> Unit = {}
) {
    val viewModel: FollowupListViewModel = viewModel(factory = FollowupListViewModel.factory(repository))
    val todo by viewModel.followups.collectAsState()
    val snoozed by viewModel.snoozed.collectAsState()
    val done by viewModel.followedUp.collectAsState()

    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TabTopBar(
                icon = Icons.Filled.CheckCircle,
                title = "ToDo",
                onHome = onHome
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 下部ナビの ToDo バッジ(= 未完了 ToDo 総数)と件数を揃える。
            val openTodoTotal = todo.sumOf { it.openTodoCount }
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("ToDo ($openTodoTotal)") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("スヌーズ (${snoozed.size})") }
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    text = { Text("完了 (${done.size})") }
                )
            }

            when (tab) {
                0 -> TodoList(
                    items = todo,
                    onOpen = onOpenClient,
                    onSnooze = { clientId, days -> viewModel.snooze(clientId, days) },
                    onSnoozeUntil = { clientId, millis -> viewModel.snoozeUntil(clientId, millis) }
                )
                1 -> SnoozedList(
                    items = snoozed,
                    onOpen = onOpenClient,
                    onUnsnooze = { viewModel.unsnooze(it) }
                )
                else -> DoneList(
                    items = done,
                    onOpen = onOpenClient,
                    onReopen = { viewModel.unmarkFollowedUp(it) }
                )
            }
        }
    }
}

@Composable
private fun TodoList(
    items: List<FollowupItem>,
    onOpen: (Long) -> Unit,
    onSnooze: (clientId: Long, days: Long) -> Unit,
    onSnoozeUntil: (clientId: Long, millis: Long) -> Unit
) {
    if (items.isEmpty()) {
        EmptyMessage("現在ToDoはありません")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.client.id }) { item ->
            FollowupCard(
                name = item.client.name,
                subtitle = followupSubtitle(item),
                phase = item.phase,
                todoCount = item.openTodoCount,
                onClick = { onOpen(item.client.id) },
                trailing = {
                    SnoozeMenu(
                        onSnooze = { days -> onSnooze(item.client.id, days) },
                        onSnoozeUntil = { millis -> onSnoozeUntil(item.client.id, millis) }
                    )
                }
            )
        }
    }
}

@Composable
private fun SnoozeMenu(onSnooze: (days: Long) -> Unit, onSnoozeUntil: (millis: Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Snooze, contentDescription = "スヌーズ")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf("1日後" to 1L, "3日後" to 3L, "1週間後" to 7L, "1ヶ月後" to 30L).forEach { (label, days) ->
                DropdownMenuItem(
                    text = { Text("$label に再表示") },
                    onClick = { onSnooze(days); open = false }
                )
            }
            DropdownMenuItem(
                text = { Text("日付を指定…") },
                onClick = { open = false; showPicker = true }
            )
        }
    }
    if (showPicker) {
        com.meetingnotes.ui.common.NextMeetingDateTimeDialog(
            initial = java.time.LocalDate.now().plusWeeks(1).atStartOfDay(),
            initialHasTime = false,
            onDismiss = { showPicker = false },
            onConfirm = { dt, _ ->
                onSnoozeUntil(
                    dt.toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                )
                showPicker = false
            }
        )
    }
}

@Composable
private fun SnoozedList(
    items: List<FollowupItem>,
    onOpen: (Long) -> Unit,
    onUnsnooze: (Long) -> Unit
) {
    if (items.isEmpty()) {
        EmptyMessage("スヌーズ中のクライアントはありません")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.client.id }) { item ->
            FollowupCard(
                name = item.client.name,
                subtitle = "${monthDay(item.snoozedUntil ?: 0L)} に再表示・ToDo ${item.openTodoCount}件",
                phase = item.phase,
                onClick = { onOpen(item.client.id) },
                trailing = {
                    TextButton(onClick = { onUnsnooze(item.client.id) }) {
                        Text("解除", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }
    }
}

@Composable
private fun DoneList(
    items: List<FollowedUpMeeting>,
    onOpen: (Long) -> Unit,
    onReopen: (Long) -> Unit
) {
    if (items.isEmpty()) {
        EmptyMessage("完了した項目はまだありません")
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
                subtitle = "完了 ${monthDay(item.followedUpAt)}・${item.title}",
                phase = DealPhase.fromWire(item.phaseOverride ?: item.dealPhase),
                onClick = { onOpen(item.clientId) },
                trailing = {
                    TextButton(onClick = { onReopen(item.meetingId) }) {
                        Text("ToDoに戻す", style = MaterialTheme.typography.labelLarge)
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
    todoCount: Int = 0
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (todoCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        TodoCountBadge(todoCount)
                    }
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
