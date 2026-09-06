package com.meetingnotes.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.notifications.NotificationSeenState
import com.meetingnotes.ui.client.FollowupItem
import com.meetingnotes.ui.client.FollowupRules
import com.meetingnotes.ui.client.UpcomingItem
import com.meetingnotes.ui.client.UpcomingRules
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ホーム画面「進行中のフェーズ」の集計。対象はヒアリング/提案/見積提示/検討中のみ
 * (初回接触・保留・成約・失注は「次の一手」を要する段階ではないため対象外)。
 */
data class PhaseTrackerCounts(
    val hearing: Int = 0,
    val proposal: Int = 0,
    val quoted: Int = 0,
    val considering: Int = 0
) {
    val total: Int get() = hearing + proposal + quoted + considering
}

class HomeViewModel(repository: MeetingRepository) : ViewModel() {

    private val clients = repository.observeClients()
    private val latestMeetings = repository.observeLatestMeetingPerClient()

    val followups: StateFlow<List<FollowupItem>> =
        combine(clients, latestMeetings) { clientList, latest -> FollowupRules.compute(clientList, latest) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcoming: StateFlow<List<UpcomingItem>> =
        combine(clients, latestMeetings) { clientList, latest -> UpcomingRules.compute(clientList, latest) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 通知一覧を最後に開いてから発火した通知があれば true(ホームの通知タイルの赤マーク)。 */
    val hasUnseenNotifications: StateFlow<Boolean> =
        combine(repository.observeNotificationLog(), NotificationSeenState.seenAt) { log, seenAt ->
            log.any { it.firedAt > seenAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val phaseCounts: StateFlow<PhaseTrackerCounts> =
        latestMeetings
            .map { latest ->
                var hearing = 0
                var proposal = 0
                var quoted = 0
                var considering = 0
                latest.forEach { m ->
                    when (DealPhase.fromWire(m.phaseOverride ?: m.dealPhase)) {
                        DealPhase.HEARING -> hearing++
                        DealPhase.PROPOSAL -> proposal++
                        DealPhase.QUOTED -> quoted++
                        DealPhase.CONSIDERING -> considering++
                        else -> {}
                    }
                }
                PhaseTrackerCounts(hearing, proposal, quoted, considering)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PhaseTrackerCounts())

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
