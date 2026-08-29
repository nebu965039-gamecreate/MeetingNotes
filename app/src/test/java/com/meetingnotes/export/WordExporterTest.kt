package com.meetingnotes.export

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordExporterTest {

    private fun readEntries(bytes: ByteArray): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return entries
    }

    @Test
    fun `docx contains the three required OOXML parts`() {
        val bytes = WordExporter.buildDocx(listOf(ExportBlock.Heading("サマリー")))
        val entries = readEntries(bytes)

        assertEquals(setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml"), entries.keys)
    }

    @Test
    fun `document xml contains heading, paragraph and bullet text`() {
        val bytes = WordExporter.buildDocx(
            listOf(
                ExportBlock.Heading("決定事項"),
                ExportBlock.Paragraph("契約条件について合意した。"),
                ExportBlock.BulletItem("見積書を送付する")
            )
        )
        val documentXml = readEntries(bytes)["word/document.xml"]!!

        assertTrue(documentXml.contains("決定事項"))
        assertTrue(documentXml.contains("契約条件について合意した。"))
        assertTrue(documentXml.contains("・見積書を送付する"))
    }

    @Test
    fun `xml special characters are escaped`() {
        val bytes = WordExporter.buildDocx(listOf(ExportBlock.Paragraph("A&B <C> \"D\"")))
        val documentXml = readEntries(bytes)["word/document.xml"]!!

        assertTrue(documentXml.contains("A&amp;B &lt;C&gt; &quot;D&quot;"))
        assertTrue(!documentXml.contains("A&B <C>"))
    }
}
