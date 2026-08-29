package com.meetingnotes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "client_groups")
data class ClientGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)
