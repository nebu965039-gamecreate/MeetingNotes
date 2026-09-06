package com.meetingnotes.data.local

/** `MeetingDao.getNextMeetingCandidates` の射影結果(リマインド用)。 */
data class NextMeetingCandidate(
    val meetingId: Long,
    val clientId: Long,
    val clientName: String,
    val nextMeetingDate: String
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
