package com.meetingnotes.ui.common

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val monthDayFormatter = DateTimeFormatter.ofPattern("M/d")

/** 「本日」「明日」「M/d」+ 時刻(終日でなければ)のラベル。ホーム画面・予定表で共通利用。 */
fun relativeDateTimeLabel(start: LocalDateTime, allDay: Boolean, today: LocalDate = LocalDate.now()): String {
    val date = start.toLocalDate()
    val dayPart = when (date) {
        today -> "本日"
        today.plusDays(1) -> "明日"
        else -> date.format(monthDayFormatter)
    }
    return if (allDay) dayPart else "$dayPart %02d:%02d".format(start.hour, start.minute)
}
