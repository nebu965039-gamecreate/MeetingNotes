package com.meetingnotes.export

import com.meetingnotes.data.local.TodoEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val EXPORT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

/**
 * ToDo の期限をエクスポート向けに整形する。「翌日」「来週月曜」のような AI 原文([TodoEntity.deadline])
 * ではなく、解決済みの絶対日付([TodoEntity.dueDate]、ISO yyyy-MM-dd)を優先して
 * "yyyy/MM/dd" 形式に変換する(CSV/Excel は表計算・タスク管理ツールへの取り込みが目的のため、
 * 相対表現より絶対日付の方が扱いやすい)。解決できていない場合のみ原文にフォールバックする。
 */
fun todoDeadlineText(todo: TodoEntity): String {
    val resolved = todo.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return resolved?.format(EXPORT_DATE_FORMAT) ?: todo.deadline
}

/** クライアント横断(全クライアントまとめ)の ToDo エクスポート用の1行。 */
data class TodoWithClient(val clientName: String, val todo: TodoEntity)
