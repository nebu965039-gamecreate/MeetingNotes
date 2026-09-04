package com.meetingnotes.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.data.local.NotificationLogEntity
import com.meetingnotes.data.model.NextMeetingTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * F7: 次回打ち合わせの前日(および当日)にリマインド通知を出す。
 * WorkManager の周期実行(約12時間ごと)から呼ばれる。exact alarm 権限は使わない。
 */
class MeetingReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MeetingNotesApp ?: return Result.success()
        val prefs = ReminderPrefs(applicationContext)
        if (!prefs.enabled) return Result.success()
        if (!NotificationHelper.hasPermission(applicationContext)) return Result.success()

        val repository = app.repository
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        val candidates = runCatching { repository.getNextMeetingCandidates() }.getOrDefault(emptyList())
        for (c in candidates) {
            val parsed = NextMeetingTime.parse(c.nextMeetingDate) ?: continue
            val date = parsed.date
            if (date != today && date != tomorrow) continue
            if (repository.notificationAlreadyFired(c.meetingId, c.nextMeetingDate)) continue

            val whenLabel = date.format(DATE_LABEL)
            val lead = if (date == today) "本日" else "明日"
            val title = "$lead の打ち合わせ: ${c.clientName}"
            val body = "$whenLabel" + (if (!parsed.allDay) " %02d:%02d".format(
                parsed.start.hour, parsed.start.minute
            ) else "") + " に ${c.clientName} との打ち合わせがあります。"

            NotificationHelper.notify(
                context = applicationContext,
                notificationId = c.meetingId.toInt(),
                title = title,
                body = body
            )
            repository.logNotification(
                NotificationLogEntity(
                    meetingId = c.meetingId,
                    clientId = c.clientId,
                    title = title,
                    body = body,
                    scheduledFor = c.nextMeetingDate,
                    firedAt = System.currentTimeMillis()
                )
            )
        }

        // 60日より古い履歴は掃除する。
        runCatching {
            repository.pruneNotificationLog(System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000)
        }

        return Result.success()
    }

    private companion object {
        val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)
    }
}
