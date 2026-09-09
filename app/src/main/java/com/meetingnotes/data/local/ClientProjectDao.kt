package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientProjectDao {
    @Query("SELECT * FROM client_projects WHERE clientId = :clientId ORDER BY createdAt ASC")
    fun observeByClient(clientId: Long): Flow<List<ClientProjectEntity>>

    @Query("SELECT * FROM client_projects")
    fun observeAll(): Flow<List<ClientProjectEntity>>

    @Query("SELECT * FROM client_projects WHERE id = :id")
    suspend fun getById(id: Long): ClientProjectEntity?

    @Insert
    suspend fun insert(project: ClientProjectEntity): Long

    @Update
    suspend fun update(project: ClientProjectEntity)

    @Query("DELETE FROM client_projects WHERE id = :id")
    suspend fun deleteById(id: Long)
}
