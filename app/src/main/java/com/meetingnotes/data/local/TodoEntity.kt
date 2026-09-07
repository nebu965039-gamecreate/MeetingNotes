package com.meetingnotes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todos",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("meetingId")]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: Long,
    val task: String,
    val assignee: String,
    /** AI が抽出した期限の原文(例: "金曜日まで")。表示用。 */
    val deadline: String,
    /** [deadline] を解決した ISO 日付(yyyy-MM-dd)。解決できなければ null。ホーム・通知・カレンダーで使う。 */
    val dueDate: String? = null,
    val isDone: Boolean = false
)
