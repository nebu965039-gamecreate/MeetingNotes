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
    val deadline: String,
    val isDone: Boolean = false
)
