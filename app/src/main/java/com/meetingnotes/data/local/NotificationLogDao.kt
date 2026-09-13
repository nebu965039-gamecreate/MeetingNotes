package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationLogDao {

    @Query("SELECT * FROM notification_log ORDER BY firedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = MAX_STORED): Flow<List<NotificationLogEntity>>

    @Query("SELECT COUNT(*) FROM notification_log WHERE meetingId = :meetingId AND scheduledFor = :scheduledFor")
    suspend fun countFor(meetingId: Long, scheduledFor: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: NotificationLogEntity)

    @Query("DELETE FROM notification_log WHERE firedAt < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)

    /** 新しい順に [limit] 件だけ残し、それより古い行を削除する(保存上限)。 */
    @Query(
        """
        DELETE FROM notification_log WHERE id NOT IN (
            SELECT id FROM notification_log ORDER BY firedAt DESC LIMIT :limit
        )
        """
    )
    suspend fun trimToLimit(limit: Int = MAX_STORED)

    companion object {
        /** 通知履歴の保存上限件数。 */
        const val MAX_STORED = 99
    }
}
