package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** `ScheduleDao` の一覧表示用射影(クライアント名付き)。 */
data class ScheduleWithClient(
    val id: Long,
    val clientId: Long,
    val clientName: String,
    val sourceMeetingId: Long?,
    val startAtMillis: Long,
    val hasTime: Boolean,
    val title: String,
    val note: String,
    val participants: String,
    val phase: String?
)

@Dao
interface ScheduleDao {

    @Query(
        """
        SELECT s.id AS id, s.clientId AS clientId, c.name AS clientName,
               s.sourceMeetingId AS sourceMeetingId, s.startAtMillis AS startAtMillis,
               s.hasTime AS hasTime, s.title AS title, s.note AS note,
               s.participants AS participants, s.phase AS phase
        FROM schedules s
        INNER JOIN clients c ON c.id = s.clientId
        ORDER BY s.startAtMillis ASC
        """
    )
    fun observeAll(): Flow<List<ScheduleWithClient>>

    @Query("SELECT * FROM schedules WHERE clientId = :clientId ORDER BY startAtMillis ASC")
    fun observeByClient(clientId: Long): Flow<List<ScheduleEntity>>

    /** リマインド Worker 用(Flow でなく suspend、指定範囲内)。 */
    @Query(
        """
        SELECT s.id AS id, s.clientId AS clientId, c.name AS clientName,
               s.sourceMeetingId AS sourceMeetingId, s.startAtMillis AS startAtMillis,
               s.hasTime AS hasTime, s.title AS title, s.note AS note,
               s.participants AS participants, s.phase AS phase
        FROM schedules s
        INNER JOIN clients c ON c.id = s.clientId
        WHERE s.startAtMillis BETWEEN :fromMillis AND :toMillis
        """
    )
    suspend fun getBetween(fromMillis: Long, toMillis: Long): List<ScheduleWithClient>

    @Query("SELECT * FROM schedules WHERE sourceMeetingId = :meetingId LIMIT 1")
    suspend fun getBySourceMeeting(meetingId: Long): ScheduleEntity?

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getById(id: Long): ScheduleEntity?

    @Insert
    suspend fun insert(schedule: ScheduleEntity): Long

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM schedules WHERE sourceMeetingId = :meetingId")
    suspend fun deleteBySourceMeeting(meetingId: Long)
}
