package com.meetingnotes.export

import android.content.Context
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Apache POI は使わず、インライン文字列のみの最小限 .xlsx(SpreadsheetML)を
 * 標準ライブラリだけで直接生成する(`WordExporter` と同じ方針)。
 * ToDo は「タスク / 担当 / 期限 / 完了」の表として書き出す(Excel での二次利用向け)。
 */
object ExcelExporter {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

    /** シートの行データ(各行 = A列からのセル文字列)を組み立てる。テスト用に公開。 */
    fun buildRows(clientName: String?, meeting: MeetingEntity, todos: List<TodoEntity>): List<List<String>> = buildList {
        add(listOf("商談メモ", meeting.title.ifBlank { "商談メモ" }))
        clientName?.let { add(listOf("クライアント", it)) }
        val recordedAt = Instant.ofEpochMilli(meeting.recordedAt).atZone(ZoneId.systemDefault())
        val endedAtText = meeting.endedAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        }
        add(listOf("日時", recordedAt.format(dateFormatter) + (endedAtText?.let { " 〜 $it" } ?: "")))
        add(emptyList())

        add(listOf("サマリー"))
        add(listOf(meeting.summary))
        add(emptyList())

        add(listOf("決定事項"))
        if (meeting.decisions.isEmpty()) add(listOf("(なし)")) else meeting.decisions.forEach { add(listOf(it)) }
        add(emptyList())

        add(listOf("ToDo"))
        add(listOf("タスク", "担当", "期限", "完了"))
        if (todos.isEmpty()) {
            add(listOf("(なし)"))
        } else {
            todos.forEach { add(listOf(it.task, it.assignee, it.deadline, if (it.isDone) "済" else "")) }
        }
        add(emptyList())

        add(listOf("懸念点・注意点"))
        if (meeting.concerns.isEmpty()) add(listOf("(なし)")) else meeting.concerns.forEach { add(listOf(it)) }
        add(emptyList())

        add(listOf("次回打ち合わせ"))
        add(listOf(meeting.nextMeetingDate ?: meeting.nextMeetingOriginalText ?: "(未定)"))
    }

    /** .xlsx(zip)のバイト列を生成する。 */
    fun buildXlsx(clientName: String?, meeting: MeetingEntity, todos: List<TodoEntity>): ByteArray {
        val rows = buildRows(clientName, meeting, todos)
        val sheetData = buildString {
            rows.forEachIndexed { index, cells ->
                val r = index + 1
                if (cells.isEmpty()) return@forEachIndexed
                append("<row r=\"$r\">")
                cells.forEachIndexed { col, value ->
                    if (value.isEmpty()) return@forEachIndexed
                    val ref = "${('A' + col)}$r"
                    append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    append(escapeXml(value))
                    append("</t></is></c>")
                }
                append("</row>")
            }
        }

        val sheetXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<sheetData>$sheetData</sheetData>
</worksheet>"""

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
            writeEntry(zip, "_rels/.rels", RELS_XML)
            writeEntry(zip, "xl/workbook.xml", WORKBOOK_XML)
            writeEntry(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS_XML)
            writeEntry(zip, "xl/worksheets/sheet1.xml", sheetXml)
        }
        return output.toByteArray()
    }

    fun exportToFile(
        context: Context,
        fileName: String,
        clientName: String?,
        meeting: MeetingEntity,
        todos: List<TodoEntity>,
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(buildXlsx(clientName, meeting, todos)) }
        return file
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
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
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private const val RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="商談メモ" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private const val WORKBOOK_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
}
