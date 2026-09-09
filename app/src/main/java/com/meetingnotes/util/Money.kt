package com.meetingnotes.util

/** 案件金額の通貨。主単位のみ扱い(円・ドルとも小数は扱わない)。 */
enum class Currency(val code: String, val symbol: String, val label: String) {
    JPY("JPY", "¥", "円 (¥)"),
    USD("USD", "$", "ドル ($)");

    companion object {
        fun of(code: String?): Currency = entries.firstOrNull { it.code == code } ?: JPY
    }
}

/** 12345 → "¥12,345" / "$12,345"。null は "—"。 */
fun formatMoney(amount: Long?, currency: Currency): String {
    if (amount == null) return "—"
    val digits = kotlin.math.abs(amount).toString().reversed().chunked(3).joinToString(",").reversed()
    val sign = if (amount < 0) "-" else ""
    return "$sign${currency.symbol}$digits"
}

/** 通貨コード指定版。 */
fun formatMoney(amount: Long?, currencyCode: String?): String =
    formatMoney(amount, Currency.of(currencyCode))

/** 入力文字列(カンマ・記号・空白を無視した数字)を Long に。空なら null。 */
fun parseMoneyInput(raw: String): Long? {
    val digits = raw.filter { it.isDigit() }
    return digits.takeIf { it.isNotEmpty() }?.toLongOrNull()
}
