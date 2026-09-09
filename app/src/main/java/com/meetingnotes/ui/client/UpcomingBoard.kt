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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meetingnotes.data.local.ScheduleWithClient
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.data.model.NextMeetingTime
import com.meetingnotes.ui.common.DealPhaseChip
import com.meetingnotes.ui.common.relativeDateTimeLabel
import java.time.LocalDate
import java.time.LocalDateTime

/** 予定(schedules)の1行。 */
data class UpcomingItem(
    val scheduleId: Long,
    val clientId: Long,
    val clientName: String,
    val start: LocalDateTime,
    val allDay: Boolean,
    val title: String,
    val note: String,
    val participants: String,
    val phase: DealPhase?,
    /** AI が要約から拾った「次回打ち合わせ」由来か(手動追加でない)。 */
    val fromAi: Boolean
)

fun ScheduleWithClient.toUpcomingItem(): UpcomingItem = UpcomingItem(
    scheduleId = id,
    clientId = clientId,
    clientName = clientName,
    start = NextMeetingTime.toLocalDateTime(startAtMillis),
    allDay = !hasTime,
    title = title,
    note = note,
    participants = participants,
    phase = DealPhase.fromWire(phase),
    fromAi = sourceMeetingId != null
)

/** 予定を日付順に並べるロジック。 */
object UpcomingRules {

    /** 今日以降(または [includePast])の予定を開始日時順に。 */
    fun order(
        schedules: List<ScheduleWithClient>,
        today: LocalDate = LocalDate.now(),
        includePast: Boolean = false
    ): List<UpcomingItem> =
        schedules.map { it.toUpcomingItem() }
            .filter { includePast || !it.start.toLocalDate().isBefore(today) }
            .sortedBy { it.start }
}

private const val PREVIEW_COUNT = 3

/** ホーム画面の「直近の予定」カード。0件でも表示する。行タップでクライアント画面へ。 */
@Composable
fun UpcomingBoard(
    items: List<UpcomingItem>,
    onOpenClient: (Long) -> Unit,
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
                    Text("直近の予定", style = MaterialTheme.typography.titleMedium)
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
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(onClick = onShowAll)
                )
            }
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
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
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp)
                ) {
                    val preview = items.take(PREVIEW_COUNT)
                    preview.forEachIndexed { index, item ->
                        UpcomingRow(item = item, onClick = { onOpenClient(item.clientId) })
                        if (index != preview.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                relativeDateTimeLabel(item.start, item.allDay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                item.clientName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.title.isNotBlank() && item.title != "打ち合わせ" && item.title != "次回打ち合わせ") {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
