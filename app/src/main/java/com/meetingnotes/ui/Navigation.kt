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
import com.meetingnotes.ui.client.ClientDetailScreen
import com.meetingnotes.ui.client.ClientListScreen
import com.meetingnotes.ui.help.HelpScreen
import com.meetingnotes.ui.meeting.MeetingDetailScreen
import com.meetingnotes.ui.recording.RecordingScreen
import com.meetingnotes.ui.result.ResultScreen

object Routes {
    const val CLIENT_LIST = "clientList"
    const val CLIENT_DETAIL = "clientDetail/{clientId}"
    const val RECORDING = "recording/{clientId}"
    const val RESULT = "result"
    const val MEETING_DETAIL = "meetingDetail/{meetingId}"
    const val HELP = "help"

    fun clientDetail(clientId: Long) = "clientDetail/$clientId"
    fun recording(clientId: Long) = "recording/$clientId"
    fun meetingDetail(meetingId: Long) = "meetingDetail/$meetingId"
}

@Composable
fun MeetingNotesNavHost(
    repository: MeetingRepository,
    navController: NavHostController = rememberNavController()
) {
    val meetingViewModel: MeetingViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.CLIENT_LIST) {
        composable(Routes.CLIENT_LIST) {
            ClientListScreen(
                repository = repository,
                onClientSelected = { clientId ->
                    navController.navigate(Routes.clientDetail(clientId))
                },
                onRecoverDraft = { clientId ->
                    meetingViewModel.resetForNewMeeting()
                    navController.navigate(Routes.recording(clientId))
                },
                onHelp = { navController.navigate(Routes.HELP) }
            )
        }
        composable(Routes.HELP) {
            HelpScreen(onBack = { navController.popBackStack() })
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
                onMeetingSelected = { meetingId ->
                    navController.navigate(Routes.meetingDetail(meetingId))
                },
                onBack = { navController.popBackStack() },
                onClientDeleted = { navController.popBackStack(Routes.CLIENT_LIST, inclusive = false) }
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
        composable(Routes.RESULT) {
            ResultScreen(
                viewModel = meetingViewModel,
                onSaved = {
                    meetingViewModel.resetForNewMeeting()
                    navController.popBackStack(Routes.CLIENT_DETAIL, inclusive = false)
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
