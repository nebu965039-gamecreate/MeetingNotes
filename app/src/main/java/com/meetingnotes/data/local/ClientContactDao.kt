package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientContactDao {
    @Query("SELECT * FROM client_contacts WHERE clientId = :clientId ORDER BY createdAt ASC")
    fun observeByClient(clientId: Long): Flow<List<ClientContactEntity>>

    @Insert
    suspend fun insert(contact: ClientContactEntity): Long

    @Query("UPDATE client_contacts SET name = :name, note = :note WHERE id = :id")
    suspend fun update(id: Long, name: String, note: String?)

    @Query("DELETE FROM client_contacts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
