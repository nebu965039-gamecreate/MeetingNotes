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

    /** クライアントごとの最新商談(フォローボード用の軽量射影)。 */
    @Query(
        """
        SELECT m.clientId AS clientId, m.recordedAt AS lastRecordedAt, m.nextMeetingDate AS nextMeetingDate,
               m.dealPhase AS dealPhase, m.phaseOverride AS phaseOverride
        FROM meetings m
        INNER JOIN (SELECT clientId, MAX(recordedAt) AS maxAt FROM meetings GROUP BY clientId) latest
          ON m.clientId = latest.clientId AND m.recordedAt = latest.maxAt
        """
    )
    fun observeLatestMeetingPerClient(): Flow<List<ClientLatestMeeting>>

    @Query("UPDATE meetings SET phaseOverride = :phase WHERE id = :meetingId")
    suspend fun updatePhaseOverride(meetingId: Long, phase: String?)

    @Query("SELECT * FROM meetings WHERE id = :meetingId")
    fun observeById(meetingId: Long): Flow<MeetingEntity?>

    @Query("DELETE FROM meetings WHERE id = :meetingId")
    suspend fun deleteById(meetingId: Long)

    @Query("UPDATE meetings SET folderId = :folderId WHERE id = :meetingId")
    suspend fun updateFolder(meetingId: Long, folderId: Long?)

    @Query("UPDATE meetings SET title = :title WHERE id = :meetingId")
    suspend fun updateTitle(meetingId: Long, title: String)
}
