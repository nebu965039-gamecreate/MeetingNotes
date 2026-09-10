package com.meetingnotes.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.analytics.AnalyticsScreen
import com.meetingnotes.ui.client.ClientListScreen
import com.meetingnotes.ui.client.FollowupListScreen
import com.meetingnotes.ui.common.PulsingHalo
import com.meetingnotes.ui.schedule.ScheduleScreen

/** ホームは下部ナビから外し、各タブ画面の左上ホームアイコンから戻る。 */
private const val HOME_ROUTE = "tab_home"

/** 下部ナビの4タブ(ホームは含まない)。 */
enum class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    CLIENTS("tab_clients", "クライアント", Icons.Outlined.People, Icons.Filled.People),
    SCHEDULE("tab_schedule", "予定表", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    TODO("tab_todo", "ToDo", Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle),
    ANALYTICS("tab_analytics", "分析", Icons.Outlined.BarChart, Icons.Filled.BarChart)
}

/**
 * 下部ナビ + 録音FAB(ホームのみ)+ 共通バナー広告を持つメインシェル。4タブを内側の NavHost で保持する。
 * 詳細画面(クライアント詳細・商談詳細・録音など)へは外側の NavController へ委譲する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabsShell(
    repository: MeetingRepository,
    onOpenClient: (Long) -> Unit,
    onOpenMeeting: (Long) -> Unit,
    onRecoverDraft: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onHelp: () -> Unit,
    onOpenNotifications: () -> Unit,
    onStartRecording: () -> Unit,
    onOpenPipeline: () -> Unit
) {
    val tabNav = rememberNavController()
    val entry by tabNav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: HOME_ROUTE

    val openTodoTotal by remember { repository.observeOpenTodoTotal() }
        .collectAsState(initial = 0)

    fun switchTab(tab: MainTab) {
        tabNav.navigate(tab.route) {
            popUpTo(tabNav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun goHome() {
        tabNav.navigate(HOME_ROUTE) {
            popUpTo(tabNav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                BannerAdView()
                Box {
                    NavigationBar {
                        NavTab(MainTab.SCHEDULE, currentRoute, openTodoTotal) { switchTab(it) }
                        NavTab(MainTab.CLIENTS, currentRoute, openTodoTotal) { switchTab(it) }
                        // 中央は録音ボタンぶんの空きスロット。
                        Spacer(Modifier.weight(1f))
                        NavTab(MainTab.TODO, currentRoute, openTodoTotal) { switchTab(it) }
                        NavTab(MainTab.ANALYTICS, currentRoute, openTodoTotal) { switchTab(it) }
                    }
                    // ナビバー中央に丸い録音ボタンを重ねる(全タブで押せる)。
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PulsingHalo(
                            color = MaterialTheme.colorScheme.primary,
                            maxScale = 1.28f,
                            maxAlpha = 0.16f,
                            durationMillis = 3400,
                            staticRingAlpha = 0.28f
                        ) {
                            Surface(
                                onClick = onStartRecording,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
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
        }
    ) { padding ->
        NavHost(
            navController = tabNav,
            startDestination = HOME_ROUTE,
            modifier = Modifier.padding(padding)
        ) {
            composable(HOME_ROUTE) {
                HomeScreen(
                    repository = repository,
                    onOpenClient = onOpenClient,
                    onOpenMeeting = onOpenMeeting,
                    onRecoverDraft = onRecoverDraft,
                    onOpenSettings = onOpenSettings,
                    onHelp = onHelp,
                    onOpenNotifications = onOpenNotifications,
                    onOpenSchedule = { switchTab(MainTab.SCHEDULE) },
                    onOpenFollowupList = { switchTab(MainTab.TODO) },
                    onOpenSales = { switchTab(MainTab.ANALYTICS) },
                    onOpenPipeline = onOpenPipeline
                )
            }
            composable(MainTab.CLIENTS.route) {
                ClientListScreen(repository = repository, onClientSelected = onOpenClient, onHome = { goHome() })
            }
            composable(MainTab.SCHEDULE.route) {
                ScheduleScreen(
                    repository = repository,
                    onOpenClient = onOpenClient,
                    onOpenMeeting = onOpenMeeting,
                    onHome = { goHome() }
                )
            }
            composable(MainTab.TODO.route) {
                FollowupListScreen(
                    repository = repository,
                    onOpenClient = onOpenClient,
                    onOpenMeeting = onOpenMeeting,
                    onHome = { goHome() }
                )
            }
            composable(MainTab.ANALYTICS.route) {
                AnalyticsScreen(repository = repository, onHome = { goHome() })
            }
        }
    }
}

@Composable
private fun RowScope.NavTab(
    tab: MainTab,
    currentRoute: String,
    badgeCount: Int,
    onSelect: (MainTab) -> Unit
) {
    val selected = currentRoute == tab.route
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
        }
    )
}
