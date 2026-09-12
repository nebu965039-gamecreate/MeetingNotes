package com.meetingnotes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todos",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("meetingId"), Index("clientId")]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * 由来の商談。要約から起票された ToDo は商談 id を持つ。
     * ユーザーが手動で追加した ToDo は null(商談に紐付かない・クライアント直下)。
     */
    val meetingId: Long? = null,
    /** 所属クライアント。手動追加・要約起票のどちらも必須。 */
    val clientId: Long = 0,
    val task: String,
    val assignee: String = "自分",
    /** AI が抽出した期限の原文(例: "金曜日まで")。手動追加では空。表示用。 */
    val deadline: String = "",
    /** [deadline] を解決した ISO 日付(yyyy-MM-dd)。解決できなければ null。ホーム・通知・カレンダーで使う。 */
    val dueDate: String? = null,
    val isDone: Boolean = false,
    /**
     * 要約完了時にアプリが自動起票する「お礼・フォローアップのメールを送る」ToDo なら true。
     * このフラグの ToDo を完了/未完了にすると、対応する商談の `meetings.followedUpAt` が同期され、
     * 下部ナビ ToDo タブの「完了」に反映される。
     */
    val isFollowupEmail: Boolean = false,
    /**
     * この ToDo についてユーザーが指定した通知予約日時(epoch millis)。null = 予約なし。
     * `TodoNotificationScheduler` が WorkManager の一回限りジョブとしてこの日時に端末通知を送る。
     * (2026-09-16、v29。旧 `snoozedUntil`〈v28〉をリネーム — 「スヌーズ(一覧から一時的に隠す)」は
     * 「気づきにくい」というフィードバックにより廃止し、ToDo は常に一覧・件数に含めたまま
     * 「指定日時に通知する」方式に置き換えた)。通知が発火すると `null` に戻る。
     */
    val notifyAt: Long? = null
)
