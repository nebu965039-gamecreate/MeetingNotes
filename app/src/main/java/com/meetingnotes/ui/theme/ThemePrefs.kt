package com.meetingnotes.ui.theme

import android.content.Context
import androidx.core.content.edit

/** 背景テーマの選択肢。既定は端末の設定に従う。 */
enum class ThemeMode(val label: String) {
    LIGHT("ライト"),
    DARK("ダーク"),
    SYSTEM("端末の設定に従う")
}

/** テーマ設定の永続化(端末ローカル)。 */
class ThemePrefs(context: Context) {

    private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)

    var mode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: ThemeMode.SYSTEM
        set(value) { prefs.edit { putString(KEY_MODE, value.name) } }

    private companion object {
        const val KEY_MODE = "mode"
    }
}
