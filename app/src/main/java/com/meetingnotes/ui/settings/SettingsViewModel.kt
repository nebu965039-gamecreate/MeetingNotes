package com.meetingnotes.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.notifications.ReminderPrefs
import com.meetingnotes.notifications.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 設定画面のリマインドON/OFF。テーマ設定は `MeetingNotesApp.themeModeState` を直接読み書きする(全画面即時反映のため)。 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val reminderPrefs = ReminderPrefs(application)

    private val _remindersEnabled = MutableStateFlow(reminderPrefs.enabled)
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    fun setRemindersEnabled(enabled: Boolean) {
        reminderPrefs.enabled = enabled
        _remindersEnabled.value = enabled
        ReminderScheduler.reschedule(getApplication())
    }

    companion object {
        fun factory(application: Application) = viewModelFactory {
            initializer { SettingsViewModel(application) }
        }
    }
}
