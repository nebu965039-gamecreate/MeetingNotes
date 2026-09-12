package com.meetingnotes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meetingnotes.ui.theme.BrandNavy
import com.meetingnotes.ui.theme.CreateActionAmber
import com.meetingnotes.ui.theme.OnBrandNavy

/**
 * ホーム「期限切れ・3日以内のToDo」カード(2026-09-14、ダッシュボード化)。
 * 個々の ToDo は表示せず、`HomeDashboardCard`(進行中フェーズの進捗カード)と同じ
 * デザイン(`BrandNavy` の Card + `StatCell`)で件数だけを見せ、「ToDoを確認する ›」から ToDo タブへ。
 * **旧**: 期限切れ/3日以内それぞれの ToDo を最大5件・区切り線付きで一覧表示していた
 * (`DueTodoSubsection`)。詳細は ToDo タブ側で確認する運用に変更したため削除。
 */
@Composable
fun DueTodoBoard(
    overdueCount: Int,
    dueSoonCount: Int,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BrandNavy, contentColor = OnBrandNavy)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 「件数だけだと何の件数か分かりづらい」というフィードバックを受けた見出し(2026-09-15)。
            // SectionHeading の縦バーは primary(ネイビー)色でこの navy カードでは見えないため、
            // 代わりにアンバー(下のリンクと同色)のバーで代用する。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(16.dp)
                        .background(CreateActionAmber, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "期限切れ・3日以内のToDo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCell(
                    "期限切れ",
                    overdueCount.toString(),
                    Modifier.weight(1f),
                    valueColor = if (overdueCount > 0) MaterialTheme.colorScheme.error else null
                )
                StatCell("3日以内", dueSoonCount.toString(), Modifier.weight(1f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowAll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ToDoを確認する",
                    style = MaterialTheme.typography.labelMedium,
                    color = CreateActionAmber
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = CreateActionAmber,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
