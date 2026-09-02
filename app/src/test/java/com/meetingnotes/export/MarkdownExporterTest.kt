package com.meetingnotes.export

import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownExporterTest {

    @Test
    fun `first heading is h1 and later headings are h2`() {
        val md = MarkdownExporter.buildMarkdown(
            listOf(
                ExportBlock.Heading("契約の打ち合わせ"),
                ExportBlock.Heading("サマリー"),
                ExportBlock.Paragraph("契約条件について合意した。")
            )
        )
        assertTrue(md.contains("# 契約の打ち合わせ"))
        assertTrue(md.contains("## サマリー"))
        assertTrue(!md.contains("## 契約の打ち合わせ"))
        assertTrue(md.contains("契約条件について合意した。"))
    }

    @Test
    fun `bullet items use dash syntax`() {
        val md = MarkdownExporter.buildMarkdown(
            listOf(
                ExportBlock.Heading("決定事項"),
                ExportBlock.BulletItem("見積書を送付する")
            )
        )
        assertTrue(md.contains("- 見積書を送付する"))
    }

    @Test
    fun `output ends with a single trailing newline`() {
        val md = MarkdownExporter.buildMarkdown(listOf(ExportBlock.Paragraph("本文")))
        assertTrue(md.endsWith("\n"))
        assertTrue(!md.endsWith("\n\n"))
    }
}
