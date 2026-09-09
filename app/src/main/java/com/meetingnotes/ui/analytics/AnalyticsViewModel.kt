package com.meetingnotes.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 「分析」画面の 活動 / 顧客 / フォロー タブの集計。売上タブは `SalesViewModel` が担当。 */
class AnalyticsViewModel(repository: MeetingRepository) : ViewModel() {

    val activity: StateFlow<ActivityStats?> =
        repository.observeAllMeetings()
            .map { AnalyticsRules.activity(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val customers: StateFlow<CustomerStats?> =
        combine(repository.observeClients(), repository.observeAllProjects()) { clients, projects ->
            AnalyticsRules.customers(clients, projects)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val follow: StateFlow<FollowStats?> =
        combine(repository.observeAllTodos(), repository.observeAllMeetings()) { todos, meetings ->
            AnalyticsRules.follow(todos, meetings)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { AnalyticsViewModel(repository) }
        }
    }
}
