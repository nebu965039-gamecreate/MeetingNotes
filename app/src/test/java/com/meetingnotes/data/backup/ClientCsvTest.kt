package com.meetingnotes.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientCsvTest {

    @Test
    fun `export then parse round trips`() {
        val rows = listOf(
            ClientCsv.Row("A社", group = "重要", email = "a@x.jp", leadSource = "紹介", referredBy = "田中"),
            ClientCsv.Row("B, Inc.", memo = "改行\nと\"引用\"を含む")
        )
        val parsed = ClientCsv.parse(ClientCsv.export(rows))
        assertEquals(rows, parsed)
    }

    @Test
    fun `parse maps columns by header name regardless of order`() {
        val csv = "メール,名前,グループ\r\nx@y.jp,テスト社,\r\n"
        val out = ClientCsv.parse(csv)
        assertEquals(1, out.size)
        assertEquals("テスト社", out[0].name)
        assertEquals("x@y.jp", out[0].email)
        assertEquals(null, out[0].group)
    }

    @Test
    fun `rows with blank name are skipped`() {
        val csv = "名前,メール\r\n,foo@bar.jp\r\n有効社,\r\n"
        val out = ClientCsv.parse(csv)
        assertEquals(listOf("有効社"), out.map { it.name })
    }

    @Test
    fun `no name column yields empty`() {
        assertTrue(ClientCsv.parse("会社,メール\r\nA,a@b.jp\r\n").isEmpty())
    }

    @Test
    fun `handles BOM and trailing newline`() {
        val csv = "\uFEFF名前\r\nＸ社\r\n"
        assertEquals(listOf("Ｘ社"), ClientCsv.parse(csv).map { it.name })
    }
}
