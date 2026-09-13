package com.meetingnotes.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.NotificationLogEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class NotificationViewModel(
    application: Application,
    private val repository: MeetingRepository
) : AndroidViewModel(application) {

    val history: StateFlow<List<NotificationLogEntity>> = repository.observeNotificationLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun factory(application: Application, repository: MeetingRepository) = viewModelFactory {
            initializer { NotificationViewModel(application, repository) }
        }
    }
}
