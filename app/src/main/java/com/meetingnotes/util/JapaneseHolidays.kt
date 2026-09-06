package com.meetingnotes.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * 日本の国民の祝日(振替休日・国民の休日込み)。カレンダー表示の色分けに使う純粋関数。
 * 春分/秋分の近似式は概ね 1980〜2099 年で有効。オリンピック等の一度きりの特例日は扱わない。
 */
object JapaneseHolidays {

    fun isHoliday(date: LocalDate): Boolean =
        holidaysInMonth(date.year, date.monthValue).contains(date)

    /** 指定した年月の祝日(振替休日・国民の休日を含む)。 */
    fun holidaysInMonth(year: Int, month: Int): Set<LocalDate> {
        // 振替休日・国民の休日は月をまたぐことがあるため前後1か月ぶんの「本来の祝日」を集める。
        val base = sortedSetOf<LocalDate>()
        for (offset in -1..1) {
            val ym = YearMonth.of(year, month).plusMonths(offset.toLong())
            base += fixedHolidays(ym.year, ym.monthValue)
        }

        val result = sortedSetOf<LocalDate>()
        result += base

        // 振替休日: 日曜が祝日なら、直後の非祝日を休日にする。
        for (h in base) {
            if (h.dayOfWeek == DayOfWeek.SUNDAY) {
                var d = h.plusDays(1)
                while (d in base || d in result) d = d.plusDays(1)
                result += d
            }
        }

        // 国民の休日: 前後が祝日で、その日自身は祝日でない平日。
        for (h in base) {
            val between = h.plusDays(1)
            if (between !in base && between.plusDays(1) in base && between.dayOfWeek != DayOfWeek.SUNDAY) {
                result += between
            }
        }

        return result.filter { it.year == year && it.monthValue == month }.toSet()
    }

    private fun fixedHolidays(year: Int, month: Int): List<LocalDate> {
        fun d(day: Int) = LocalDate.of(year, month, day)
        fun nthMonday(n: Int) =
            LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.MONDAY))

        return when (month) {
            1 -> listOf(d(1), nthMonday(2))            // 元日 / 成人の日
            2 -> listOf(d(11), d(23))                  // 建国記念の日 / 天皇誕生日
            3 -> listOf(d(springEquinox(year)))        // 春分の日
            4 -> listOf(d(29))                         // 昭和の日
            5 -> listOf(d(3), d(4), d(5))              // 憲法記念日 / みどりの日 / こどもの日
            7 -> listOf(nthMonday(3))                  // 海の日
            8 -> listOf(d(11))                         // 山の日
            9 -> listOf(d(autumnEquinox(year)), nthMonday(3)) // 秋分の日 / 敬老の日
            10 -> listOf(nthMonday(2))                 // スポーツの日
            11 -> listOf(d(3), d(23))                  // 文化の日 / 勤労感謝の日
            else -> emptyList()
        }
    }

    private fun springEquinox(year: Int): Int =
        (20.8431 + 0.242194 * (year - 1980) - (year - 1980) / 4).toInt()

    private fun autumnEquinox(year: Int): Int =
        (23.2488 + 0.242194 * (year - 1980) - (year - 1980) / 4).toInt()
}
