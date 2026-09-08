package com.meetingnotes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** クライアントの担当者(先方窓口)。1クライアントに複数追加できる。 */
@Entity(
    tableName = "client_contacts",
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
data class ClientContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val name: String,
    /** 役職・部署・連絡先などの自由メモ。 */
    val note: String? = null,
    val createdAt: Long
)
