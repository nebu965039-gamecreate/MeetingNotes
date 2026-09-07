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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientLatestMeeting
import com.meetingnotes.data.model.DealPhase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** ToDo(要フォロー)の1行。 */
data class FollowupItem(
    val client: ClientEntity,
    val meetingId: Long,
    val lastRecordedAt: Long,
    val phase: DealPhase?
)

/**
 * 「メール連絡などの対応がまだの商談」を出すロジック(F1)。純粋関数で単体テスト可能。AI は使わない。
 *
 * 対象 = 各クライアントの最新商談で、以下をすべて満たすもの:
 *  - `followedUpAt` が未設定(「完了」を押したら二度と出ない)
 *  - 成約・失注ではない
 *  - 最終商談から [RECENT_WINDOW_DAYS] 日以内(古い商談は初回導入時に大量表示されないよう対象外)
 *
 * 次回予定の有無は問わない(打ち合わせが決まっていても、お礼メール等の連絡は別途必要なため)。
 */
object FollowupRules {

    /** 要約直後の商談を ToDo に出す対象期間(日)。これより古い商談は出さない。 */
    const val RECENT_WINDOW_DAYS = 30

    fun compute(
        clients: List<ClientEntity>,
        latest: List<ClientLatestMeeting>,
        now: Long = System.currentTimeMillis()
    ): List<FollowupItem> {
        val byClient = latest.associateBy { it.clientId }
        val cutoff = now - RECENT_WINDOW_DAYS * DAY_MS
        return clients.mapNotNull { client ->
            val m = byClient[client.id] ?: return@mapNotNull null
            if (m.followedUpAt != null) return@mapNotNull null
            if (m.lastRecordedAt < cutoff) return@mapNotNull null
            val phase = DealPhase.fromWire(m.phaseOverride ?: m.dealPhase)
            if (phase == DealPhase.WON || phase == DealPhase.LOST) return@mapNotNull null
            FollowupItem(client, m.meetingId, m.lastRecordedAt, phase)
        }.sortedByDescending { it.lastRecordedAt }
    }

    private const val DAY_MS = 86_400_000L
}

private val boardDateFormatter = DateTimeFormatter.ofPattern("M/d")

/** 内部スクロールで表示する最大件数。これを超える分は「すべて表示」で全件ページへ誘導する。 */
private const val MAX_VISIBLE = 10

/** ToDo 1行の説明文(ホーム・全件ページ共通)。 */
internal fun followupSubtitle(item: FollowupItem): String {
    val date = boardDateFormatter.format(
        Instant.ofEpochMilli(item.lastRecordedAt).atZone(ZoneId.systemDefault())
    )
    val phase = item.phase?.label ?: "フェーズ未設定"
    return "最終 $date・$phase・メール連絡"
}

/** ホーム画面の「ToDo」カード。0件でも表示する。デフォルトで3件ぶんの高さ、内部スクロールで最大10件確認できる。 */
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("ToDo", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${items.size}件",
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
                        .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        "対応が必要な商談はありません。",
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
                        TextButton(
                            onClick = { onMarkFollowedUp(item.meetingId) },
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text("完了", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (index != visible.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}
