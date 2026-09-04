package com.meetingnotes.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.NotificationLogEntity
import com.meetingnotes.data.model.NextMeetingTime
import com.meetingnotes.notifications.ReminderPrefs
import com.meetingnotes.notifications.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/** 通知一覧の1行(これから発火する予定のリマインド)。 */
data class UpcomingReminder(
    val meetingId: Long,
    val clientName: String,
    val start: LocalDateTime,
    val allDay: Boolean
)

class NotificationViewModel(
    application: Application,
    private val repository: MeetingRepository
) : AndroidViewModel(application) {

    private val prefs = ReminderPrefs(application)

    private val _remindersEnabled = MutableStateFlow(prefs.enabled)
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    val history: StateFlow<List<NotificationLogEntity>> = repository.observeNotificationLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _upcoming = MutableStateFlow<List<UpcomingReminder>>(emptyList())
    val upcoming: StateFlow<List<UpcomingReminder>> = _upcoming.asStateFlow()

    init {
        refreshUpcoming()
    }

    fun refreshUpcoming() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val list = withContext(Dispatchers.IO) {
                repository.getNextMeetingCandidates().mapNotNull { c ->
                    val parsed = NextMeetingTime.parse(c.nextMeetingDate) ?: return@mapNotNull null
                    if (parsed.date.isBefore(today)) return@mapNotNull null
                    UpcomingReminder(c.meetingId, c.clientName, parsed.start, parsed.allDay)
                }.sortedBy { it.start }
            }
            _upcoming.value = list
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        prefs.enabled = enabled
        _remindersEnabled.value = enabled
        ReminderScheduler.reschedule(getApplication())
    }

    companion object {
        fun factory(application: Application, repository: MeetingRepository) = viewModelFactory {
            initializer { NotificationViewModel(application, repository) }
        }
    }
}
