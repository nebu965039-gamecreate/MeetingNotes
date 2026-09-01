package com.meetingnotes.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TranscriptPreprocessorTest {

    private val preprocessor = TranscriptPreprocessor()

    @Test
    fun `filler words are removed`() {
        val result = preprocessor.preprocess("えーと、あのー、来週までに見積もりを送ります")
        assertFalse(result.contains("えー"))
        assertFalse(result.contains("あのー"))
        assertEquals("来週までに見積もりを送ります", result)
    }

    @Test
    fun `repeated acknowledgements collapse into a single one`() {
        val result = preprocessor.preprocess("はい、はい、はい。それで進めましょう")
        assertEquals("はい。それで進めましょう", result)
    }

    @Test
    fun `normal text without filler words is preserved`() {
        val input = "月額プランで契約する方向で進めます。来週見積もりを送付します。"
        assertEquals(input, preprocessor.preprocess(input))
    }

    @Test
    fun `single acknowledgement is not altered`() {
        val input = "はい、承知しました。"
        assertEquals(input, preprocessor.preprocess(input))
    }

    @Test
    fun `demonstratives ano and sono without elongation are kept`() {
        val input = "あの資料とその見積もりを確認します。"
        assertEquals(input, preprocessor.preprocess(input))
    }

    @Test
    fun `adjacent duplicate sentences are collapsed`() {
        val result = preprocessor.preprocess("来週見積もりを送ります。来週見積もりを送ります。次回は水曜。")
        assertEquals("来週見積もりを送ります。次回は水曜。", result)
    }

    @Test
    fun `repeated punctuation and long runs are normalized`() {
        val result = preprocessor.preprocess("そうですね。。。まあ、いいでしょうーーーー")
        assertEquals("そうですね。まあ、いいでしょう", result)
    }
}
