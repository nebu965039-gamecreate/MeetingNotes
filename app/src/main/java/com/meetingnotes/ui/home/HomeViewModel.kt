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
    val lostCount: Int = 0,
    /** 通貨コード → 今月の成約額合計。 */
    val wonAmountThisMonth: Map<String, Long> = emptyMap(),
    /** 通貨コード → 進行中案件の見積額合計(パイプライン)。 */
    val pipelineAmount: Map<String, Long> = emptyMap()
) {
    val winRate: Int?
        get() = (wonCount + lostCount).takeIf { it > 0 }?.let { (wonCount * 100) / it }
}

class HomeViewModel(private val repository: MeetingRepository) : ViewModel() {

    private val clients = repository.observeClients()
    private val latestMeetings = repository.observeLatestMeetingPerClient()
    private val openTodoCounts = repository.observeOpenTodoCountByClient()
    private val clientDealPhase = repository.observeClientDealPhase()

    val followups: StateFlow<List<FollowupItem>> =
        combine(clients, latestMeetings, openTodoCounts, clientDealPhase) { clientList, latest, counts, phases ->
            FollowupRules.compute(
                clientList, latest,
                openTodoCountByClient = counts,
                dealPhaseByClient = phases
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcoming: StateFlow<List<UpcomingItem>> =
        repository.observeSchedules()
            .map { UpcomingRules.order(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** クライアントID → 直近の商談ID(予定詳細から「前回の会議」へ飛ぶため)。 */
    val latestMeetingByClient: StateFlow<Map<Long, Long>> =
        latestMeetings
            .map { list -> list.associate { it.clientId to it.meetingId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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
            latestMeetings,
            repository.observeAllProjects()
        ) { clientList, meetingsThisMonth, openTodoTotal, latest, projects ->
            // 成約率・進行中フェーズは「案件」を数える(案件フェーズを正とする、2026-09-11)。
            val won = projects.count { it.phase == DealPhase.WON.wireValue }
            val lost = projects.count { it.phase == DealPhase.LOST.wireValue }
            val wonAmount = projects
                .filter {
                    it.phase == DealPhase.WON.wireValue &&
                        it.wonAt != null && it.wonAt >= startOfThisMonth &&
                        it.wonAmount != null
                }
                .groupBy { it.currency }
                .mapValues { (_, list) -> list.sumOf { it.wonAmount ?: 0L } }
            val pipeline = projects
                .filter {
                    DealPhase.fromWire(it.phase)?.isActive != false && it.estimatedAmount != null
                }
                .groupBy { it.currency }
                .mapValues { (_, list) -> list.sumOf { it.estimatedAmount ?: 0L } }
            HomeDashboard(
                clientCount = clientList.size,
                meetingsThisMonth = meetingsThisMonth,
                openTodoTotal = openTodoTotal,
                phase = phaseCountsOf(projects),
                wonCount = won,
                lostCount = lost,
                wonAmountThisMonth = wonAmount,
                pipelineAmount = pipeline
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeDashboard())

    /** 進行中なのに一定日数フェーズが動いていない案件。 */
    val staleDeals: StateFlow<List<StaleDeal>> =
        combine(repository.observeAllProjects(), clients) { projects, clientList ->
            StaleDealRules.compute(projects, clientList)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /** 進行中案件をフェーズ別に数える(ヒアリング/提案/見積提示/検討中のみ)。 */
    private fun phaseCountsOf(projects: List<com.meetingnotes.data.local.ClientProjectEntity>): PhaseTrackerCounts {
        var hearing = 0; var proposal = 0; var quoted = 0; var considering = 0
        projects.forEach { p ->
            when (DealPhase.fromWire(p.phase)) {
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
