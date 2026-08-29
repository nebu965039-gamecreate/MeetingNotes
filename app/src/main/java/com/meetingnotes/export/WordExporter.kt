package com.meetingnotes.export

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Apache POIはAndroid実行環境(java.awt欠如等)との相性問題があるため使わず、
 * 見出し+段落+箇条書きのみの最小限.docx(OOXML)を標準ライブラリのみで直接生成する。
 */
object WordExporter {

    /** .docx(zip)のバイト列を生成する。Context不要の純粋関数でテストしやすい。 */
    fun buildDocx(blocks: List<ExportBlock>): ByteArray {
        val bodyXml = buildString {
            blocks.forEach { block ->
                when (block) {
                    is ExportBlock.Heading -> append(paragraphXml(block.text, bold = true, sizeHalfPoints = 28))
                    is ExportBlock.Paragraph -> append(paragraphXml(block.text))
                    is ExportBlock.BulletItem -> append(paragraphXml("・${block.text}"))
                }
            }
        }

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>
$bodyXml
<w:sectPr/>
</w:body>
</w:document>"""

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
            writeEntry(zip, "_rels/.rels", RELS_XML)
            writeEntry(zip, "word/document.xml", documentXml)
        }
        return output.toByteArray()
    }

    fun exportToFile(context: Context, fileName: String, blocks: List<ExportBlock>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(buildDocx(blocks)) }
        return file
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun paragraphXml(text: String, bold: Boolean = false, sizeHalfPoints: Int = 22): String {
        val rPr = buildString {
            append("<w:rPr>")
            if (bold) append("<w:b/>")
            append("<w:sz w:val=\"$sizeHalfPoints\"/>")
            append("</w:rPr>")
        }
        return "<w:p><w:r>$rPr<w:t xml:space=\"preserve\">${escapeXml(text)}</w:t></w:r></w:p>"
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    private const val RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
}
