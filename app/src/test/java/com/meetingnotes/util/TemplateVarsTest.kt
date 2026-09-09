package com.meetingnotes.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TemplateVarsTest {

    private val date = LocalDate.of(2026, 9, 9)

    @Test
    fun `substitutes known variables`() {
        val out = TemplateVars.apply(
            "{クライアント名} 様\n\n{日付}はありがとうございました。次回は{次回打ち合わせ}です。",
            clientName = "テスト株式会社",
            nextMeeting = "9月20日 14:00",
            today = date
        )
        assertEquals(
            "テスト株式会社 様\n\n9月9日はありがとうございました。次回は9月20日 14:00です。",
            out
        )
    }

    @Test
    fun `missing values become empty, no leftover braces`() {
        val out = TemplateVars.apply(
            "{クライアント名}様、{次回打ち合わせ}",
            clientName = null,
            nextMeeting = null,
            today = date
        )
        assertEquals("様、", out)
    }

    @Test
    fun `text without variables is unchanged`() {
        assertEquals("お世話になっております。", TemplateVars.apply("お世話になっております。", today = date))
    }
}
