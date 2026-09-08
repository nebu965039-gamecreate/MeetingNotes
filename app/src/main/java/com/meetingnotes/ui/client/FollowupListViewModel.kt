package com.meetingnotes.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.FollowedUpMeeting
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FollowupListViewModel(private val repository: MeetingRepository) : ViewModel() {

    val followups: StateFlow<List<FollowupItem>> =
        combine(
            repository.observeClients(),
            repository.observeLatestMeetingPerClient(),
            repository.observeOpenTodoCountByClient()
        ) { clients, latest, counts ->
            FollowupRules.compute(clients, latest, openTodoCountByClient = counts)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val followedUp: StateFlow<List<FollowedUpMeeting>> =
        repository.observeFollowedUpMeetings()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 「完了」タブから ToDo に戻す(フォローアップメール ToDo も未完了へ)。 */
    fun unmarkFollowedUp(meetingId: Long) {
        viewModelScope.launch { repository.clearMeetingFollowedUp(meetingId) }
    }

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { FollowupListViewModel(repository) }
        }
    }
}
