package com.meetingnotes.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** `MeetingEntity.nextMeetingDate`(ISO 文字列)の解釈。F7 予定カレンダー・リマインドで共通利用。 */
object NextMeetingTime {

    /** LocalDateTime → epoch millis(端末のタイムゾーン)。 */
    fun toMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** epoch millis → LocalDateTime(端末のタイムゾーン)。 */
    fun toLocalDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()

    private val DATE_ONLY = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val DATE_TIME = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})""")

    data class Parsed(val start: LocalDateTime, val allDay: Boolean) {
        val date: LocalDate get() = start.toLocalDate()
    }

    /** ISO 日付(YYYY-MM-DD)または日時(YYYY-MM-DDTHH:MM)なら Parsed、それ以外は null。 */
    fun parse(raw: String?): Parsed? {
        val s = raw?.trim().orEmpty()
        DATE_ONLY.matchEntire(s)?.let { m ->
            val g = m.groupValues
            return runCatching {
                Parsed(LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt()).atStartOfDay(), allDay = true)
            }.getOrNull()
        }
        DATE_TIME.find(s)?.let { m ->
            val g = m.groupValues
            return runCatching {
                Parsed(
                    LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt()).atTime(g[4].toInt(), g[5].toInt()),
                    allDay = false
                )
            }.getOrNull()
        }
        return null
    }

    /** LocalDateTime を保存用 ISO 文字列にする。 */
    fun toIso(dateTime: LocalDateTime, includeTime: Boolean): String =
        if (includeTime) {
            "%04d-%02d-%02dT%02d:%02d".format(
                dateTime.year, dateTime.monthValue, dateTime.dayOfMonth,
                dateTime.hour, dateTime.minute
            )
        } else {
            "%04d-%02d-%02d".format(dateTime.year, dateTime.monthValue, dateTime.dayOfMonth)
        }
}
