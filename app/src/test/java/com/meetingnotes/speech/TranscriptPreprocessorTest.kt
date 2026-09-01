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
    fun `repeated acknowledgements are dropped`() {
        val result = preprocessor.preprocess("はい、はい、はい。それで進めましょう")
        assertEquals("それで進めましょう", result)
    }

    @Test
    fun `normal text without filler words is preserved`() {
        val input = "月額プランで契約する方向で進めます。来週見積もりを送付します。"
        assertEquals(input, preprocessor.preprocess(input))
    }

    @Test
    fun `acknowledgement that leads into content is kept`() {
        val input = "はい、承知しました。"
        assertEquals(input, preprocessor.preprocess(input))
        assertEquals(
            "そうですね、その方向で進めます。",
            preprocessor.preprocess("そうですね、その方向で進めます。")
        )
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
    fun `backchannel-only sentences are removed`() {
        val result = preprocessor.preprocess("はい。なるほど。わかりました。来週契約します。")
        assertEquals("来週契約します。", result)
    }

    @Test
    fun `repeated punctuation and long runs are normalized`() {
        val result = preprocessor.preprocess("契約します。。。すぐにーーー対応します")
        assertEquals("契約します。すぐに対応します", result)
    }

    @Test
    fun `leading filler conjunction is trimmed`() {
        val result = preprocessor.preprocess("見積もりを送ります。で、来週打ち合わせます。")
        assertEquals("見積もりを送ります。来週打ち合わせます。", result)
    }
}
