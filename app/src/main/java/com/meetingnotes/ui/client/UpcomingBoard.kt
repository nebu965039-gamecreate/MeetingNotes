package com.meetingnotes.ui.client

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientLatestMeeting
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.data.model.NextMeetingTime
import com.meetingnotes.ui.common.DealPhaseChip
import com.meetingnotes.ui.common.relativeDateTimeLabel
import java.time.LocalDate
import java.time.LocalDateTime

/** 予定カレンダー(F7)の1行。 */
data class UpcomingItem(
    val client: ClientEntity,
    val meetingId: Long,
    val start: LocalDateTime,
    val allDay: Boolean,
    val phase: DealPhase?
)

/** 未来の「次回打ち合わせ」を日付順に拾うロジック。純粋関数で単体テスト可能。 */
object UpcomingRules {

    fun compute(
        clients: List<ClientEntity>,
        latest: List<ClientLatestMeeting>,
        today: LocalDate = LocalDate.now()
    ): List<UpcomingItem> {
        val byClient = latest.associateBy { it.clientId }
        return clients.mapNotNull { client ->
            val m = byClient[client.id] ?: return@mapNotNull null
            val parsed = NextMeetingTime.parse(m.nextMeetingDate) ?: return@mapNotNull null
            if (parsed.date.isBefore(today)) return@mapNotNull null
            UpcomingItem(
                client = client,
                meetingId = m.meetingId,
                start = parsed.start,
                allDay = parsed.allDay,
                phase = DealPhase.fromWire(m.phaseOverride ?: m.dealPhase)
            )
        }.sortedBy { it.start }
    }
}

private const val PREVIEW_COUNT = 3

/** ホーム画面の「次回の予定」カード。0件でも表示する。 */
@Composable
fun UpcomingBoard(
    items: List<UpcomingItem>,
    onOpenMeeting: (Long) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.EventAvailable,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("次回の予定", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "全${items.size}件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "すべて表示",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onShowAll)
                )
            }
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        "次回打ち合わせの予定はありません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp)
                ) {
                    val preview = items.take(PREVIEW_COUNT)
                    preview.forEachIndexed { index, item ->
                        UpcomingRow(item = item, onClick = { onOpenMeeting(item.meetingId) })
                        if (index != preview.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(item: UpcomingItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                relativeDateTimeLabel(item.start, item.allDay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                item.client.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.phase != null) {
                DealPhaseChip(phase = item.phase)
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
