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
        combine(repository.observeClients(), repository.observeLatestMeetingPerClient()) { clients, latest ->
            FollowupRules.compute(clients, latest)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val followedUp: StateFlow<List<FollowedUpMeeting>> =
        repository.observeFollowedUpMeetings()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markFollowedUp(meetingIds: List<Long>) {
        viewModelScope.launch { repository.markMeetingsFollowedUp(meetingIds) }
    }

    /** 未フォロー(NEEDS_EMAIL / STALE 問わず)を全部フォロー済みにする。 */
    fun markAllFollowedUp() {
        viewModelScope.launch {
            repository.markMeetingsFollowedUp(followups.value.map { it.meetingId })
        }
    }

    /** フォロー済みを取り消して「未フォロー」に戻す。 */
    fun unmarkFollowedUp(meetingId: Long) {
        viewModelScope.launch { repository.clearMeetingFollowedUp(meetingId) }
    }

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { FollowupListViewModel(repository) }
        }
    }
}
