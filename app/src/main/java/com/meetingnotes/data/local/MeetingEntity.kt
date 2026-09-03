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
    val phaseOverride: String? = null
)
