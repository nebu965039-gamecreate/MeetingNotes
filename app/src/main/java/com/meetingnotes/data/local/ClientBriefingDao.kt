package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientBriefingDao {

    @Query("SELECT * FROM client_briefing WHERE clientId = :clientId")
    fun observe(clientId: Long): Flow<ClientBriefingEntity?>

    @Query("SELECT * FROM client_briefing WHERE clientId = :clientId")
    suspend fun get(clientId: Long): ClientBriefingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ClientBriefingEntity)
}
