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

    /**
     * 1日あたりのAI要約トークン消費上限(2026-09-21〜、無料/Pro共通の安全弁)。
     *
     * 「Proは無制限」を回数ではなくトークン量で守る: 呼び出し回数ではなく実際に消費した
     * トークン数(Anthropicのレスポンスの `usage.input_tokens + output_tokens`)を積算するため、
     * 録音を途中でやめた/失敗したなど短い文字起こしの要約は消費が少なく、正当な利用を圧迫しない。
     * 1文字起こし上限(約20,000字)+システムプロンプト+出力上限をフルに使った最悪ケースの
     * 呼び出しが約26,000トークンなので、その約19回ぶん(1日に19件のフル長さ商談を要約する、
     * という現実的にまず起こらない水準)を上限にしてある。通常利用では絶対に到達しない。
     */
    const val DAILY_TOKEN_CAP = 500_000

    private val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun currentYearMonth(): String = LocalDate.now().format(yearMonthFormatter)

    /** 日次上限のリセット判定に使う「今日」(端末のローカル日付)。 */
    fun currentDate(): String = LocalDate.now().format(dateFormatter)

    /** 保存されているlastResetYearMonthが現在の年月と異なればリセットが必要。 */
    fun shouldReset(currentYearMonth: String, storedYearMonth: String): Boolean =
        currentYearMonth != storedYearMonth

    /** 保存されている日付が今日と異なれば日次トークン消費量のリセットが必要。 */
    fun shouldResetDaily(currentDate: String, storedDate: String): Boolean =
        currentDate != storedDate
}
