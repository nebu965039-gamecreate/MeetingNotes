package com.meetingnotes.export

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfExporterTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val blocks = listOf(
        ExportBlock.Heading("商談メモ"),
        ExportBlock.Paragraph("これはPDF出力のスモークテストです。".repeat(10)),
        ExportBlock.BulletItem("見積書を送付する")
    )

    @Test
    fun exportedFileStartsWithPdfMagicBytes() {
        val file = PdfExporter.exportToFile(context, "smoke_test.pdf", blocks, watermark = null)

        assertTrue(file.exists())
        assertTrue(file.length() > 0)

        val header = file.inputStream().use { it.readNBytes(4) }
        assertTrue(header.toString(Charsets.US_ASCII) == "%PDF")
    }

    @Test
    fun watermarkedFileIsLargerThanPlainFile() {
        val plain = PdfExporter.exportToFile(context, "plain.pdf", blocks, watermark = null)
        val watermarked = PdfExporter.exportToFile(
            context,
            "watermarked.pdf",
            blocks,
            watermark = Watermark(text = "SAMPLE", position = WatermarkPosition.CENTER)
        )

        assertTrue(watermarked.length() > plain.length())
    }

    @Test
    fun manyBlocksProduceMultiplePagesWithoutCrashing() {
        val manyBlocks = (1..200).map { ExportBlock.Paragraph("ダミーの行 $it") }
        val file = PdfExporter.exportToFile(context, "multi_page.pdf", manyBlocks, watermark = Watermark("SAMPLE"))

        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }
}
