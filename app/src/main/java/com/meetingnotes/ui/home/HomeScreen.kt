package com.meetingnotes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.client.FollowupBoard
import com.meetingnotes.ui.client.UpcomingBoard
import com.meetingnotes.ui.theme.PhaseChartColors
import com.meetingnotes.ui.theme.ThemeMode
import com.meetingnotes.data.model.DealPhase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val headerDateFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: MeetingRepository,
    onOpenSchedule: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenFollowupList: () -> Unit,
    onOpenClient: (Long) -> Unit,
    onOpenMeeting: (Long) -> Unit,
    onRecoverDraft: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onHelp: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
    val followups by viewModel.followups.collectAsState()
    val upcoming by viewModel.upcoming.collectAsState()
    val phaseCounts by viewModel.phaseCounts.collectAsState()
    val dashboard by viewModel.dashboard.collectAsState()
    val hasUnseenNotifications by viewModel.hasUnseenNotifications.collectAsState()
    val dueTodos by viewModel.dueTodos.collectAsState()

    val context = LocalContext.current
    val app = context.applicationContext as MeetingNotesApp
    val draft by app.recordingDraftStore.draft.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = LocalDate.now().format(headerDateFormatter),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "こんにちは",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row {
                        IconButton(onClick = onOpenNotifications) {
                            if (hasUnseenNotifications) {
                                BadgedBox(badge = { Badge() }) {
                                    Icon(Icons.Filled.Notifications, contentDescription = "通知")
                                }
                            } else {
                                Icon(Icons.Filled.Notifications, contentDescription = "通知")
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "設定")
                        }
                        IconButton(onClick = onHelp) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "使い方・ヘルプ")
                        }
                    }
                }
            }

            item(key = "dashboard") {
                HomeDashboardCard(data = dashboard)
            }

            draft?.let { d ->
                item(key = "draft_recovery") {
                    DraftRecoveryCard(
                        endedAt = d.endedAt,
                        updatedAt = d.updatedAt,
                        onOpen = { onRecoverDraft(d.clientId) },
                        onDiscard = { app.recordingDraftStore.clear() }
                    )
                }
            }

            if (dueTodos.isNotEmpty()) {
                item(key = "due_todos") {
                    DueTodoBoard(
                        items = dueTodos,
                        onOpen = onOpenMeeting,
                        onComplete = { viewModel.completeTodo(it) }
                    )
                }
            }

            item(key = "upcoming_board") {
                UpcomingBoard(items = upcoming, onOpenClient = onOpenClient, onShowAll = onOpenSchedule)
            }

            item(key = "followup_board") {
                FollowupBoard(
                    items = followups,
                    onOpen = onOpenClient,
                    onShowAll = onOpenFollowupList
                )
            }

            item(key = "phase_tracker") {
                PhaseTrackerSection(counts = phaseCounts)
            }
        }
    }
}

/** 前回の録音〜要約が途中で終わっている場合にホーム先頭に出す復元カード。 */
@Composable
private fun DraftRecoveryCard(
    endedAt: Long,
    updatedAt: Long,
    onOpen: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val whenText = java.time.Instant.ofEpochMilli(if (updatedAt > 0) updatedAt else endedAt)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("M/d HH:mm"))
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("未完了の商談メモがあります", style = MaterialTheme.typography.titleSmall)
            Text(
                "$whenText の録音。文字起こしが保存されています。",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDiscard) { Text("破棄") }
                TextButton(onClick = onOpen) { Text("開いて続ける") }
            }
        }
    }
}

/** 「進行中のフェーズ」= 各フェーズの横棒(ファネル)。成約/保留/失注/初回接触は対象外。 */
@Composable
private fun PhaseTrackerSection(counts: PhaseTrackerCounts, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as MeetingNotesApp
    val darkTheme = when (app.themeModeState.value) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val rows = listOf(
        Triple("ヒアリング", counts.hearing, DealPhase.HEARING),
        Triple("提案", counts.proposal, DealPhase.PROPOSAL),
        Triple("見積提示", counts.quoted, DealPhase.QUOTED),
        Triple("検討中", counts.considering, DealPhase.CONSIDERING)
    )
    val maxCount = rows.maxOf { it.second }.coerceAtLeast(1)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("進行中のフェーズ", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${counts.total}件",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEach { (label, count, phase) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.width(60.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(count.toFloat() / maxCount)
                                    .height(14.dp)
                                    .background(
                                        PhaseChartColors.of(phase, darkTheme),
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$count",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
