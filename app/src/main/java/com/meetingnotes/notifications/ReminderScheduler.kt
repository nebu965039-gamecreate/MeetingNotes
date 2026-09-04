package com.meetingnotes.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** F7: リマインドチェックの周期ジョブを登録する。 */
object ReminderScheduler {

    private const val WORK_NAME = "meeting-reminders"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MeetingReminderWorker>(12, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** 設定変更時などに即時チェックを走らせたい場合に周期ジョブを作り直す。 */
    fun reschedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MeetingReminderWorker>(12, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
