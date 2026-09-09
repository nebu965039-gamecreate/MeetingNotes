package com.meetingnotes.ui.notifications

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.NotificationLogEntity
import com.meetingnotes.notifications.NotificationSeenState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val firedAtFormat = DateTimeFormatter.ofPattern("M/d HH:mm")
private val upcomingFormat = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    repository: MeetingRepository,
    onBack: () -> Unit,
    onOpenMeeting: (Long) -> Unit,
    onOpenClient: (Long) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: NotificationViewModel = viewModel(
        factory = NotificationViewModel.factory(application, repository)
    )
    val upcoming by viewModel.upcoming.collectAsState()
    val history by viewModel.history.collectAsState()

    // この画面を開いたら通知は既読扱い(ホームの赤マークを消す)。
    LaunchedEffect(Unit) { NotificationSeenState.markSeen(context) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "通知",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = { BannerAdView(Modifier.navigationBarsPadding()) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "予定されているリマインド",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (upcoming.isEmpty()) {
                item {
                    Text(
                        "次回打ち合わせが設定されている商談はありません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(upcoming, key = { "up_${it.scheduleId}" }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenClient(item.clientId) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                item.start.toLocalDate().format(upcomingFormat) +
                                    if (!item.allDay) " %02d:%02d".format(
                                        item.start.hour, item.start.minute
                                    ) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${item.clientName} ・ ${item.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "通知履歴",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            if (history.isEmpty()) {
                item {
                    Text(
                        "まだ通知はありません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(history, key = { it.id }) { log ->
                    HistoryRow(
                        log = log,
                        onClick = {
                            if (log.meetingId > 0) onOpenMeeting(log.meetingId)
                            else onOpenClient(log.clientId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(log: NotificationLogEntity, onClick: () -> Unit) {
    val firedAt = Instant.ofEpochMilli(log.firedAt).atZone(ZoneId.systemDefault()).format(firedAtFormat)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(firedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(log.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(log.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
