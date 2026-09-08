package com.meetingnotes.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.client.ClientListScreen
import com.meetingnotes.ui.client.FollowupListScreen
import com.meetingnotes.ui.schedule.ScheduleScreen
import com.meetingnotes.ui.theme.CreateActionBlue
import com.meetingnotes.ui.theme.OnCreateActionBlue

/** 下部ナビの4タブ。 */
enum class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME("tab_home", "ホーム", Icons.Outlined.Home, Icons.Filled.Home),
    CLIENTS("tab_clients", "クライアント", Icons.Outlined.People, Icons.Filled.People),
    SCHEDULE("tab_schedule", "予定表", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    TODO("tab_todo", "ToDo", Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle)
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
    onStartRecording: () -> Unit
) {
    val tabNav = rememberNavController()
    val entry by tabNav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: MainTab.HOME.route

    val openTodoTotal by remember { repository.observeOpenTodoTotal() }
        .collectAsState(initial = 0)

    fun switchTab(tab: MainTab) {
        tabNav.navigate(tab.route) {
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
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { switchTab(tab) },
                            icon = {
                                val iconVector = if (selected) tab.selectedIcon else tab.icon
                                if (tab == MainTab.TODO && openTodoTotal > 0) {
                                    BadgedBox(badge = { Badge { Text("$openTodoTotal") } }) {
                                        Icon(iconVector, contentDescription = null)
                                    }
                                } else {
                                    Icon(iconVector, contentDescription = null)
                                }
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == MainTab.HOME.route) {
                FloatingActionButton(
                    onClick = onStartRecording,
                    containerColor = CreateActionBlue,
                    contentColor = OnCreateActionBlue
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "録音を始める")
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = tabNav,
            startDestination = MainTab.HOME.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(MainTab.HOME.route) {
                HomeScreen(
                    repository = repository,
                    onOpenClient = onOpenClient,
                    onOpenMeeting = onOpenMeeting,
                    onRecoverDraft = onRecoverDraft,
                    onOpenSettings = onOpenSettings,
                    onHelp = onHelp,
                    onOpenNotifications = onOpenNotifications,
                    onOpenSchedule = { switchTab(MainTab.SCHEDULE) },
                    onOpenFollowupList = { switchTab(MainTab.TODO) }
                )
            }
            composable(MainTab.CLIENTS.route) {
                ClientListScreen(repository = repository, onClientSelected = onOpenClient)
            }
            composable(MainTab.SCHEDULE.route) {
                ScheduleScreen(repository = repository, onOpenClient = onOpenClient)
            }
            composable(MainTab.TODO.route) {
                FollowupListScreen(repository = repository, onOpenClient = onOpenClient)
            }
        }
    }
}
