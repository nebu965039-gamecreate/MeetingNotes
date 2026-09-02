package com.meetingnotes.export

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Markdown 書き出し。Notion / Obsidian / GitHub 等への貼り付け用。
 * `ExportBlock` の共通モデルをそのまま Markdown 記法に落とすだけ。
 */
object MarkdownExporter {

    /** Markdown テキストを生成する。Context 不要の純粋関数でテストしやすい。 */
    fun buildMarkdown(blocks: List<ExportBlock>): String {
        val sb = StringBuilder()
        var firstHeading = true
        blocks.forEach { block ->
            when (block) {
                is ExportBlock.Heading -> {
                    if (sb.isNotEmpty()) sb.append('\n')
                    // 先頭の見出し(商談タイトル)は h1、以降のセクション見出しは h2。
                    sb.append(if (firstHeading) "# " else "## ").append(block.text).append('\n')
                    firstHeading = false
                }
                is ExportBlock.Paragraph -> sb.append('\n').append(block.text).append('\n')
                is ExportBlock.BulletItem -> sb.append("- ").append(block.text).append('\n')
            }
        }
        return sb.toString().trim() + "\n"
    }

    fun exportToFile(context: Context, fileName: String, blocks: List<ExportBlock>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(buildMarkdown(blocks).toByteArray(Charsets.UTF_8)) }
        return file
    }

    const val MIME_TYPE = "text/markdown"
}
