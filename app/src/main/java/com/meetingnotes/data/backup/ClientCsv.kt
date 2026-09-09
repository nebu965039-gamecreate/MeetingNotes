package com.meetingnotes.data.backup

/**
 * クライアント一覧の CSV 書き出し / 取り込み(純粋関数)。RFC 4180 準拠(CRLF・"" エスケープ)。
 * 取り込みはヘッダー行の列名でマッピングするため、列の順番が違っても・一部の列が無くても読める。
 * 名前が空の行は無視する。
 */
object ClientCsv {

    private const val BOM_CODE = 0xFEFF

    /** 1クライアントぶんの取り込み可能な項目。 */
    data class Row(
        val name: String,
        val group: String? = null,
        val email: String? = null,
        val phone: String? = null,
        val leadSource: String? = null,
        val referredBy: String? = null,
        val memo: String? = null
    )

    private const val NAME = "名前"
    private const val GROUP = "グループ"
    private const val EMAIL = "メール"
    private const val PHONE = "電話"
    private const val LEAD = "流入経路"
    private const val REF = "紹介元"
    private const val MEMO = "備考"

    private val HEADER = listOf(NAME, GROUP, EMAIL, PHONE, LEAD, REF, MEMO)

    fun export(rows: List<Row>): String {
        val lines = buildList {
            add(HEADER)
            rows.forEach {
                add(
                    listOf(
                        it.name, it.group.orEmpty(), it.email.orEmpty(), it.phone.orEmpty(),
                        it.leadSource.orEmpty(), it.referredBy.orEmpty(), it.memo.orEmpty()
                    )
                )
            }
        }
        return lines.joinToString("\r\n") { row -> row.joinToString(",") { escape(it) } } + "\r\n"
    }

    fun parse(csv: String): List<Row> {
        val noBom = if (csv.isNotEmpty() && csv[0].code == BOM_CODE) csv.substring(1) else csv
        val records = parseRecords(noBom)
        if (records.isEmpty()) return emptyList()
        val header = records.first().map { it.trim() }
        fun idx(name: String) = header.indexOf(name)
        val iName = idx(NAME)
        if (iName < 0) return emptyList()
        val iGroup = idx(GROUP); val iEmail = idx(EMAIL); val iPhone = idx(PHONE)
        val iLead = idx(LEAD); val iRef = idx(REF); val iMemo = idx(MEMO)

        fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
        fun List<String>.at(i: Int): String? = if (i in 0..lastIndex) this[i] else null

        return records.drop(1).mapNotNull { fields ->
            val name = fields.at(iName)?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            Row(
                name = name,
                group = fields.at(iGroup).clean(),
                email = fields.at(iEmail).clean(),
                phone = fields.at(iPhone).clean(),
                leadSource = fields.at(iLead).clean(),
                referredBy = fields.at(iRef).clean(),
                memo = fields.at(iMemo).clean()
            )
        }
    }

    // ---- RFC 4180 パーサ ----

    private fun parseRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        var sawAny = false
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> when {
                    ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    ch == '"' -> inQuotes = false
                    else -> field.append(ch)
                }
                ch == '"' -> { inQuotes = true; sawAny = true }
                ch == ',' -> { row.add(field.toString()); field = StringBuilder(); sawAny = true }
                ch == '\r' -> { /* skip, handled by \n */ }
                ch == '\n' -> {
                    row.add(field.toString()); field = StringBuilder()
                    if (sawAny || row.size > 1) records.add(row)
                    row = mutableListOf(); sawAny = false
                }
                else -> { field.append(ch); sawAny = true }
            }
            i++
        }
        if (sawAny || field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            if (row.any { it.isNotEmpty() }) records.add(row)
        }
        return records
    }

    private fun escape(f: String): String =
        if (f.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + f.replace("\"", "\"\"") + "\""
        } else f
}
