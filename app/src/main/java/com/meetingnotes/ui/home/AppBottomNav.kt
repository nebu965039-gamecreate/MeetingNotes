package com.meetingnotes.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meetingnotes.ui.common.PulsingHalo
import com.meetingnotes.ui.theme.CreateActionAmber

/**
 * 下部ナビ(4タブ + 中央の丸い録音ボタン)。`MainTabsShell` とクライアント詳細画面で共有する。
 * [currentTab] が null のときはどのタブも未選択(タブ画面以外で表示しているとき)。
 */
@Composable
fun AppBottomNav(
    currentTab: MainTab?,
    todoBadge: Int,
    onSelectTab: (MainTab) -> Unit,
    onStartRecording: () -> Unit
) {
    Box {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            NavTab(MainTab.SCHEDULE, currentTab, todoBadge, onSelectTab)
            NavTab(MainTab.CLIENTS, currentTab, todoBadge, onSelectTab)
            // 中央は録音ボタンぶんの空きスロット。
            Spacer(Modifier.weight(1f))
            NavTab(MainTab.TODO, currentTab, todoBadge, onSelectTab)
            NavTab(MainTab.ANALYTICS, currentTab, todoBadge, onSelectTab)
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            PulsingHalo(
                color = CreateActionAmber,
                maxScale = 1.28f,
                maxAlpha = 0.16f,
                durationMillis = 3400,
                staticRingAlpha = 0.28f
            ) {
                Surface(
                    onClick = onStartRecording,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = CreateActionAmber,
                    border = BorderStroke(2.dp, CreateActionAmber),
                    shadowElevation = 3.dp
                ) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Mic, contentDescription = "録音を始める")
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavTab(
    tab: MainTab,
    currentTab: MainTab?,
    badgeCount: Int,
    onSelect: (MainTab) -> Unit
) {
    val selected = currentTab == tab
    NavigationBarItem(
        selected = selected,
        onClick = { onSelect(tab) },
        icon = {
            val iconVector = if (selected) tab.selectedIcon else tab.icon
            if (tab == MainTab.TODO && badgeCount > 0) {
                BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
                    Icon(iconVector, contentDescription = null)
                }
            } else {
                Icon(iconVector, contentDescription = null)
            }
        },
        label = {
            Text(
                tab.label,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false
            )
        },
        colors = NavigationBarItemDefaults.colors(
            // 選択中は背景ピルではなくアイコン/文字の色(ネイビー)だけで示す。
            // indicatorColor はバー地と同じにして、ピル自体を見せない。
            indicatorColor = MaterialTheme.colorScheme.surface,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
