package com.meetingnotes.speech

/**
 * 要約API呼び出し前に文字起こしテキストを整形し、送信トークン数と要約精度を改善する(仕様書7.2)。
 *
 *  - フィラー語・連続した相槌の削減
 *  - 音声認識のセッション切り替えで生じる「隣接する同一文」の除去
 *  - 連続した同一記号・かなの長音の正規化、空白の圧縮
 *
 * 意味を変えうる削減(指示語「あの」「その」単体の除去、専門用語の省略等)は行わない。
 */
class TranscriptPreprocessor {

    // 末尾を伸ばした形(ー)や明確なフィラーのみ対象。「あの」「その」「ええ」単体は残す。
    private val fillerRegexes = listOf(
        Regex("えー+っ?と?"),        // えー / えーっと / えーと / えーーー
        Regex("ええと"),
        Regex("えっと"),
        Regex("あの[ーあ]+"),        // あのー / あのあの
        Regex("その[ーそ]+"),
        Regex("んー+"),
        Regex("うー+ん(?=[、。\\s]|$)"), // 相槌の「うーん」
    )
    private val repeatedAckRegex = Regex("(はい[、。]?\\s*){2,}")
    private val repeatedPunctRegex = Regex("([。、！？])\\1+")
    private val hiraganaElongationRegex = Regex("(?<=[ぁ-ん])ー{2,}")
    private val longRunRegex = Regex("([ぁ-ん])\\1{3,}")
    private val multiSpaceRegex = Regex("[ \\t\\u3000]{2,}")
    private val multiNewlineRegex = Regex("\\n{2,}")
    private val orphanLeadingPunctRegex = Regex("(^|\\n)[、。！？\\s]+")
    private val sentenceSplitRegex = Regex("(?<=[。．！？\\n])")
    private val ignorableForCompareRegex = Regex("[\\s、。．!！?？]")

    fun preprocess(rawText: String): String {
        var text = rawText
        fillerRegexes.forEach { text = it.replace(text, "") }
        text = repeatedAckRegex.replace(text, "はい。")
        text = repeatedPunctRegex.replace(text) { it.groupValues[1] }
        text = hiraganaElongationRegex.replace(text, "")
        text = longRunRegex.replace(text) { it.groupValues[1] }
        text = dropAdjacentDuplicateSentences(text)
        text = multiSpaceRegex.replace(text, " ")
        text = multiNewlineRegex.replace(text, "\n")
        text = orphanLeadingPunctRegex.replace(text) { it.groupValues[1] }
        return text.trim()
    }

    /** 隣接する同一文を1つに畳む。音声認識のセッション切り替えで直前の発話が二重に確定することがある。 */
    private fun dropAdjacentDuplicateSentences(text: String): String {
        val parts = sentenceSplitRegex.split(text)
        val out = StringBuilder()
        var prevKey: String? = null
        for (part in parts) {
            val key = ignorableForCompareRegex.replace(part, "")
            if (key.isNotEmpty() && key == prevKey) continue
            out.append(part)
            if (key.isNotEmpty()) prevKey = key
        }
        return out.toString()
    }
}
