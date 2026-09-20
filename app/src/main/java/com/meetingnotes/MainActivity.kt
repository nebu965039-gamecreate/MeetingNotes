package com.meetingnotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.meetingnotes.ui.MeetingNotesNavHost
import com.meetingnotes.ui.theme.ThemeMode
import com.meetingnotes.ui.theme.navyDarkColorScheme
import com.meetingnotes.ui.theme.navyLightColorScheme
import com.meetingnotes.widget.TodayWidgetProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MeetingNotesApp
        val repository = app.repository
        setContent {
            val darkTheme = when (app.themeModeState.value) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val colorScheme = if (darkTheme) navyDarkColorScheme() else navyLightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MeetingNotesNavHost(repository = repository)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 別端末での購入・解約・払い戻しに追従する。
        (application as MeetingNotesApp).billingManager.refreshPurchases()
        // アプリ内で予定・ToDoを更新してホーム画面に戻ったときに、ウィジェットの自動更新(最短30分)を
        // 待たずに最新化する。ウィジェットが追加されていなければ内部で何もしない。
        TodayWidgetProvider.requestUpdate(this)
    }
}
