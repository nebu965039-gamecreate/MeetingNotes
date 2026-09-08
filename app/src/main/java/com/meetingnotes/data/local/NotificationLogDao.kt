package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationLogDao {

    @Query("SELECT * FROM notification_log ORDER BY firedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<NotificationLogEntity>>

    @Query("SELECT COUNT(*) FROM notification_log WHERE meetingId = :meetingId AND scheduledFor = :scheduledFor")
    suspend fun countFor(meetingId: Long, scheduledFor: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: NotificationLogEntity)

    @Query("DELETE FROM notification_log WHERE firedAt < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)
}
