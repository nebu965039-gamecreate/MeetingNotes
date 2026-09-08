package com.meetingnotes.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.briefing.BriefingScreen
import com.meetingnotes.ui.client.ClientDetailScreen
import com.meetingnotes.ui.client.ClientEditScreen
import com.meetingnotes.ui.client.ClientInfoScreen
import com.meetingnotes.ui.client.ClientListScreen
import com.meetingnotes.ui.client.FollowupListScreen
import com.meetingnotes.ui.help.HelpScreen
import com.meetingnotes.ui.home.HomeScreen
import com.meetingnotes.ui.meeting.MeetingDetailScreen
import com.meetingnotes.ui.notifications.NotificationScreen
import com.meetingnotes.ui.recording.RecordingScreen
import com.meetingnotes.ui.result.ResultScreen
import com.meetingnotes.ui.schedule.ScheduleScreen
import com.meetingnotes.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val CLIENT_LIST = "clientList"
    const val CLIENT_DETAIL = "clientDetail/{clientId}"
    const val BRIEFING = "briefing/{clientId}"
    const val RECORDING = "recording/{clientId}"
    const val RECORDING_UNASSIGNED = "recordingUnassigned"
    const val RESULT = "result"
    const val MEETING_DETAIL = "meetingDetail/{meetingId}"
    const val HELP = "help"
    const val NOTIFICATIONS = "notifications"
    const val SCHEDULE = "schedule"
    const val FOLLOWUP_LIST = "followupList"
    const val SETTINGS = "settings"
    const val CLIENT_INFO = "clientInfo/{clientId}"
    const val CLIENT_EDIT = "clientEdit/{clientId}"

    fun clientDetail(clientId: Long) = "clientDetail/$clientId"
    fun briefing(clientId: Long) = "briefing/$clientId"
    fun recording(clientId: Long) = "recording/$clientId"
    fun meetingDetail(meetingId: Long) = "meetingDetail/$meetingId"
    fun clientInfo(clientId: Long) = "clientInfo/$clientId"
    fun clientEdit(clientId: Long) = "clientEdit/$clientId"
}

@Composable
fun MeetingNotesNavHost(
    repository: MeetingRepository,
    navController: NavHostController = rememberNavController()
) {
    val meetingViewModel: MeetingViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                repository = repository,
                onStartRecording = {
                    meetingViewModel.resetForNewMeeting()
                    navController.navigate(Routes.RECORDING_UNASSIGNED)
                },
                onOpenClientList = { navController.navigate(Routes.CLIENT_LIST) },
                onOpenSchedule = { navController.navigate(Routes.SCHEDULE) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenFollowupList = { navController.navigate(Routes.FOLLOWUP_LIST) },
                onOpenClient = { clientId -> navController.navigate(Routes.clientDetail(clientId)) },
                onOpenMeeting = { meetingId -> navController.navigate(Routes.meetingDetail(meetingId)) },
                onRecoverDraft = { clientId ->
                    meetingViewModel.resetForNewMeeting()
                    if (clientId < 0) {
                        navController.navigate(Routes.RECORDING_UNASSIGNED)
                    } else {
                        navController.navigate(Routes.recording(clientId))
                    }
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onHelp = { navController.navigate(Routes.HELP) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CLIENT_LIST) {
            ClientListScreen(
                repository = repository,
                onClientSelected = { clientId ->
                    navController.navigate(Routes.clientDetail(clientId))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SCHEDULE) {
            ScheduleScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenClient = { clientId -> navController.navigate(Routes.clientDetail(clientId)) }
            )
        }
        composable(Routes.FOLLOWUP_LIST) {
            FollowupListScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenMeeting = { meetingId -> navController.navigate(Routes.meetingDetail(meetingId)) }
            )
        }
        composable(Routes.HELP) {
            HelpScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenMeeting = { meetingId ->
                    navController.navigate(Routes.meetingDetail(meetingId))
                }
            )
        }
        composable(
            Routes.CLIENT_DETAIL,
            arguments = listOf(navArgument("clientId") { type = NavType.LongType })
        ) { backStackEntry ->
            val clientId = backStackEntry.arguments?.getLong("clientId") ?: return@composable
            ClientDetailScreen(
                repository = repository,
                clientId = clientId,
                onStartRecording = { id ->
                    meetingViewModel.resetForNewMeeting()
                    navController.navigate(Routes.recording(id))
                },
                onShowBriefing = { id -> navController.navigate(Routes.briefing(id)) },
                onMeetingSelected = { meetingId ->
                    navController.navigate(Routes.meetingDetail(meetingId))
                },
                onOpenClientInfo = { navController.navigate(Routes.clientInfo(clientId)) },
                onBack = { navController.popBackStack() },
                onClientDeleted = { navController.popBackStack(Routes.CLIENT_LIST, inclusive = false) }
            )
        }
        composable(
            Routes.CLIENT_INFO,
            arguments = listOf(navArgument("clientId") { type = NavType.LongType })
        ) { backStackEntry ->
            val clientId = backStackEntry.arguments?.getLong("clientId") ?: return@composable
            ClientInfoScreen(
                repository = repository,
                clientId = clientId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.clientEdit(clientId)) }
            )
        }
        composable(
            Routes.CLIENT_EDIT,
            arguments = listOf(navArgument("clientId") { type = NavType.LongType })
        ) { backStackEntry ->
            val clientId = backStackEntry.arguments?.getLong("clientId") ?: return@composable
            ClientEditScreen(
                repository = repository,
                clientId = clientId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.BRIEFING,
            arguments = listOf(navArgument("clientId") { type = NavType.LongType })
        ) { backStackEntry ->
            val clientId = backStackEntry.arguments?.getLong("clientId") ?: return@composable
            BriefingScreen(
                repository = repository,
                clientId = clientId,
                onStartRecording = { id ->
                    meetingViewModel.resetForNewMeeting()
                    navController.navigate(Routes.recording(id))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.RECORDING,
            arguments = listOf(navArgument("clientId") { type = NavType.LongType })
        ) { backStackEntry ->
            val clientId = backStackEntry.arguments?.getLong("clientId") ?: return@composable
            RecordingScreen(
                viewModel = meetingViewModel,
                clientId = clientId,
                onSubmitted = { navController.navigate(Routes.RESULT) },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(Routes.RECORDING_UNASSIGNED) {
            RecordingScreen(
                viewModel = meetingViewModel,
                clientId = -1L,
                onSubmitted = { navController.navigate(Routes.RESULT) },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(Routes.RESULT) {
            ResultScreen(
                viewModel = meetingViewModel,
                onSaved = { savedClientId ->
                    // 直接録音フローかどうかを、リセット前に判定しておく。
                    val cameFromClientDetail = meetingViewModel.isClientAssigned()
                    meetingViewModel.resetForNewMeeting()
                    if (cameFromClientDetail) {
                        // 通常フロー: クライアント詳細がスタックに残っているのでそこへ戻る。
                        navController.popBackStack(Routes.CLIENT_DETAIL, inclusive = false)
                    } else {
                        // 直接録音フロー: 詳細がスタックに無いので、ホームまで戻してから開く。
                        navController.popBackStack(Routes.HOME, inclusive = false)
                        navController.navigate(Routes.clientDetail(savedClientId))
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.MEETING_DETAIL,
            arguments = listOf(navArgument("meetingId") { type = NavType.LongType })
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getLong("meetingId") ?: return@composable
            MeetingDetailScreen(
                repository = repository,
                meetingId = meetingId,
                onBack = { navController.popBackStack() },
                onMeetingDeleted = { navController.popBackStack() }
            )
        }
    }
}
