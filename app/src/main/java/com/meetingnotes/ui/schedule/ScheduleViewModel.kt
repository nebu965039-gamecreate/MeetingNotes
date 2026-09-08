package com.meetingnotes.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientLatestMeeting
import com.meetingnotes.data.local.OpenTodo
import com.meetingnotes.ui.client.UpcomingItem
import com.meetingnotes.ui.client.UpcomingRules
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(private val repository: MeetingRepository) : ViewModel() {

    private val clients: StateFlow<List<ClientEntity>> = repository.observeClients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val latestMeetings: StateFlow<List<ClientLatestMeeting>> = repository.observeLatestMeetingPerClient()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcoming: StateFlow<List<UpcomingItem>> =
        combine(clients, latestMeetings) { clientList, latest ->
            UpcomingRules.compute(clientList, latest)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 期限が解決できている未完了 ToDo(カレンダーの丸印・日付選択時の一覧に使う)。 */
    val dueTodos: StateFlow<List<OpenTodo>> = repository.observeOpenTodosWithDueDate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun completeTodo(todoId: Long) {
        viewModelScope.launch { repository.setTodoDone(todoId, true) }
    }

    /** すでに商談記録があり、次回予定を新たに設定できるクライアント一覧(名前順)。 */
    val schedulableClients: StateFlow<List<Pair<Long, String>>> =
        combine(clients, latestMeetings) { clientList, latest ->
            val eligible = latest.map { it.clientId }.toSet()
            clientList.filter { it.id in eligible }
                .sortedBy { it.name }
                .map { it.id to it.name }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 日程を変更する(ISO 日付)。 */
    fun rescheduleMeeting(meetingId: Long, dateIso: String) {
        viewModelScope.launch { repository.setNextMeeting(meetingId, dateIso, originalText = null) }
    }

    /** 予定表からの削除 = 次回打ち合わせ日をクリアする(商談の記録自体は消さない)。 */
    fun clearSchedule(meetingId: Long) {
        viewModelScope.launch { repository.setNextMeeting(meetingId, dateIso = null, originalText = null) }
    }

    /** 既存クライアントの最新の商談記録に対して次回予定を新規設定する。 */
    fun scheduleForClient(clientId: Long, dateIso: String) {
        val meetingId = latestMeetings.value.firstOrNull { it.clientId == clientId }?.meetingId ?: return
        rescheduleMeeting(meetingId, dateIso)
    }

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { ScheduleViewModel(repository) }
        }
    }
}
