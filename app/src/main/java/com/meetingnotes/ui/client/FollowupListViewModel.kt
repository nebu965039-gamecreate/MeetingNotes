package com.meetingnotes.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FollowupListViewModel(private val repository: MeetingRepository) : ViewModel() {

    val followups: StateFlow<List<FollowupItem>> =
        combine(repository.observeClients(), repository.observeLatestMeetingPerClient()) { clients, latest ->
            FollowupRules.compute(clients, latest)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markFollowedUp(meetingId: Long) {
        viewModelScope.launch { repository.markMeetingFollowedUp(meetingId) }
    }

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { FollowupListViewModel(repository) }
        }
    }
}
