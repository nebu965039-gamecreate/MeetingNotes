package com.meetingnotes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** フォルダ型タブの1枚分。[count] は 0 なら非表示。 */
data class FolderTab(val label: String, val count: Int = 0)

/**
 * フォルダの見出しのように、選択中のタブが手前(白)・非選択が奥(色付き)に見えるタブ列。
 * 件数はラベル横の小さなピルで表示する(`PrimaryTabRow` の "(N)" 表記の置き換え)。
 * まず ToDo 画面で試している(CLAUDE.md 参照)。
 */
@Composable
fun FolderTabRow(
    tabs: List<FolderTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp)
            .height(44.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            Surface(
                color = if (selected) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = if (selected) 3.dp else 0.dp,
                shadowElevation = if (selected) 2.dp else 0.dp,
                shape = shape,
                modifier = Modifier
                    .weight(1f)
                    .height(if (selected) 44.dp else 36.dp)
                    .clip(shape)
                    .clickable { onSelect(index) }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (tab.count > 0) {
                            Spacer(Modifier.width(6.dp))
                            CountPill(tab.count, selected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountPill(count: Int, selected: Boolean) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold
        )
    }
}
