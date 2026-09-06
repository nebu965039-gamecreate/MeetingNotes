package com.meetingnotes.ui.client

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/** 要フォローに出る理由。 */
enum class FollowupReason {
    /** 要約は完了したが、まだメールでのフォローアップをしていない。 */
    NEEDS_EMAIL,

    /** メールフォロー済み(または対象外)だが、次回予定が無いまま日数が経っている。 */
    STALE
}

/** フォローボードの1行。 */
data class FollowupItem(
    val client: ClientEntity,
    val meetingId: Long,
    val lastRecordedAt: Long,
    val daysSince: Int,
    val phase: DealPhase?,
    val reason: FollowupReason
)

/**
 * 「次の一手が必要な案件」を判定するロジック(F1)。純粋関数で単体テスト可能にしている。AI は使わない。
 *
 * 各クライアントの最新商談を見て、成約/失注でも今後の予定も無いものが対象:
 *  - `followedUpAt` が未設定 → [FollowupReason.NEEDS_EMAIL](要約完了直後から、メールフォローするまで出続ける)
 *  - フォロー済みでも最終商談から [THRESHOLD_DAYS] 日以上経過 → [FollowupReason.STALE]
 */
object FollowupRules {

    /** メールフォロー後、次回予定が無いまま「放置」とみなすまでの日数。 */
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
            val reason = when {
                m.followedUpAt == null -> FollowupReason.NEEDS_EMAIL
                days >= THRESHOLD_DAYS -> FollowupReason.STALE
                else -> return@mapNotNull null
            }
            FollowupItem(client, m.meetingId, m.lastRecordedAt, days, phase, reason)
        }.sortedWith(
            // 未フォロー(要約直後)を上に、その中では新しい商談から。放置は日数の多い順。
            compareBy<FollowupItem> { it.reason != FollowupReason.NEEDS_EMAIL }
                .thenByDescending {
                    if (it.reason == FollowupReason.NEEDS_EMAIL) it.lastRecordedAt else it.daysSince.toLong()
                }
        )
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

/** 内部スクロールで表示する最大件数。これを超える分は「すべて表示」で全件ページへ誘導する。 */
private const val MAX_VISIBLE = 10

/** 要フォロー1行の説明文(ホーム・全件ページ共通)。 */
internal fun followupSubtitle(item: FollowupItem): String {
    val date = boardDateFormatter.format(
        Instant.ofEpochMilli(item.lastRecordedAt).atZone(ZoneId.systemDefault())
    )
    val phase = item.phase?.label ?: "フェーズ未設定"
    return when (item.reason) {
        FollowupReason.NEEDS_EMAIL -> "最終 $date・$phase・メールでフォロー"
        FollowupReason.STALE -> "最終 $date・$phase・${item.daysSince}日経過"
    }
}

/** ホーム画面の「要フォロー」カード。0件でも表示する。デフォルトで3件ぶんの高さ、内部スクロールで最大10件確認できる。 */
@Composable
fun FollowupBoard(
    items: List<FollowupItem>,
    onOpen: (meetingId: Long) -> Unit,
    onMarkFollowedUp: (meetingId: Long) -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("要フォロー", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${items.size}件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        "フォローが必要な商談はありません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(16.dp))
                    .heightIn(max = 152.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
            ) {
                val visible = items.take(MAX_VISIBLE)
                val hasMore = items.size > MAX_VISIBLE
                visible.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item.meetingId) }
                            .padding(vertical = 9.dp),
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
                                followupSubtitle(item),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (item.reason == FollowupReason.NEEDS_EMAIL) {
                            TextButton(
                                onClick = { onMarkFollowedUp(item.meetingId) },
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("フォロー済み", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (index != visible.lastIndex || hasMore) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.08f))
                    }
                }
                if (hasMore) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onShowAll)
                            .padding(vertical = 11.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "すべて表示",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
