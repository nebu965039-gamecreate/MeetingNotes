package com.meetingnotes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_credits")
data class UserCreditsEntity(
    @PrimaryKey val deviceIdHash: String,
    val balance: Int,
    val lastResetYearMonth: String
)
