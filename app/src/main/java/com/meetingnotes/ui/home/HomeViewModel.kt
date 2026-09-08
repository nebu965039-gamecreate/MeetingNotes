package com.meetingnotes.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.OpenTodo
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.notifications.NotificationSeenState
import java.time.LocalDate
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

/**
 * ホーム画面 最上部のダッシュボード(ドーナツ + 数字)の集計。
 * [winRate] は全期間の成約率(成約 / (成約 + 失注))。母数が 0 なら null で、その行は非表示。
 */
data class HomeDashboard(
    val clientCount: Int = 0,
    val meetingsThisMonth: Int = 0,
    val openTodoTotal: Int = 0,
    val phase: PhaseTrackerCounts = PhaseTrackerCounts(),
    val wonCount: Int = 0,
    val lostCount: Int = 0
) {
    val winRate: Int?
        get() = (wonCount + lostCount).takeIf { it > 0 }?.let { (wonCount * 100) / it }
}

class HomeViewModel(private val repository: MeetingRepository) : ViewModel() {

    private val clients = repository.observeClients()
    private val latestMeetings = repository.observeLatestMeetingPerClient()
    private val openTodoCounts = repository.observeOpenTodoCountByClient()

    val followups: StateFlow<List<FollowupItem>> =
        combine(clients, latestMeetings, openTodoCounts) { clientList, latest, counts ->
            FollowupRules.compute(clientList, latest, openTodoCountByClient = counts)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcoming: StateFlow<List<UpcomingItem>> =
        combine(clients, latestMeetings) { clientList, latest -> UpcomingRules.compute(clientList, latest) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 通知一覧を最後に開いてから発火した通知があれば true(ホームの通知タイルの赤マーク)。 */
    val hasUnseenNotifications: StateFlow<Boolean> =
        combine(repository.observeNotificationLog(), NotificationSeenState.seenAt) { log, seenAt ->
            log.any { it.firedAt > seenAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val startOfThisMonth: Long =
        java.time.YearMonth.now().atDay(1).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    /** ホーム最上部のダッシュボード。 */
    val dashboard: StateFlow<HomeDashboard> =
        combine(
            clients,
            repository.observeMeetingCountSince(startOfThisMonth),
            repository.observeOpenTodoTotal(),
            latestMeetings
        ) { clientList, meetingsThisMonth, openTodoTotal, latest ->
            var won = 0
            var lost = 0
            latest.forEach { m ->
                when (DealPhase.fromWire(m.phaseOverride ?: m.dealPhase)) {
                    DealPhase.WON -> won++
                    DealPhase.LOST -> lost++
                    else -> {}
                }
            }
            HomeDashboard(
                clientCount = clientList.size,
                meetingsThisMonth = meetingsThisMonth,
                openTodoTotal = openTodoTotal,
                phase = phaseCountsOf(latest),
                wonCount = won,
                lostCount = lost
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeDashboard())

    /** 期限切れ + 今日 + 3日以内の未完了 ToDo(期限が解決できているもの)。 */
    val dueTodos: StateFlow<List<OpenTodo>> =
        repository.observeOpenTodosWithDueDate()
            .map { list ->
                val limit = LocalDate.now().plusDays(3).toString()
                list.filter { it.dueDate <= limit }.sortedBy { it.dueDate }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun completeTodo(todoId: Long) {
        viewModelScope.launch { repository.setTodoDone(todoId, true) }
    }

    private fun phaseCountsOf(latest: List<com.meetingnotes.data.local.ClientLatestMeeting>): PhaseTrackerCounts {
        var hearing = 0; var proposal = 0; var quoted = 0; var considering = 0
        latest.forEach { m ->
            when (DealPhase.fromWire(m.phaseOverride ?: m.dealPhase)) {
                DealPhase.HEARING -> hearing++
                DealPhase.PROPOSAL -> proposal++
                DealPhase.QUOTED -> quoted++
                DealPhase.CONSIDERING -> considering++
                else -> {}
            }
        }
        return PhaseTrackerCounts(hearing, proposal, quoted, considering)
    }

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
