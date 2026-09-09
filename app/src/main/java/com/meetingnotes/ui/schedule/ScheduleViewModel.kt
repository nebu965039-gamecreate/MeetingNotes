package com.meetingnotes.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.OpenTodo
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.ui.client.UpcomingItem
import com.meetingnotes.ui.client.UpcomingRules
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(private val repository: MeetingRepository) : ViewModel() {

    /** すべての予定(過去含む)。開始日時順。画面側で未来/選択日で絞り込む。 */
    val schedules: StateFlow<List<UpcomingItem>> =
        repository.observeSchedules()
            .map { UpcomingRules.order(it, includePast = true) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 予定を作成できるクライアント(全件、名前順)。 */
    val clients: StateFlow<List<Pair<Long, String>>> =
        repository.observeClients()
            .map { list -> list.sortedBy { it.name }.map { it.id to it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 期限が解決できている未完了 ToDo(カレンダーの丸印・日付選択時の一覧に使う)。 */
    val dueTodos: StateFlow<List<OpenTodo>> = repository.observeOpenTodosWithDueDate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun completeTodo(todoId: Long) {
        viewModelScope.launch { repository.setTodoDone(todoId, true) }
    }

    fun addSchedule(
        clientId: Long,
        startAtMillis: Long,
        hasTime: Boolean,
        title: String,
        note: String,
        participants: String,
        phase: DealPhase?,
        meetingUrl: String?,
        location: String?
    ) {
        viewModelScope.launch {
            repository.addSchedule(
                clientId, startAtMillis, hasTime, title, note, participants, phase, meetingUrl, location
            )
        }
    }

    fun updateSchedule(
        id: Long,
        startAtMillis: Long,
        hasTime: Boolean,
        title: String,
        note: String,
        participants: String,
        phase: DealPhase?,
        meetingUrl: String?,
        location: String?
    ) {
        viewModelScope.launch {
            repository.updateSchedule(
                id, startAtMillis, hasTime, title, note, participants, phase, meetingUrl, location
            )
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch { repository.deleteSchedule(id) }
    }

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { ScheduleViewModel(repository) }
        }
    }
}
