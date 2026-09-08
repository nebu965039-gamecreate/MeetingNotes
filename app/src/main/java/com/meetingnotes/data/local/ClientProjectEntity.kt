package com.meetingnotes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * クライアントの下の「プロジェクト」(任意)。1クライアントに複数持てる。
 * 商談は `meetings.projectId`(FK なしの単純カラム)で任意に紐付ける。
 * プロジェクト削除時は `MeetingRepository.deleteClientProject` が先に該当商談の projectId を null にする。
 */
@Entity(
    tableName = "client_projects",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("clientId")]
)
data class ClientProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val name: String,
    val createdAt: Long
)
