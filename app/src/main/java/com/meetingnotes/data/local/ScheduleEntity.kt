package com.meetingnotes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 予定(打ち合わせ)。1クライアントに複数持てる。
 *
 * - AI が要約から拾った「次回打ち合わせ」も、この行として作られる(`sourceMeetingId` に商談ID)。
 *   商談詳細で日程を変えると連動して更新される。
 * - `sourceMeetingId == null` は 予定表 で手動追加したもの。上書きされず独立して並ぶ。
 */
@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("clientId"), Index("sourceMeetingId")]
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    /** この予定が特定商談の「次回打ち合わせ」由来なら、その商談ID。手動追加は null。 */
    val sourceMeetingId: Long? = null,
    /** 開始日時(epoch millis)。終日なら当日 00:00。 */
    val startAtMillis: Long,
    /** 時刻まで指定しているか(false = 終日扱い)。 */
    val hasTime: Boolean = false,
    val title: String = "打ち合わせ",
    val note: String = "",
    val participants: String = "",
    /** 進捗ラベル(`DealPhase.wireValue`)。未設定は null。 */
    val phase: String? = null,
    val createdAt: Long
)
