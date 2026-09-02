package com.meetingnotes.export

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * iCalendar(.ics)書き出し。「次回打ち合わせ」を Google カレンダー / Outlook 等に
 * 取り込むためのファイルを生成する。日時が確定していない場合は生成できない(null)。
 */
object IcsExporter {

    private val UTC_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val DATE_ONLY = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val DATE_TIME = Regex("""^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})""")

    /** カレンダー登録が可能か(`nextMeetingDate` が ISO 日付/日時として解釈できるか)。 */
    fun hasUsableDate(nextMeetingDate: String?): Boolean {
        val raw = nextMeetingDate?.trim().orEmpty()
        return DATE_ONLY.matches(raw) || DATE_TIME.containsMatchIn(raw)
    }

    /**
     * VCALENDAR テキストを生成する。`nextMeetingDate` が ISO 8601 の日付
     * (YYYY-MM-DD)または日時(YYYY-MM-DDTHH:MM)でない場合は null。
     * Context 不要の純粋関数でテストしやすい。
     */
    fun buildIcs(
        meetingId: Long,
        meetingTitle: String,
        clientName: String?,
        nextMeetingDate: String?,
        nextMeetingOriginalText: String?,
    ): String? {
        val raw = nextMeetingDate?.trim().orEmpty()
        val dateOnly = DATE_ONLY.matches(raw)
        val dateTime = DATE_TIME.find(raw)
        if (!dateOnly && dateTime == null) return null

        val summary = clientName?.let { "$it との打ち合わせ" } ?: "次回打ち合わせ($meetingTitle)"
        val descriptionParts = buildList {
            add("商談メモ「$meetingTitle」の次回打ち合わせ")
            nextMeetingOriginalText?.takeIf { it.isNotBlank() }?.let { add("元の表現: $it") }
        }
        val dtStamp = ZonedDateTime.now(ZoneOffset.UTC).format(UTC_STAMP)
        val uid = "meeting-$meetingId-${raw.replace(Regex("[^0-9A-Za-z]"), "")}@meetingnotes.manaapps"

        val (dtStart, dtEnd) = if (dateOnly) {
            val d = LocalDate.parse(raw)
            val f = DateTimeFormatter.BASIC_ISO_DATE
            "DTSTART;VALUE=DATE:${d.format(f)}" to "DTEND;VALUE=DATE:${d.plusDays(1).format(f)}"
        } else {
            val g = dateTime!!.groupValues
            val start = LocalDate.parse(g[1]).atTime(g[2].toInt(), g[3].toInt())
            val end = start.plusHours(1)
            val f = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
            "DTSTART:${start.format(f)}" to "DTEND:${end.format(f)}"
        }

        // RFC 5545 は CRLF 改行。日本語(マルチバイト)行折り返しは UTF-8 を壊すため行わない
        // (Google カレンダー / Apple カレンダーは長い行を許容する)。
        return buildString {
            append("BEGIN:VCALENDAR\r\n")
            append("VERSION:2.0\r\n")
            append("PRODID:-//manaapps//MeetingNotes//JA\r\n")
            append("CALSCALE:GREGORIAN\r\n")
            append("METHOD:PUBLISH\r\n")
            append("BEGIN:VEVENT\r\n")
            append("UID:").append(uid).append("\r\n")
            append("DTSTAMP:").append(dtStamp).append("\r\n")
            append(dtStart).append("\r\n")
            append(dtEnd).append("\r\n")
            append("SUMMARY:").append(escape(summary)).append("\r\n")
            append("DESCRIPTION:").append(escape(descriptionParts.joinToString("\n"))).append("\r\n")
            append("END:VEVENT\r\n")
            append("END:VCALENDAR\r\n")
        }
    }

    fun exportToFile(
        context: Context,
        fileName: String,
        meetingId: Long,
        meetingTitle: String,
        clientName: String?,
        nextMeetingDate: String?,
        nextMeetingOriginalText: String?,
    ): File? {
        val ics = buildIcs(meetingId, meetingTitle, clientName, nextMeetingDate, nextMeetingOriginalText)
            ?: return null
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(ics.toByteArray(Charsets.UTF_8)) }
        return file
    }

    private fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")

    const val MIME_TYPE = "text/calendar"
}
