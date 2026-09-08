package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientProjectDao {
    @Query("SELECT * FROM client_projects WHERE clientId = :clientId ORDER BY createdAt ASC")
    fun observeByClient(clientId: Long): Flow<List<ClientProjectEntity>>

    @Insert
    suspend fun insert(project: ClientProjectEntity): Long

    @Query("UPDATE client_projects SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM client_projects WHERE id = :id")
    suspend fun deleteById(id: Long)
}
