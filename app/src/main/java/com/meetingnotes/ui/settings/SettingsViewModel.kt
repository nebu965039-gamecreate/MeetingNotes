package com.meetingnotes.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.data.backup.BackupManager
import com.meetingnotes.notifications.ReminderPrefs
import com.meetingnotes.notifications.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 設定画面のリマインドON/OFF + データのバックアップ/復元。テーマ設定は `MeetingNotesApp.themeModeState` を直接読み書きする。 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val reminderPrefs = ReminderPrefs(application)

    private val _remindersEnabled = MutableStateFlow(reminderPrefs.enabled)
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    /** バックアップ/復元の進行・結果。 */
    sealed interface BackupState {
        data object Idle : BackupState
        data object Working : BackupState
        data class Done(val message: String, val restart: Boolean) : BackupState
        data class Error(val message: String) : BackupState
    }

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    fun clearBackupState() {
        _backupState.value = BackupState.Idle
    }

    fun setRemindersEnabled(enabled: Boolean) {
        reminderPrefs.enabled = enabled
        _remindersEnabled.value = enabled
        ReminderScheduler.reschedule(getApplication())
    }

    private val db
        get() = (getApplication<Application>() as MeetingNotesApp).database.openHelper.writableDatabase

    fun exportBackup(uri: Uri, password: String?) {
        _backupState.value = BackupState.Working
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val json = BackupManager.export(db, password)
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("ファイルに書き込めませんでした。")
                }
            }
            _backupState.value = result.fold(
                onSuccess = {
                    BackupState.Done(
                        if (password.isNullOrEmpty()) "バックアップを作成しました。"
                        else "パスワード付きバックアップを作成しました。",
                        restart = false
                    )
                },
                onFailure = { BackupState.Error(it.message ?: "バックアップに失敗しました。") }
            )
        }
    }

    fun importBackup(uri: Uri, password: String?) {
        _backupState.value = BackupState.Working
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("ファイルを読み取れませんでした。")
                    BackupManager.import(db, text, password)
                }
            }
            _backupState.value = result.fold(
                onSuccess = { BackupState.Done("復元しました。アプリを再起動します。", restart = true) },
                onFailure = { BackupState.Error(it.message ?: "復元に失敗しました。") }
            )
        }
    }

    companion object {
        fun factory(application: Application) = viewModelFactory {
            initializer { SettingsViewModel(application) }
        }
    }
}
