package com.meetingnotes.speech

/**
 * 要約API呼び出し前に文字起こしテキストからフィラー語・連続した相槌を削減し、
 * 送信トークン数を節約する(仕様書7.2)。
 * 意味を変えうる削減(専門用語の省略等)は行わない。
 */
class TranscriptPreprocessor {
    private val fillerPatterns = listOf("えー+", "あのー*", "まあ", "そのー*")
    private val repeatedAckPattern = Regex("(はい[、。]?){2,}")

    fun preprocess(rawText: String): String {
        var text = rawText
        fillerPatterns.forEach { text = text.replace(Regex(it), "") }
        text = text.replace(repeatedAckPattern, "はい。")
        return text.trim()
    }
}
