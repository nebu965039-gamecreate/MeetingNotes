package com.meetingnotes.ui.client

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
    val phase: DealPhase?,
    /** このクライアントの未完了 ToDo 件数(0 なら未表示)。 */
    val openTodoCount: Int = 0,
    /** スヌーズ中の場合の再表示日時(epoch millis)。通常の ToDo 行は null。 */
    val snoozedUntil: Long? = null
)

/**
 * ホーム/一覧の「ToDo」ボードに出すクライアントを決めるロジック(F1)。純粋関数で単体テスト可能。AI は使わない。
 *
 * 対象 = **未完了の ToDo が1件以上あるクライアント**。
 * ToDo には要約が抽出したタスクに加え、要約完了時に自動起票される「お礼・フォローアップのメールを送る」も含む。
 * クライアント画面で ToDo をすべてチェックし終えるとボードから消える(「完了」ボタンは廃止)。
 * 最新商談はフェーズ・日付の表示にのみ使う(判定には使わない)。
 */
object FollowupRules {

    private fun buildItem(
        client: ClientEntity,
        latestByClient: Map<Long, ClientLatestMeeting>,
        count: Int,
        snoozedUntil: Long?
    ): FollowupItem {
        val m = latestByClient[client.id]
        return FollowupItem(
            client = client,
            meetingId = m?.meetingId ?: 0L,
            lastRecordedAt = m?.lastRecordedAt ?: client.createdAt,
            phase = m?.let { DealPhase.fromWire(it.phaseOverride ?: it.dealPhase) },
            openTodoCount = count,
            snoozedUntil = snoozedUntil
        )
    }

    /** ToDo ボードに出すクライアント。スヌーズ期限が未来のクライアントは除外する。 */
    fun compute(
        clients: List<ClientEntity>,
        latest: List<ClientLatestMeeting>,
        openTodoCountByClient: Map<Long, Int> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<FollowupItem> {
        val byClient = latest.associateBy { it.clientId }
        return clients.mapNotNull { client ->
            val count = openTodoCountByClient[client.id] ?: 0
            if (count == 0) return@mapNotNull null
            val snooze = client.followBoardSnoozedUntil
            if (snooze != null && snooze > nowMillis) return@mapNotNull null
            buildItem(client, byClient, count, snoozedUntil = null)
        }.sortedByDescending { it.lastRecordedAt }
    }

    /** 現在スヌーズ中(未完了 ToDo あり かつ 再表示日が未来)のクライアント。再表示が近い順。 */
    fun computeSnoozed(
        clients: List<ClientEntity>,
        latest: List<ClientLatestMeeting>,
        openTodoCountByClient: Map<Long, Int> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<FollowupItem> {
        val byClient = latest.associateBy { it.clientId }
        return clients.mapNotNull { client ->
            val snooze = client.followBoardSnoozedUntil ?: return@mapNotNull null
            if (snooze <= nowMillis) return@mapNotNull null
            val count = openTodoCountByClient[client.id] ?: 0
            if (count == 0) return@mapNotNull null
            buildItem(client, byClient, count, snoozedUntil = snooze)
        }.sortedBy { it.snoozedUntil }
    }
}

private val boardDateFormatter = DateTimeFormatter.ofPattern("M/d")

/** 内部スクロールで表示する最大件数。これを超える分は「すべて表示」で全件ページへ誘導する。 */
private const val MAX_VISIBLE = 10

/** クライアント名の右に出す「未完了ToDo N件」バッジ。 */
@Composable
internal fun TodoCountBadge(count: Int) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Text(
            "ToDo $count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

/** ToDo 1行の説明文(ホーム・全件ページ共通)。 */
internal fun followupSubtitle(item: FollowupItem): String {
    val date = boardDateFormatter.format(
        Instant.ofEpochMilli(item.lastRecordedAt).atZone(ZoneId.systemDefault())
    )
    val phase = item.phase?.label ?: "フェーズ未設定"
    return "最終 $date・$phase"
}

/** ホーム画面の「ToDo」カード。0件でも表示する。デフォルトで3件ぶんの高さ、内部スクロールで最大10件確認できる。 */
@Composable
fun FollowupBoard(
    items: List<FollowupItem>,
    onOpen: (clientId: Long) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalTodos = items.sumOf { it.openTodoCount }
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
                        "${totalTodos}件",
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
                        "未完了のToDoはありません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                    .heightIn(max = 152.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
            ) {
                val visible = items.take(MAX_VISIBLE)
                visible.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item.client.id) }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.client.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (item.openTodoCount > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    TodoCountBadge(item.openTodoCount)
                                }
                            }
                            Text(
                                followupSubtitle(item),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (index != visible.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
