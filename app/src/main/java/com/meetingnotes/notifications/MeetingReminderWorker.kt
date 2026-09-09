package com.meetingnotes.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.data.local.NotificationLogEntity
import com.meetingnotes.data.model.NextMeetingTime
import java.time.LocalDate
import java.time.ZoneId
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

        // 今日・明日ぶんの予定(schedules)を拾う。
        val zone = ZoneId.systemDefault()
        val fromMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = tomorrow.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val schedules = runCatching { repository.getSchedulesForReminder(fromMillis, toMillis) }
            .getOrDefault(emptyList())
        for (s in schedules) {
            val start = NextMeetingTime.toLocalDateTime(s.startAtMillis)
            val date = start.toLocalDate()
            if (date != today && date != tomorrow) continue
            val key = "sch-${s.id}-$date"
            if (repository.notificationAlreadyFired(0, key)) continue

            val whenLabel = date.format(DATE_LABEL)
            val lead = if (date == today) "本日" else "明日"
            val title = "$lead の予定: ${s.clientName}"
            val timePart = if (s.hasTime) " %02d:%02d".format(start.hour, start.minute) else ""
            val body = "$whenLabel$timePart ・ ${s.title}(${s.clientName})"

            NotificationHelper.notify(
                context = applicationContext,
                notificationId = ("sch${s.id}").hashCode(),
                title = title,
                body = body
            )
            repository.logNotification(
                NotificationLogEntity(
                    meetingId = 0,
                    clientId = s.clientId,
                    title = title,
                    body = body,
                    scheduledFor = key,
                    firedAt = System.currentTimeMillis()
                )
            )
        }

        // 期限が本日の未完了 ToDo も通知する。
        val todayIso = today.toString()
        val dueTodos = runCatching { repository.getTodosDueOn(todayIso) }.getOrDefault(emptyList())
        for (t in dueTodos) {
            val key = "todo-${t.todoId}-$todayIso"
            if (repository.notificationAlreadyFired(t.meetingId, key)) continue
            val title = "本日期限のToDo: ${t.clientName}"
            val body = "「${t.task}」の期限が本日です。"
            NotificationHelper.notify(
                context = applicationContext,
                notificationId = ("todo${t.todoId}").hashCode(),
                title = title,
                body = body
            )
            repository.logNotification(
                NotificationLogEntity(
                    meetingId = t.meetingId,
                    clientId = t.clientId,
                    title = title,
                    body = body,
                    scheduledFor = key,
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
