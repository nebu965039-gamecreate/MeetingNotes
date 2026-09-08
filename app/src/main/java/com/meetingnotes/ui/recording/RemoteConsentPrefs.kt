package com.meetingnotes.ui.recording

import android.content.Context
import androidx.core.content.edit

/** リモート会議モードの「音声をサーバー送信する」同意を一度だけ確認するための保存。 */
object RemoteConsentPrefs {
    private const val PREFS = "remote_consent"
    private const val KEY = "consented"

    fun hasConsented(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setConsented(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, true) }
    }
}
