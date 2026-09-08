package com.meetingnotes.ui.briefing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.remote.AnthropicClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BriefingUiState {
    data object Loading : BriefingUiState

    /** このクライアントとの初商談。おさらいするものが無い。 */
    data class FirstMeeting(val clientName: String) : BriefingUiState

    data class Ready(
        val clientName: String,
        val flowText: String?,
        val flowLoading: Boolean,
        val flowError: String?,
        val openTodos: List<String>,
        val decisions: List<String>,
        val concerns: List<String>,
        val pastMeetingCount: Int
    ) : BriefingUiState
}

class BriefingViewModel(
    application: Application,
    private val repository: MeetingRepository,
    private val clientId: Long
) : AndroidViewModel(application) {

    private val anthropicClient = AnthropicClient()

    private data class FlowGen(val loading: Boolean = false, val error: String? = null)

    private val _clientName = MutableStateFlow("")
    private val _flowGen = MutableStateFlow(FlowGen())

    val state: StateFlow<BriefingUiState> = combine(
        repository.observeMeetings(clientId),
        repository.observeTodosByClient(clientId),
        repository.observeBriefing(clientId),
        _clientName,
        _flowGen
    ) { meetings, todos, briefing, name, gen ->
        when {
            name.isEmpty() && meetings.isEmpty() -> BriefingUiState.Loading
            meetings.isEmpty() -> BriefingUiState.FirstMeeting(name)
            else -> BriefingUiState.Ready(
                clientName = name,
                flowText = briefing?.flowText,
                flowLoading = gen.loading || (briefing == null && gen.error == null),
                flowError = gen.error,
                openTodos = todos.filter { !it.isDone }
                    .map { "${it.task}(担当: ${it.assignee} / 期限: ${it.deadline})" },
                decisions = meetings.flatMap { it.decisions }.distinct().take(6),
                concerns = meetings.firstOrNull()?.concerns.orEmpty(),
                pastMeetingCount = meetings.size
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BriefingUiState.Loading)

    init {
        viewModelScope.launch {
            _clientName.value = repository.getClient(clientId)?.name ?: ""
            ensureFlow()
        }
    }

    private var ensuring = false

    private fun ensureFlow(force: Boolean = false) {
        viewModelScope.launch {
            if (ensuring) return@launch
            ensuring = true
            try {
                val past = repository.getMeetingsChrono(clientId)
                if (past.isEmpty()) return@launch
                val cache = repository.getBriefing(clientId)
                if (!force && cache != null && cache.sourceMeetingCount == past.size) return@launch
                _flowGen.value = FlowGen(loading = true)
                runCatching { anthropicClient.generateBriefing(past.map { it.summary }) }
                    .onSuccess {
                        repository.saveBriefing(clientId, it, past.size)
                        _flowGen.value = FlowGen()
                    }
                    .onFailure {
                        _flowGen.value = FlowGen(error = it.message ?: "おさらいの作成に失敗しました。")
                    }
            } finally {
                ensuring = false
            }
        }
    }

    fun regenerate() = ensureFlow(force = true)

    companion object {
        fun factory(application: Application, repository: MeetingRepository, clientId: Long) =
            viewModelFactory {
                initializer { BriefingViewModel(application, repository, clientId) }
            }
    }
}
