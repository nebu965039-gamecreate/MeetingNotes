package com.meetingnotes.data.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * ToDo の期限原文(AI 抽出、例: "金曜日まで" "9/15" "来週")を ISO 日付に解決する。
 * 解決できないもの(「未定」「なるべく早く」など)は null。
 * Worker が返す `deadlineDate` を優先し、無いとき/旧データでこれをフォールバックに使う。
 */
object TodoDueDate {

    private val ISO = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val SLASH = Regex("""(?<!\d)(\d{1,2})[/／-](\d{1,2})(?!\d)""")
    private val JP_MD = Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*日""")

    private val weekdays = mapOf(
        "月" to DayOfWeek.MONDAY, "火" to DayOfWeek.TUESDAY, "水" to DayOfWeek.WEDNESDAY,
        "木" to DayOfWeek.THURSDAY, "金" to DayOfWeek.FRIDAY, "土" to DayOfWeek.SATURDAY,
        "日" to DayOfWeek.SUNDAY
    )

    fun parse(raw: String?, today: LocalDate = LocalDate.now()): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        if (t.contains("未定") || t.contains("なるべく") || t.contains("随時") || t.contains("特になし")) return null

        ISO.find(t)?.let { m ->
            return runCatching {
                LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull()?.toString()
        }

        JP_MD.find(t)?.let { m -> return monthDay(m.groupValues[1].toInt(), m.groupValues[2].toInt(), today) }
        SLASH.find(t)?.let { m -> return monthDay(m.groupValues[1].toInt(), m.groupValues[2].toInt(), today) }

        when {
            t.contains("本日") || t.contains("今日") -> return today.toString()
            t.contains("明後日") -> return today.plusDays(2).toString()
            t.contains("明日") -> return today.plusDays(1).toString()
        }

        // 「(来週)◯曜(日)」
        val nextWeek = t.contains("来週")
        for ((k, dow) in weekdays) {
            if (t.contains("${k}曜")) {
                // 今日より後で最初に来る その曜日
                var d = today
                do { d = d.plusDays(1) } while (d.dayOfWeek != dow)
                if (nextWeek) {
                    // 「来週」= 次の月曜以降。まだ今週内なら1週間ずらす
                    val daysToNextMonday =
                        ((DayOfWeek.MONDAY.value - today.dayOfWeek.value + 7) % 7).let { if (it == 0) 7 else it }
                    val nextMonday = today.plusDays(daysToNextMonday.toLong())
                    if (d.isBefore(nextMonday)) d = d.plusWeeks(1)
                }
                return d.toString()
            }
        }
        if (t.contains("今週中") || t.contains("週内")) {
            // その週の金曜(既に過ぎていれば当日)
            var d = today
            while (d.dayOfWeek != DayOfWeek.FRIDAY && d.dayOfWeek != DayOfWeek.SUNDAY) d = d.plusDays(1)
            return d.toString()
        }
        // 「来週」だけ(曜日なし)は特定できないので解決しない
        return null
    }

    private fun monthDay(month: Int, day: Int, today: LocalDate): String? {
        if (month !in 1..12 || day !in 1..31) return null
        return runCatching {
            var date = LocalDate.of(today.year, month, day)
            if (date.isBefore(today.minusDays(1))) date = date.plusYears(1)
            date.toString()
        }.getOrNull()
    }
}
