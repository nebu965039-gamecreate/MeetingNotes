package com.meetingnotes.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 無料枠クレジットの方針(仕様書7.1)。Context/時刻に依存しない純粋関数として切り出し、
 * 月初リセット判定を単体テストしやすくする。
 */
object CreditPolicy {
    const val MONTHLY_FREE_CREDITS = 3

    private val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    fun currentYearMonth(): String = LocalDate.now().format(yearMonthFormatter)

    /** 保存されているlastResetYearMonthが現在の年月と異なればリセットが必要。 */
    fun shouldReset(currentYearMonth: String, storedYearMonth: String): Boolean =
        currentYearMonth != storedYearMonth
}
