package com.meetingnotes.data.local

/** `MeetingDao.getNextMeetingCandidates` の射影結果(リマインド用)。 */
data class NextMeetingCandidate(
    val meetingId: Long,
    val clientId: Long,
    val clientName: String,
    val nextMeetingDate: String
)

/** `MeetingDao.observeFollowedUpMeetings` の射影結果(フォロー済み一覧)。 */
data class FollowedUpMeeting(
    val meetingId: Long,
    val clientId: Long,
    val clientName: String,
    val title: String,
    val recordedAt: Long,
    val followedUpAt: Long,
    val dealPhase: String? = null,
    val phaseOverride: String? = null
)

/** `MeetingDao.observeLatestMeetingPerClient` の射影結果。エンティティではなく読み取り専用のPOJO。 */
data class ClientLatestMeeting(
    val clientId: Long,
    val meetingId: Long,
    val lastRecordedAt: Long,
    val nextMeetingDate: String?,
    val dealPhase: String? = null,
    val phaseOverride: String? = null,
    val followedUpAt: Long? = null
)
