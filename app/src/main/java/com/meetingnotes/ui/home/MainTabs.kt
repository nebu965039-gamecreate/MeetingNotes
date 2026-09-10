package com.meetingnotes.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.SavedStateHandle
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
    /** 子画面(クライアント詳細など)から「このタブへ切り替えて」を受け取るための橋渡し。 */
    savedStateHandle: SavedStateHandle? = null
) {
    val tabNav = rememberNavController()
    val entry by tabNav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: HOME_ROUTE
    val currentTab = MainTab.entries.find { it.route == currentRoute }

    val openTodoTotal by remember { repository.observeOpenTodoTotal() }
        .collectAsState(initial = 0)

    fun switchTab(tab: MainTab) {
        tabNav.navigate(tab.route) {
            popUpTo(tabNav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 子画面(外側 NavHost)から MAIN に戻ってきたときの「保留中のタブ切り替え」を処理する。
    if (savedStateHandle != null) {
        val pendingTab by savedStateHandle.getStateFlow<String?>("pendingTab", null).collectAsState()
        LaunchedEffect(pendingTab) {
            pendingTab?.let { name ->
                MainTab.entries.find { it.name == name }?.let { switchTab(it) }
                savedStateHandle["pendingTab"] = null
            }
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
                AppBottomNav(
                    currentTab = currentTab,
                    todoBadge = openTodoTotal,
                    onSelectTab = { switchTab(it) },
                    onStartRecording = onStartRecording
                )
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
                    onOpenSales = { switchTab(MainTab.ANALYTICS) }
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
