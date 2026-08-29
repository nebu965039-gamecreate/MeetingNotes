package com.meetingnotes.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

enum class WatermarkPosition {
    CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

/** 無料プランのPDFに付与する透かし(仕様書6章)。 */
data class Watermark(
    val text: String,
    val position: WatermarkPosition = WatermarkPosition.CENTER,
    val opacity: Float = 0.3f
)

object PdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40
    private const val BLOCK_SPACING = 12
    private const val HEADING_SPACING_BEFORE = 18
    private const val HEADER_HEIGHT = 48
    private const val FOOTER_HEIGHT = 28
    private const val BULLET_HANGING_INDENT = 16

    /** アプリのMaterial3標準テーマに合わせたブランドカラー(primary相当の紫)。 */
    private val BRAND_COLOR = Color.rgb(0x67, 0x50, 0xA4)

    fun exportToFile(context: Context, fileName: String, blocks: List<ExportBlock>, watermark: Watermark?): File {
        val document = PdfDocument()
        val contentWidth = PAGE_WIDTH - MARGIN * 2

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        drawHeader(canvas)
        var y = MARGIN + HEADER_HEIGHT

        fun finishCurrentPage() {
            watermark?.let { drawWatermark(canvas, it) }
            drawFooter(canvas, pageNumber)
            document.finishPage(page)
        }

        fun startNewPage() {
            finishCurrentPage()
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            drawHeader(canvas)
            y = MARGIN + HEADER_HEIGHT
        }

        blocks.forEachIndexed { index, block ->
            val isFirstBlock = index == 0

            if (block is ExportBlock.Heading && !isFirstBlock) {
                y += HEADING_SPACING_BEFORE
            }

            val text = when (block) {
                is ExportBlock.Heading -> block.text
                is ExportBlock.Paragraph -> block.text
                is ExportBlock.BulletItem -> "・${block.text}"
            }
            val paint = if (block is ExportBlock.Heading) headingPaint() else bodyPaint()

            val layoutBuilder = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
            if (block is ExportBlock.BulletItem) {
                layoutBuilder.setIndents(intArrayOf(0, BULLET_HANGING_INDENT), intArrayOf(0))
            }
            val layout = layoutBuilder.build()

            val bottomLimit = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT
            if (y + layout.height > bottomLimit && y > MARGIN + HEADER_HEIGHT) {
                startNewPage()
            }

            canvas.save()
            canvas.translate(MARGIN.toFloat(), y.toFloat())
            layout.draw(canvas)
            canvas.restore()
            y += layout.height

            if (block is ExportBlock.Heading) {
                y += 4
                val rulePaint = Paint().apply {
                    color = BRAND_COLOR
                    strokeWidth = 1.2f
                }
                canvas.drawLine(MARGIN.toFloat(), y.toFloat(), (PAGE_WIDTH - MARGIN).toFloat(), y.toFloat(), rulePaint)
                y += 6
            } else {
                y += BLOCK_SPACING
            }
        }

        finishCurrentPage()

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun drawHeader(canvas: Canvas) {
        val bandPaint = Paint().apply {
            color = BRAND_COLOR
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), HEADER_HEIGHT.toFloat(), bandPaint)

        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 18f
            isFakeBoldText = true
        }
        val metrics = titlePaint.fontMetrics
        val baselineY = HEADER_HEIGHT / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText("商談メモ", MARGIN.toFloat(), baselineY, titlePaint)
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val paint = TextPaint().apply {
            isAntiAlias = true
            color = Color.GRAY
            textSize = 9f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(pageNumber.toString(), PAGE_WIDTH / 2f, (PAGE_HEIGHT - FOOTER_HEIGHT / 2f), paint)
    }

    private fun drawWatermark(canvas: Canvas, watermark: Watermark) {
        val paint = TextPaint().apply {
            isAntiAlias = true
            color = Color.GRAY
            alpha = (watermark.opacity.coerceIn(0f, 1f) * 255).toInt()
        }

        when (watermark.position) {
            WatermarkPosition.CENTER -> {
                paint.textSize = 56f
                paint.textAlign = Paint.Align.CENTER
                canvas.save()
                canvas.rotate(-45f, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
                canvas.drawText(watermark.text, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, paint)
                canvas.restore()
            }
            WatermarkPosition.TOP_LEFT -> {
                paint.textSize = 12f
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(watermark.text, 20f, 24f, paint)
            }
            WatermarkPosition.TOP_RIGHT -> {
                paint.textSize = 12f
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(watermark.text, PAGE_WIDTH - 20f, 24f, paint)
            }
            WatermarkPosition.BOTTOM_LEFT -> {
                paint.textSize = 12f
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(watermark.text, 20f, PAGE_HEIGHT - 20f, paint)
            }
            WatermarkPosition.BOTTOM_RIGHT -> {
                paint.textSize = 12f
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(watermark.text, PAGE_WIDTH - 20f, PAGE_HEIGHT - 20f, paint)
            }
        }
    }

    private fun headingPaint(): TextPaint = TextPaint().apply {
        isAntiAlias = true
        color = BRAND_COLOR
        textSize = 16f
        isFakeBoldText = true
    }

    private fun bodyPaint(): TextPaint = TextPaint().apply {
        isAntiAlias = true
        color = Color.BLACK
        textSize = 11f
    }

    const val MIME_TYPE = "application/pdf"
}
