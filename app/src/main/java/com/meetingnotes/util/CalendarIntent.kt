package com.meetingnotes.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import java.time.LocalDateTime
import java.time.ZoneId

/** OS のカレンダーアプリ(Google カレンダー等)の「予定作成」画面を開く。権限不要。 */
object CalendarIntent {

    fun add(
        context: Context,
        title: String,
        start: LocalDateTime,
        allDay: Boolean,
        description: String = ""
    ) {
        val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            if (description.isNotBlank()) putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            if (allDay) {
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            } else {
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 60L * 60L * 1000L)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "カレンダーアプリが見つかりませんでした。", Toast.LENGTH_SHORT).show()
        }
    }
}
