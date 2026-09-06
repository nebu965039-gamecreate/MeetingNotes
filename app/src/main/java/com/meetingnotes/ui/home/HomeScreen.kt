package com.meetingnotes.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.client.FollowupBoard
import com.meetingnotes.ui.client.UpcomingBoard
import com.meetingnotes.ui.theme.PhaseTrackerColor
import com.meetingnotes.ui.theme.PhaseTrackerColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val headerDateFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: MeetingRepository,
    onOpenClientList: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenFollowupList: () -> Unit,
    onOpenClient: (Long) -> Unit,
    onRecoverDraft: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onHelp: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
    val followups by viewModel.followups.collectAsState()
    val upcoming by viewModel.upcoming.collectAsState()
    val phaseCounts by viewModel.phaseCounts.collectAsState()
    val hasUnseenNotifications by viewModel.hasUnseenNotifications.collectAsState()

    val context = LocalContext.current
    val app = context.applicationContext as MeetingNotesApp
    val draft by app.recordingDraftStore.draft.collectAsState()

    Scaffold(
        bottomBar = { BannerAdView(Modifier.navigationBarsPadding()) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
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
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "設定")
                        }
                        IconButton(onClick = onHelp) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "使い方・ヘルプ")
                        }
                    }
                }
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

            item(key = "action_tiles") {
                ActionTilesSection(
                    onStartRecording = onOpenClientList,
                    onOpenClientList = onOpenClientList,
                    onOpenSchedule = onOpenSchedule,
                    onOpenNotifications = onOpenNotifications,
                    hasNotificationBadge = hasUnseenNotifications
                )
            }

            item(key = "phase_tracker") {
                PhaseTrackerSection(counts = phaseCounts)
            }

            item(key = "followup_board") {
                FollowupBoard(items = followups, onOpen = onOpenClient, onShowAll = onOpenFollowupList)
            }

            item(key = "upcoming_board") {
                UpcomingBoard(items = upcoming, onOpenClient = onOpenClient, onShowAll = onOpenSchedule)
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

/** 録音開始/クライアント一覧/予定表/通知への導線タイル。それぞれ薄い枠で囲んでグループを明確にする。 */
@Composable
private fun ActionTilesSection(
    onStartRecording: () -> Unit,
    onOpenClientList: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenNotifications: () -> Unit,
    hasNotificationBadge: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile("録音を始める", Icons.Filled.Mic, Modifier.weight(1f), filled = true, onClick = onStartRecording)
                ActionTile("クライアント一覧", Icons.Filled.People, Modifier.weight(1f), onClick = onOpenClientList)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile("予定表", Icons.Filled.CalendarMonth, Modifier.weight(1f), onClick = onOpenSchedule)
                ActionTile(
                    "通知",
                    Icons.Filled.Notifications,
                    Modifier.weight(1f),
                    badge = hasNotificationBadge,
                    onClick = onOpenNotifications
                )
            }
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    badge: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val iconColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary

    Card(
        onClick = onClick,
        modifier = modifier.height(108.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        border = if (!filled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (badge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                )
            }
        }
    }
}

/** 「進行中のフェーズ」ステップ・トラッカー(成約/保留/失注/初回接触は対象外)。 */
@Composable
private fun PhaseTrackerSection(counts: PhaseTrackerCounts, modifier: Modifier = Modifier) {
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
            Box(Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 7.dp, start = 7.dp, end = 7.dp)
                )
                Row(Modifier.fillMaxWidth()) {
                    PhaseStep("ヒアリング", counts.hearing, PhaseTrackerColors.Hearing, Modifier.weight(1f))
                    PhaseStep("提案", counts.proposal, PhaseTrackerColors.Proposal, Modifier.weight(1f))
                    PhaseStep("見積提示", counts.quoted, PhaseTrackerColors.Quoted, Modifier.weight(1f))
                    PhaseStep("検討中", counts.considering, PhaseTrackerColors.Considering, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PhaseStep(label: String, count: Int, color: PhaseTrackerColor, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(15.dp)
                .background(color.dot, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .background(color.badgeBackground, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 1.dp)
        ) {
            Text(
                text = "${count}件",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color.badgeText
            )
        }
    }
}
