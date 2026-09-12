package com.meetingnotes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meetingnotes.ui.theme.CreateActionAmber
import com.meetingnotes.ui.theme.OnBrandNavyDim

/**
 * ホームの各ボード(「動いていない案件」「期限切れ・3日以内のToDo」「直近の予定」等)で使う見出し行。
 * アイコンの代わりにネイビーの縦バー([MaterialTheme.colorScheme.primary]、ライト=ネイビー/ダーク=明るい青)
 * + タイトル + 件数。右端に任意で `trailing`(「すべて表示」等のリンク)を置ける。
 */
@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier
                    .size(width = 3.dp, height = 15.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (count != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * ネイビー背景のダッシュボードカード([DueTodoBoard]/[StaleDealBoard]/[UpcomingBoard] 等、
 * `BrandNavy` の `Card`)専用の見出し(2026-09-18)。[SectionHeading] の縦バーは
 * `primary`(ネイビー)色のためネイビーカード上では見えなくなるので、代わりに
 * `CreateActionAmber`(下の「すべて表示」等のリンクと同色)のバーを使う。
 */
@Composable
fun NavyCardHeading(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .background(CreateActionAmber, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (count != null) {
                Spacer(Modifier.width(6.dp))
                Text(count, style = MaterialTheme.typography.bodySmall, color = OnBrandNavyDim)
            }
        }
        trailing?.invoke()
    }
}
