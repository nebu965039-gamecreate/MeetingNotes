package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Insert
    suspend fun insert(client: ClientEntity): Long

    @Query("SELECT * FROM clients ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :clientId")
    suspend fun getById(clientId: Long): ClientEntity?

    @Query("SELECT * FROM clients WHERE id = :clientId")
    fun observeById(clientId: Long): Flow<ClientEntity?>

    @Query("UPDATE clients SET name = :name WHERE id = :clientId")
    suspend fun rename(clientId: Long, name: String)

    @Query("UPDATE clients SET groupId = :groupId WHERE id = :clientId")
    suspend fun updateGroup(clientId: Long, groupId: Long?)

    @Query("DELETE FROM clients WHERE id = :clientId")
    suspend fun deleteById(clientId: Long)
}
