package com.meetingnotes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meetings",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("clientId"), Index("folderId")]
)
data class MeetingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val folderId: Long? = null,
    val title: String,
    val recordedAt: Long,
    val endedAt: Long? = null,
    val transcript: String,
    val summary: String,
    val decisions: List<String>,
    val concerns: List<String>,
    val nextMeetingDate: String?,
    val nextMeetingOriginalText: String?,
    /** AI が推定した商談フェーズ(`DealPhase.wireValue`)。 */
    val dealPhase: String? = null,
    /** ユーザーが上書きしたフェーズ。表示は phaseOverride ?: dealPhase。 */
    val phaseOverride: String? = null,
    /**
     * この商談についてメールでのフォローアップを済ませた時刻(epoch millis)。
     * null のあいだは「要フォロー」に「メールでフォロー」として出続ける(F1)。
     */
    val followedUpAt: Long? = null,
    /** 要約時に一緒に生成したフォローアップ文面の下書き(F5)。旧データは null。 */
    val followupDraft: String? = null,
    /** 実施形態(`MeetingType.wireValue`: "in_person" / "remote")。旧データは null。 */
    val meetingType: String? = null,
    /** クライアント配下の任意のプロジェクト(`client_projects.id`)。未設定は null。FK は張らず repository で後始末。 */
    val projectId: Long? = null
)
