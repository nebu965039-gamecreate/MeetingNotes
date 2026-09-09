package com.meetingnotes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clients",
    foreignKeys = [
        ForeignKey(
            entity = ClientGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("groupId")]
)
data class ClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val memo: String? = null,
    val groupId: Long? = null,
    val createdAt: Long,
    val email: String? = null,
    val phone: String? = null,
    /** ホーム/一覧の「ToDo」ボードでこのクライアントを一時的に伏せる期限(epoch millis)。null で通常表示。 */
    val followBoardSnoozedUntil: Long? = null,
    /** 流入経路(紹介 / Web検索 / SNS / イベント 等、自由入力も可)。任意。 */
    val leadSource: String? = null,
    /** 紹介元(紹介者の名前・関係先)。任意。 */
    val referredBy: String? = null
)
