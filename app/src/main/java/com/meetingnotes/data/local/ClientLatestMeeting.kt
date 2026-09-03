package com.meetingnotes.data.local

/** `MeetingDao.observeLatestMeetingPerClient` の射影結果。エンティティではなく読み取り専用のPOJO。 */
data class ClientLatestMeeting(
    val clientId: Long,
    val lastRecordedAt: Long,
    val nextMeetingDate: String?
)
