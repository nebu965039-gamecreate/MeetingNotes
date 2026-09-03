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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** フォローボードの1行。 */
data class FollowupItem(
    val client: ClientEntity,
    val lastRecordedAt: Long,
    val daysSince: Int,
    val phase: DealPhase?
)

/**
 * 「次アクション未定のまま放置されている案件」を判定するロジック(F1)。
 * 純粋関数で単体テスト可能にしている。AI は使わない。
 */
object FollowupRules {

    /** 最終商談からこの日数以上経過し、次回予定が無ければ「要フォロー」。 */
    const val THRESHOLD_DAYS = 14

    fun compute(
        clients: List<ClientEntity>,
        latest: List<ClientLatestMeeting>,
        now: Long = System.currentTimeMillis()
    ): List<FollowupItem> {
        val byClient = latest.associateBy { it.clientId }
        return clients.mapNotNull { client ->
            val m = byClient[client.id] ?: return@mapNotNull null
            val phase = DealPhase.fromWire(m.phaseOverride ?: m.dealPhase)
            if (phase == DealPhase.WON || phase == DealPhase.LOST) return@mapNotNull null
            if (hasUpcomingMeeting(m.nextMeetingDate, now)) return@mapNotNull null
            val days = ((now - m.lastRecordedAt) / DAY_MS).toInt()
            if (days < THRESHOLD_DAYS) return@mapNotNull null
            FollowupItem(client, m.lastRecordedAt, days, phase)
        }.sortedByDescending { it.daysSince }
    }

    /** `nextMeetingDate` が「今日以降の ISO 日付」なら予定あり=フォロー不要。 */
    private fun hasUpcomingMeeting(nextMeetingDate: String?, now: Long): Boolean {
        val match = ISO_DATE.find(nextMeetingDate?.trim().orEmpty()) ?: return false
        return try {
            val date = LocalDate.of(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt()
            )
            val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
            !date.isBefore(today)
        } catch (e: RuntimeException) {
            false
        }
    }

    private const val DAY_MS = 86_400_000L
    private val ISO_DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})""")
}

private val boardDateFormatter = DateTimeFormatter.ofPattern("M/d")

/** クライアント一覧の先頭に置く「要フォロー」カード。空なら何も描かない。 */
@Composable
fun FollowupBoard(
    items: List<FollowupItem>,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "要フォロー (${items.size})",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            items.take(5).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(item.client.id) }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.client.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "最終 ${formatDate(item.lastRecordedAt)}・${item.phase?.label ?: "次回未定"}・${item.daysSince}日",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (items.size > 5) {
                Text(
                    "ほか ${items.size - 5} 件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(boardDateFormatter)
