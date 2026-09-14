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
     *  - 近傍の同一文を畳む(音声認識のセッション切り替えで直前・近傍の発話が二重確定しやすい)
     *  - 相槌・つなぎ言葉だけの文を落とす(要約には不要)
     *
     * 2026-09-21: 「直前の1文のみと比較」だと、`TranscriptionManager` 側のセッション境界処理で
     * 間に他の文が挟まった状態で重複が発生するケース(実機報告)を取りこぼしていたため、
     * 直近 [RECENT_SENTENCE_WINDOW] 文以内での重複も畳むよう拡張した。長い商談の中で離れた
     * タイミングに本当に同じ発言が繰り返された場合(要約上は残したい)まで潰さないよう、
     * 窓は「近傍」に留め、トランスクリプト全体を対象にした重複除去はしない。
     */
    private fun filterSentences(text: String): String {
        val parts = sentenceSplitRegex.split(text)
        val out = StringBuilder()
        val recentKeys = ArrayDeque<String>()
        for (part in parts) {
            val key = ignorableForCompareRegex.replace(part, "")
            if (key.isEmpty()) {
                out.append(part)
                continue
            }
            if (key in recentKeys) continue
            if (key in pureBackchannels) continue
            out.append(part)
            recentKeys.addLast(key)
            if (recentKeys.size > RECENT_SENTENCE_WINDOW) recentKeys.removeFirst()
        }
        return out.toString()
    }

    private companion object {
        /** 重複判定で「近傍」とみなす文の数(直前のNつぶんと比較)。 */
        const val RECENT_SENTENCE_WINDOW = 5
    }
}
