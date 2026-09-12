package com.meetingnotes.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * ToDo 1件ごとの通知予約(2026-09-16、旧スヌーズを置き換え)。
 * ユーザーが指定した日時に一度だけ端末通知を送る。WorkManager の一回限りジョブとして登録するため、
 * 端末の再起動をまたいでも消えない(AlarmManager の exact alarm 権限は使わない = 数分程度の
 * 遅延は許容する前提。既存の `MeetingReminderWorker` と同じ方針)。
 */
object TodoNotificationScheduler {

    private fun workName(todoId: Long) = "todo-notify-$todoId"

    /** [atMillis] に ToDo 1件の通知を予約する。同じ ToDo に既存の予約があれば置き換える。 */
    fun schedule(context: Context, todoId: Long, atMillis: Long) {
        val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<TodoNotificationWorker>()
            .setInputData(Data.Builder().putLong(TodoNotificationWorker.KEY_TODO_ID, todoId).build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(todoId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** 予約をキャンセルする(通知解除・ToDo 削除時に呼ぶ)。 */
    fun cancel(context: Context, todoId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(todoId))
    }
}
