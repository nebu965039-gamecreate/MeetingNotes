package com.meetingnotes.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 無料枠クレジットの方針(仕様書7.1)。Context/時刻に依存しない純粋関数として切り出し、
 * 月初リセット判定を単体テストしやすくする。
 */
object CreditPolicy {
    const val MONTHLY_FREE_CREDITS = 5

    /** リモート会議モード(サーバー文字起こし)の月間上限。 */
    const val ONLINE_TRANSCRIPTION_PRO_MONTHLY = 40

    /** 無料ユーザーが広告なしで使えるリモート会議モードの月間回数。 */
    const val ONLINE_TRANSCRIPTION_FREE_MONTHLY = 1

    /** 無料ユーザーがリワード広告で追加できる上限(=無料は最大 1 + これ)。 */
    const val ONLINE_TRANSCRIPTION_FREE_BONUS_CAP = 4

    /** その月に使えるリモート会議モードの総回数。 */
    fun onlineTranscriptionAllowance(isPro: Boolean, bonus: Int): Int =
        if (isPro) ONLINE_TRANSCRIPTION_PRO_MONTHLY
        else ONLINE_TRANSCRIPTION_FREE_MONTHLY + bonus.coerceAtMost(ONLINE_TRANSCRIPTION_FREE_BONUS_CAP)

    private val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    fun currentYearMonth(): String = LocalDate.now().format(yearMonthFormatter)

    /** 保存されているlastResetYearMonthが現在の年月と異なればリセットが必要。 */
    fun shouldReset(currentYearMonth: String, storedYearMonth: String): Boolean =
        currentYearMonth != storedYearMonth
}
