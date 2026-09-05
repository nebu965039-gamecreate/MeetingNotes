package com.meetingnotes.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.client.UpcomingItem
import com.meetingnotes.ui.client.UpcomingRules
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ScheduleViewModel(repository: MeetingRepository) : ViewModel() {

    val upcoming: StateFlow<List<UpcomingItem>> =
        combine(repository.observeClients(), repository.observeLatestMeetingPerClient()) { clients, latest ->
            UpcomingRules.compute(clients, latest)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { ScheduleViewModel(repository) }
        }
    }
}
