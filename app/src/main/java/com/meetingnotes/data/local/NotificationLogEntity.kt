package com.meetingnotes.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * F7: 発火したリマインド通知の履歴。商談が消えても残せるよう FK は張らない。
 * `scheduledFor` は通知対象の商談日(重複発火の防止キー)。
 */
@Entity(
    tableName = "notification_log",
    indices = [Index(value = ["meetingId", "scheduledFor"], unique = true)]
)
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: Long,
    val clientId: Long,
    val title: String,
    val body: String,
    val scheduledFor: String,
    val firedAt: Long
)
