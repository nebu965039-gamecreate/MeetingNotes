package com.meetingnotes.notifications

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知一覧を最後に開いた時刻をプロセス全体で共有する。
 * `ReminderPrefs` に永続化しつつ、ホーム画面のバッジを即時更新するため Flow でも保持する。
 */
object NotificationSeenState {

    private val _seenAt = MutableStateFlow(0L)
    val seenAt: StateFlow<Long> = _seenAt.asStateFlow()

    fun init(context: Context) {
        _seenAt.value = ReminderPrefs(context).notificationsSeenAt
    }

    /** 通知一覧を開いたときに呼ぶ。 */
    fun markSeen(context: Context) {
        val now = System.currentTimeMillis()
        ReminderPrefs(context).notificationsSeenAt = now
        _seenAt.value = now
    }
}
