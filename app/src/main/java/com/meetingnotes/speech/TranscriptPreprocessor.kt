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
    private val leadingFillerConjRegex = Regex("(^|(?<=[。！？\\n]))(で、|でね、|えー、|あの、|まあ、)")
    private val multiSpaceRegex = Regex("[ \\t\\u3000]{2,}")
    private val multiNewlineRegex = Regex("\\n{2,}")
    private val orphanLeadingPunctRegex = Regex("(^|\\n)[、。！？\\s]+")
    private val sentenceSplitRegex = Regex("(?<=[。．！？\\n])")
    private val ignorableForCompareRegex = Regex("[\\s、。．!！?？]")

    // 文全体がこれだけ、という相槌・つなぎ言葉は要約に不要なので落とす。
    private val pureBackchannels = setOf(
        "はい", "ええ", "うん", "そうですね", "そうですか", "なるほど", "確かに",
        "わかりました", "承知しました", "了解です", "ありがとうございます",
    )

    fun preprocess(rawText: String): String {
        var text = rawText
        fillerRegexes.forEach { text = it.replace(text, "") }
        text = repeatedAckRegex.replace(text, "はい。")
        text = repeatedPunctRegex.replace(text) { it.groupValues[1] }
        text = hiraganaElongationRegex.replace(text, "")
        text = longRunRegex.replace(text) { it.groupValues[1] }
        text = leadingFillerConjRegex.replace(text) { it.groupValues[1] }
        text = filterSentences(text)
        text = multiSpaceRegex.replace(text, " ")
        text = multiNewlineRegex.replace(text, "\n")
        text = orphanLeadingPunctRegex.replace(text) { it.groupValues[1] }
        return text.trim()
    }

    /**
     * 文単位のフィルタ:
     *  - 隣接する同一文を畳む(音声認識のセッション切り替えで直前の発話が二重確定しやすい)
     *  - 相槌・つなぎ言葉だけの文を落とす(要約には不要)
     */
    private fun filterSentences(text: String): String {
        val parts = sentenceSplitRegex.split(text)
        val out = StringBuilder()
        var prevKey: String? = null
        for (part in parts) {
            val key = ignorableForCompareRegex.replace(part, "")
            if (key.isEmpty()) {
                out.append(part)
                continue
            }
            if (key == prevKey) continue
            if (key in pureBackchannels) continue
            out.append(part)
            prevKey = key
        }
        return out.toString()
    }
}
