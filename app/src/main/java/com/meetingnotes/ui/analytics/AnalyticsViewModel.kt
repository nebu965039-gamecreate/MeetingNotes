package com.meetingnotes.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.sales.SalesPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // 「フォローも売上と同じ今月/今年の切り替えがほしい」というフィードバックを受け、
    // 売上タブと同じ SalesPeriod を流用(2026-09-19)。
    private val _followPeriod = MutableStateFlow(SalesPeriod.THIS_MONTH)
    val followPeriod: StateFlow<SalesPeriod> = _followPeriod.asStateFlow()

    val follow: StateFlow<FollowStats?> =
        combine(repository.observeAllTodos(), repository.observeAllMeetings(), _followPeriod) { todos, meetings, period ->
            AnalyticsRules.follow(todos, meetings, period)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setFollowPeriod(period: SalesPeriod) {
        _followPeriod.value = period
    }

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { AnalyticsViewModel(repository) }
        }
    }
}
