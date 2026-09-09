package com.meetingnotes.ui.pipeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.ClientProjectEntity
import com.meetingnotes.data.model.DealPhase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** パイプラインボードの1枚のカード。 */
data class PipelineCard(
    val project: ClientProjectEntity,
    val clientId: Long,
    val clientName: String
)

/** フェーズ1列ぶん。 */
data class PipelineColumn(
    val phase: DealPhase,
    val cards: List<PipelineCard>
) {
    val total: Long get() = cards.sumOf { it.project.estimatedAmount ?: 0L }
}

class PipelineViewModel(private val repository: MeetingRepository) : ViewModel() {

    /** 列に出すフェーズ(進行中のみ・パイプラインの並び)。 */
    val phases: List<DealPhase> = listOf(
        DealPhase.FIRST_CONTACT, DealPhase.HEARING, DealPhase.PROPOSAL,
        DealPhase.QUOTED, DealPhase.CONSIDERING, DealPhase.ON_HOLD
    )

    val columns: StateFlow<List<PipelineColumn>> =
        combine(repository.observeAllProjects(), repository.observeClients()) { projects, clients ->
            val nameById = clients.associate { it.id to it.name }
            val byPhase = projects
                .filter { DealPhase.fromWire(it.phase)?.isActive == true }
                .groupBy { DealPhase.fromWire(it.phase)!! }
            phases.map { phase ->
                val cards = (byPhase[phase] ?: emptyList())
                    .sortedByDescending { it.estimatedAmount ?: 0L }
                    .map { p -> PipelineCard(p, p.clientId, nameById[p.clientId] ?: "(不明)") }
                PipelineColumn(phase, cards)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun movePhase(projectId: Long, phase: DealPhase) {
        viewModelScope.launch { repository.setClientProjectPhase(projectId, phase) }
    }

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { PipelineViewModel(repository) }
        }
    }
}
