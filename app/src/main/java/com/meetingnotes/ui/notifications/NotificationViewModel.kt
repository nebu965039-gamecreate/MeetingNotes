package com.meetingnotes.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.NotificationLogEntity
import com.meetingnotes.data.model.NextMeetingTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime

/** 通知一覧の1行(これから発火する予定のリマインド)。 */
data class UpcomingReminder(
    val scheduleId: Long,
    val clientId: Long,
    val clientName: String,
    val title: String,
    val start: LocalDateTime,
    val allDay: Boolean
)

class NotificationViewModel(
    application: Application,
    private val repository: MeetingRepository
) : AndroidViewModel(application) {

    val history: StateFlow<List<NotificationLogEntity>> = repository.observeNotificationLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcoming: StateFlow<List<UpcomingReminder>> =
        repository.observeSchedules()
            .map { list ->
                val today = LocalDate.now()
                list.mapNotNull { s ->
                    val start = NextMeetingTime.toLocalDateTime(s.startAtMillis)
                    if (start.toLocalDate().isBefore(today)) return@mapNotNull null
                    UpcomingReminder(s.id, s.clientId, s.clientName, s.title, start, !s.hasTime)
                }.sortedBy { it.start }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun factory(application: Application, repository: MeetingRepository) = viewModelFactory {
            initializer { NotificationViewModel(application, repository) }
        }
    }
}
