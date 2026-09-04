package com.meetingnotes.notifications

import android.content.Context
import androidx.core.content.edit

/** F7: リマインドの有効/無効(端末ローカル設定)。既定は有効。 */
class ReminderPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) { prefs.edit { putBoolean(KEY_ENABLED, value) } }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}
