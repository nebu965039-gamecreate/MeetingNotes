package com.meetingnotes.notifications

import android.content.Context
import androidx.core.content.edit

/** F7: リマインドの有効/無効・通知既読時刻(端末ローカル設定)。 */
class ReminderPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    /** リマインド通知の有効/無効。既定は有効。 */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) { prefs.edit { putBoolean(KEY_ENABLED, value) } }

    /** 通知一覧を最後に開いた時刻(ミリ秒)。これより後に発火した通知があればホームのバッジを出す。 */
    var notificationsSeenAt: Long
        get() = prefs.getLong(KEY_SEEN_AT, 0L)
        set(value) { prefs.edit { putLong(KEY_SEEN_AT, value) } }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_SEEN_AT = "notifications_seen_at"
    }
}
