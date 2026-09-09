package com.meetingnotes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * クライアントの下の「プロジェクト」(任意)。1クライアントに複数持てる。
 * 商談は `meetings.projectId`(FK なしの単純カラム)で任意に紐付ける。
 * プロジェクト削除時は `MeetingRepository.deleteClientProject` が先に該当商談の projectId を null にする。
 */
@Entity(
    tableName = "client_projects",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("clientId")]
)
data class ClientProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val name: String,
    val createdAt: Long,
    /** 案件の状態(`DealPhase.wireValue`)。未設定は null。`WON` になると `wonAt` を記録する。 */
    val phase: String? = null,
    /** 金額の通貨コード。"JPY" / "USD"。 */
    val currency: String = "JPY",
    /** 見積額(通貨の主単位・小数なし。ドルもセントは扱わない)。 */
    val estimatedAmount: Long? = null,
    /** 成約額。 */
    val wonAmount: Long? = null,
    /** 成約日(epoch millis)。フェーズを成約にすると自動、フォームで手動変更も可。 */
    val wonAt: Long? = null,
    /** 失注理由(自由記述 or プリセット文言)。`phase == LOST` のときのみ意味を持つ。 */
    val lostReason: String? = null,
    /** 想定クローズ日(受注見込み日、epoch millis)。任意。 */
    val expectedCloseAt: Long? = null,
    /** 受注確度(%、0..100)。未入力(null)なら `DealPhase.defaultProbability` を使う。 */
    val probability: Int? = null,
    /** フェーズが最後に変わった時刻(epoch millis)。よどみ検知に使う。作成時は createdAt。 */
    val phaseChangedAt: Long? = null
)
