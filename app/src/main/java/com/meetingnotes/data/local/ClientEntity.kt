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
    val createdAt: Long
)
