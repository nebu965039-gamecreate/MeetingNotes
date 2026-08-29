package com.meetingnotes.export

import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object MeetingExportContentBuilder {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

    fun build(clientName: String?, meeting: MeetingEntity, todos: List<TodoEntity>): List<ExportBlock> = buildList {
        add(ExportBlock.Heading(meeting.title.ifBlank { "商談メモ" }))
        clientName?.let { add(ExportBlock.Paragraph("クライアント: $it")) }
        val recordedAt = Instant.ofEpochMilli(meeting.recordedAt).atZone(ZoneId.systemDefault())
        val endedAtText = meeting.endedAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        }
        add(
            ExportBlock.Paragraph(
                "日時: ${recordedAt.format(dateFormatter)}" + (endedAtText?.let { " 〜 $it" } ?: "")
            )
        )

        add(ExportBlock.Heading("サマリー"))
        add(ExportBlock.Paragraph(meeting.summary))

        add(ExportBlock.Heading("決定事項"))
        if (meeting.decisions.isEmpty()) {
            add(ExportBlock.Paragraph("(なし)"))
        } else {
            meeting.decisions.forEach { add(ExportBlock.BulletItem(it)) }
        }

        add(ExportBlock.Heading("ToDo"))
        if (todos.isEmpty()) {
            add(ExportBlock.Paragraph("(なし)"))
        } else {
            todos.forEach { add(ExportBlock.BulletItem("${it.task}(担当: ${it.assignee} / 期限: ${it.deadline})")) }
        }

        add(ExportBlock.Heading("懸念点・注意点"))
        if (meeting.concerns.isEmpty()) {
            add(ExportBlock.Paragraph("(なし)"))
        } else {
            meeting.concerns.forEach { add(ExportBlock.BulletItem(it)) }
        }

        add(ExportBlock.Heading("次回打ち合わせ"))
        add(ExportBlock.Paragraph(meeting.nextMeetingDate ?: meeting.nextMeetingOriginalText ?: "(未定)"))
    }

    /** メール文面用のプレーンテキスト表現。 */
    fun buildPlainText(clientName: String?, meeting: MeetingEntity, todos: List<TodoEntity>): String =
        build(clientName, meeting, todos).joinToString("\n") { block ->
            when (block) {
                is ExportBlock.Heading -> "\n【${block.text}】"
                is ExportBlock.Paragraph -> block.text
                is ExportBlock.BulletItem -> "・${block.text}"
            }
        }.trim()
}
