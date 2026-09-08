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

    @Query("SELECT * FROM meetings WHERE clientId = :clientId ORDER BY recordedAt ASC")
    suspend fun getByClientChrono(clientId: Long): List<MeetingEntity>

    /** クライアントごとの最新商談(フォローボード・予定カレンダー用の軽量射影)。 */
    @Query(
        """
        SELECT m.clientId AS clientId, m.id AS meetingId, m.recordedAt AS lastRecordedAt,
               m.nextMeetingDate AS nextMeetingDate, m.dealPhase AS dealPhase, m.phaseOverride AS phaseOverride,
               m.followedUpAt AS followedUpAt
        FROM meetings m
        INNER JOIN (SELECT clientId, MAX(recordedAt) AS maxAt FROM meetings GROUP BY clientId) latest
          ON m.clientId = latest.clientId AND m.recordedAt = latest.maxAt
        """
    )
    fun observeLatestMeetingPerClient(): Flow<List<ClientLatestMeeting>>

    /** クライアントごとの最新商談で、次回打ち合わせが設定されているもの(リマインド用)。 */
    @Query(
        """
        SELECT m.id AS meetingId, m.clientId AS clientId, c.name AS clientName,
               m.nextMeetingDate AS nextMeetingDate
        FROM meetings m
        INNER JOIN clients c ON c.id = m.clientId
        INNER JOIN (SELECT clientId, MAX(recordedAt) AS maxAt FROM meetings GROUP BY clientId) latest
          ON m.clientId = latest.clientId AND m.recordedAt = latest.maxAt
        WHERE m.nextMeetingDate IS NOT NULL AND m.nextMeetingDate != ''
        """
    )
    suspend fun getNextMeetingCandidates(): List<NextMeetingCandidate>

    /** 指定時刻以降に録音した商談の件数(ホームのダッシュボード「今月の商談」)。 */
    @Query("SELECT COUNT(*) FROM meetings WHERE recordedAt >= :since")
    fun observeCountRecordedSince(since: Long): Flow<Int>

    @Query("UPDATE meetings SET phaseOverride = :phase WHERE id = :meetingId")
    suspend fun updatePhaseOverride(meetingId: Long, phase: String?)

    @Query("UPDATE meetings SET nextMeetingDate = :date, nextMeetingOriginalText = :originalText WHERE id = :meetingId")
    suspend fun updateNextMeeting(meetingId: Long, date: String?, originalText: String?)

    @Query("SELECT * FROM meetings WHERE id = :meetingId")
    fun observeById(meetingId: Long): Flow<MeetingEntity?>

    @Query("DELETE FROM meetings WHERE id = :meetingId")
    suspend fun deleteById(meetingId: Long)

    @Query("UPDATE meetings SET folderId = :folderId WHERE id = :meetingId")
    suspend fun updateFolder(meetingId: Long, folderId: Long?)

    @Query("UPDATE meetings SET title = :title WHERE id = :meetingId")
    suspend fun updateTitle(meetingId: Long, title: String)

    @Query("UPDATE meetings SET followedUpAt = :at WHERE id = :meetingId")
    suspend fun updateFollowedUpAt(meetingId: Long, at: Long?)

    @Query("UPDATE meetings SET followupDraft = :draft WHERE id = :meetingId")
    suspend fun updateFollowupDraft(meetingId: Long, draft: String)

    /** メールフォロー済みの商談一覧(新しくフォローした順)。 */
    @Query(
        """
        SELECT m.id AS meetingId, m.clientId AS clientId, c.name AS clientName,
               m.title AS title, m.recordedAt AS recordedAt, m.followedUpAt AS followedUpAt,
               m.dealPhase AS dealPhase, m.phaseOverride AS phaseOverride
        FROM meetings m
        INNER JOIN clients c ON c.id = m.clientId
        WHERE m.followedUpAt IS NOT NULL
        ORDER BY m.followedUpAt DESC
        """
    )
    fun observeFollowedUpMeetings(): Flow<List<FollowedUpMeeting>>
}
