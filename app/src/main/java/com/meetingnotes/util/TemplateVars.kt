package com.meetingnotes.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * メール文面テンプレートの差し込み。対応する変数:
 * - `{クライアント名}` … 対象クライアントの名前
 * - `{日付}` … 実行日(M月d日)
 * - `{次回打ち合わせ}` … あれば次回打ち合わせの原文、なければ空
 *
 * 値が無いものは空文字に置き換える(未解決の `{...}` を残さない)。
 */
object TemplateVars {

    private val dateFmt = DateTimeFormatter.ofPattern("M月d日")

    fun apply(
        body: String,
        clientName: String? = null,
        nextMeeting: String? = null,
        today: LocalDate = LocalDate.now()
    ): String = body
        .replace("{クライアント名}", clientName.orEmpty())
        .replace("{日付}", today.format(dateFmt))
        .replace("{次回打ち合わせ}", nextMeeting.orEmpty())
}
