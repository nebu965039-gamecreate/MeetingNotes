package com.meetingnotes.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 売上・実績ビュー(Pro)の集計。案件(`client_projects`)の金額・フェーズ・成約日から算出。 */
class SalesViewModel(repository: MeetingRepository) : ViewModel() {

    private val _period = MutableStateFlow(SalesPeriod.THIS_MONTH)
    val period: StateFlow<SalesPeriod> = _period.asStateFlow()

    val reports: StateFlow<List<SalesCurrencyReport>> =
        combine(repository.observeAllProjects(), _period) { projects, period ->
            SalesRules.report(projects, period)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPeriod(period: SalesPeriod) {
        _period.value = period
    }

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { SalesViewModel(repository) }
        }
    }
}
