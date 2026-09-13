package com.meetingnotes.ui.notifications

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.NotificationLogEntity
import com.meetingnotes.notifications.NotificationSeenState
import com.meetingnotes.ui.common.TabTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val firedAtFormat = DateTimeFormatter.ofPattern("M/d HH:mm")

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
    val history by viewModel.history.collectAsState()

    // この画面を開いたら通知は既読扱い(ホームの赤マークを消す)。
    LaunchedEffect(Unit) { NotificationSeenState.markSeen(context) }

    Scaffold(
        topBar = { TabTopBar(icon = Icons.Filled.Notifications, title = "通知", onHome = onBack) },
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
                    "通知履歴",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
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
