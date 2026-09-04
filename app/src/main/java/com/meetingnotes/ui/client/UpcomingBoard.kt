package com.meetingnotes.ui.client

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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

private val agendaDate = DateTimeFormatter.ofPattern("M/d")

/** クライアント一覧の「近日の予定」カード。空なら何も描かない。 */
@Composable
fun UpcomingBoard(
    items: List<UpcomingItem>,
    onOpenMeeting: (Long) -> Unit,
    onAddToCalendar: (UpcomingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.EventAvailable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("近日の予定 (${items.size})", style = MaterialTheme.typography.titleSmall)
            }
            items.take(6).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMeeting(item.meetingId) }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${item.start.toLocalDate().format(agendaDate)} " +
                                "(${item.start.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPAN)})" +
                                if (!item.allDay) " %02d:%02d".format(item.start.hour, item.start.minute) else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            buildString {
                                append(item.client.name)
                                item.phase?.let { append("・").append(it.label) }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onAddToCalendar(item) }) {
                        Icon(
                            Icons.Filled.Event,
                            contentDescription = "カレンダーに追加",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (items.size > 6) {
                Text(
                    "ほか ${items.size - 6} 件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
