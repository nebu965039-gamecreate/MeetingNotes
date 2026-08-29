package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientGroupDao {
    @Insert
    suspend fun insert(group: ClientGroupEntity): Long

    @Query("SELECT * FROM client_groups ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ClientGroupEntity>>

    @Query("UPDATE client_groups SET name = :name WHERE id = :groupId")
    suspend fun rename(groupId: Long, name: String)

    @Query("DELETE FROM client_groups WHERE id = :groupId")
    suspend fun deleteById(groupId: Long)
}
