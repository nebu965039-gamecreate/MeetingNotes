package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCreditsDao {
    @Query("SELECT * FROM user_credits WHERE deviceIdHash = :hash")
    suspend fun getByHash(hash: String): UserCreditsEntity?

    @Query("SELECT * FROM user_credits WHERE deviceIdHash = :hash")
    fun observeByHash(hash: String): Flow<UserCreditsEntity?>

    @Insert
    suspend fun insert(entity: UserCreditsEntity)

    @Update
    suspend fun update(entity: UserCreditsEntity)
}
