package com.meetingnotes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * F2: クライアントごとの「ここまでの流れ」キャッシュ。
 * 商談が1件増えるたび(= [sourceMeetingCount] が変わったら)再生成する。
 */
@Entity(
    tableName = "client_briefing",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["clientId"], unique = true)]
)
data class ClientBriefingEntity(
    @PrimaryKey val clientId: Long,
    val flowText: String,
    val generatedAt: Long,
    val sourceMeetingCount: Int
)
