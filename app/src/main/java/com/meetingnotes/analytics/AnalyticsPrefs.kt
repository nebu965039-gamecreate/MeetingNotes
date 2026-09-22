package com.meetingnotes.analytics

import android.content.Context
import androidx.core.content.edit

/** 利用状況データ送信(Analytics/Crashlytics)のON/OFF設定の永続化(端末ローカル)。既定ON。 */
class AnalyticsPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("analytics", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) { prefs.edit { putBoolean(KEY_ENABLED, value) } }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}
