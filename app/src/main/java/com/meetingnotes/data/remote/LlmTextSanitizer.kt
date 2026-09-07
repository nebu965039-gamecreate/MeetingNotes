package com.meetingnotes.data.remote

/**
 * LLM の出力に、モデル内部の関数呼び出しタグ(`<function_calls>` / `<parameter>` など)や
 * 思考ブロックが混入することがまれにある(入力に紛らわしいテキストが含まれた場合など)。
 * ユーザーに見せる前に必ず取り除く。
 */
private val CONTROL_TAGS = Regex(
    """</?\s*(antml:)?(function_calls|invoke|parameter|function_results|thinking)\b[^>]*>""",
    RegexOption.IGNORE_CASE
)

/** タグを閉じ損ねた断片(例: "</antml parameter>", "</ parameter >")も拾う保険。 */
private val LOOSE_CONTROL_FRAGMENT = Regex(
    """</?\s*(antml[:\s]*)?(function_calls|invoke|parameter|function_results)[^>\n]*>?""",
    RegexOption.IGNORE_CASE
)

fun String.stripLlmControlTokens(): String =
    LOOSE_CONTROL_FRAGMENT.replace(CONTROL_TAGS.replace(this, ""), "")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
