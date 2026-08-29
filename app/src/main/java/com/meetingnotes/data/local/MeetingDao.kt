package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Insert
    suspend fun insert(meeting: MeetingEntity): Long

    @Query("SELECT * FROM meetings WHERE clientId = :clientId ORDER BY recordedAt DESC")
    fun observeByClient(clientId: Long): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE id = :meetingId")
    fun observeById(meetingId: Long): Flow<MeetingEntity?>

    @Query("DELETE FROM meetings WHERE id = :meetingId")
    suspend fun deleteById(meetingId: Long)

    @Query("UPDATE meetings SET folderId = :folderId WHERE id = :meetingId")
    suspend fun updateFolder(meetingId: Long, folderId: Long?)

    @Query("UPDATE meetings SET title = :title WHERE id = :meetingId")
    suspend fun updateTitle(meetingId: Long, title: String)
}
