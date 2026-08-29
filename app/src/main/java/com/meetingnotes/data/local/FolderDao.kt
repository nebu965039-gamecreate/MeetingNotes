package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("SELECT * FROM folders WHERE clientId = :clientId ORDER BY createdAt ASC")
    fun observeByClient(clientId: Long): Flow<List<FolderEntity>>

    @Query("UPDATE folders SET name = :name WHERE id = :folderId")
    suspend fun rename(folderId: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteById(folderId: Long)
}
