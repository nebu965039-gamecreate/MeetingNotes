package com.meetingnotes.export

import android.content.Context
import com.meetingnotes.data.local.TodoEntity
import java.io.File
import java.io.FileOutputStream

/**
 * CSV 書き出し。ToDo 一覧(タスク / 担当 / 期限 / 完了)を表計算ソフトや
 * タスク管理ツールに取り込むための形式。RFC 4180 準拠(CRLF 改行、
 * カンマ・改行・二重引用符を含むフィールドは "" で囲みエスケープ)。
 */
object CsvExporter {

    private val HEADER = listOf("タスク", "担当", "期限", "完了")

    /** CSV テキスト(BOM なし)を生成する。Context 不要の純粋関数でテストしやすい。 */
    fun buildCsv(todos: List<TodoEntity>): String {
        val rows = buildList {
            add(HEADER)
            todos.forEach { add(listOf(it.task, it.assignee, it.deadline, if (it.isDone) "済" else "")) }
        }
        return rows.joinToString("\r\n") { row -> row.joinToString(",") { escape(it) } } + "\r\n"
    }

    fun exportToFile(context: Context, fileName: String, todos: List<TodoEntity>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            // Excel が UTF-8 CSV を正しく開けるよう BOM を付ける。
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            out.write(buildCsv(todos).toByteArray(Charsets.UTF_8))
        }
        return file
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    const val MIME_TYPE = "text/csv"
}
