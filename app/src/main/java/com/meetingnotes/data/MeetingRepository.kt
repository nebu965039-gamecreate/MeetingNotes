package com.meetingnotes.data

import com.meetingnotes.data.local.ClientBriefingDao
import com.meetingnotes.data.local.ClientBriefingEntity
import com.meetingnotes.data.local.ClientDao
import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientLatestMeeting
import com.meetingnotes.data.local.ClientGroupDao
import com.meetingnotes.data.local.ClientGroupEntity
import com.meetingnotes.data.local.FolderDao
import com.meetingnotes.data.local.FolderEntity
import com.meetingnotes.data.local.MeetingDao
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.NextMeetingCandidate
import com.meetingnotes.data.local.NotificationLogDao
import com.meetingnotes.data.local.NotificationLogEntity
import com.meetingnotes.data.local.TodoDao
import com.meetingnotes.data.local.TodoEntity
import com.meetingnotes.data.local.UserCreditsDao
import com.meetingnotes.data.local.UserCreditsEntity
import com.meetingnotes.data.model.MeetingSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MeetingRepository(
    private val clientDao: ClientDao,
    private val meetingDao: MeetingDao,
    private val todoDao: TodoDao,
    private val userCreditsDao: UserCreditsDao,
    private val folderDao: FolderDao,
    private val clientGroupDao: ClientGroupDao,
    private val clientBriefingDao: ClientBriefingDao,
    private val notificationLogDao: NotificationLogDao,
    private val clientContactDao: com.meetingnotes.data.local.ClientContactDao,
    private val clientProjectDao: com.meetingnotes.data.local.ClientProjectDao
) {
    fun observeClients(): Flow<List<ClientEntity>> = clientDao.observeAll()

    suspend fun addClient(name: String, groupId: Long? = null): Long =
        clientDao.insert(ClientEntity(name = name, groupId = groupId, createdAt = System.currentTimeMillis()))

    suspend fun getClient(clientId: Long): ClientEntity? = clientDao.getById(clientId)

    fun observeClient(clientId: Long): Flow<ClientEntity?> = clientDao.observeById(clientId)

    suspend fun renameClient(clientId: Long, name: String) = clientDao.rename(clientId, name)

    suspend fun updateClientInfo(clientId: Long, name: String, email: String?, phone: String?, memo: String?) =
        clientDao.updateInfo(
            clientId, name.trim(),
            email?.trim()?.ifBlank { null },
            phone?.trim()?.ifBlank { null },
            memo?.trim()?.ifBlank { null }
        )

    fun observeClientContacts(clientId: Long): Flow<List<com.meetingnotes.data.local.ClientContactEntity>> =
        clientContactDao.observeByClient(clientId)

    private fun String?.cleaned() = this?.trim()?.ifBlank { null }

    suspend fun addClientContact(clientId: Long, name: String, note: String?, email: String?, phone: String?) {
        if (name.isBlank()) return
        clientContactDao.insert(
            com.meetingnotes.data.local.ClientContactEntity(
                clientId = clientId,
                name = name.trim(),
                note = note.cleaned(),
                email = email.cleaned(),
                phone = phone.cleaned(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateClientContact(id: Long, name: String, note: String?, email: String?, phone: String?) {
        if (name.isBlank()) return
        clientContactDao.update(id, name.trim(), note.cleaned(), email.cleaned(), phone.cleaned())
    }

    suspend fun deleteClientContact(id: Long) = clientContactDao.deleteById(id)

    suspend fun deleteClient(clientId: Long) = clientDao.deleteById(clientId)

    fun observeClientGroups(): Flow<List<ClientGroupEntity>> = clientGroupDao.observeAll()

    suspend fun addClientGroup(name: String): Long =
        clientGroupDao.insert(ClientGroupEntity(name = name, createdAt = System.currentTimeMillis()))

    suspend fun renameClientGroup(groupId: Long, name: String) = clientGroupDao.rename(groupId, name)

    suspend fun deleteClientGroup(groupId: Long) = clientGroupDao.deleteById(groupId)

    suspend fun moveClientToGroup(clientId: Long, groupId: Long?) = clientDao.updateGroup(clientId, groupId)

    fun observeMeetings(clientId: Long): Flow<List<MeetingEntity>> = meetingDao.observeByClient(clientId)

    fun observeLatestMeetingPerClient(): Flow<List<ClientLatestMeeting>> = meetingDao.observeLatestMeetingPerClient()

    fun observeMeeting(meetingId: Long): Flow<MeetingEntity?> = meetingDao.observeById(meetingId)

    suspend fun deleteMeeting(meetingId: Long) = meetingDao.deleteById(meetingId)

    suspend fun moveMeetingToFolder(meetingId: Long, folderId: Long?) = meetingDao.updateFolder(meetingId, folderId)

    suspend fun renameMeeting(meetingId: Long, title: String) = meetingDao.updateTitle(meetingId, title)

    suspend fun setMeetingPhaseOverride(meetingId: Long, phase: com.meetingnotes.data.model.DealPhase?) =
        meetingDao.updatePhaseOverride(meetingId, phase?.wireValue)

    suspend fun setNextMeeting(meetingId: Long, dateIso: String?, originalText: String? = null) =
        meetingDao.updateNextMeeting(meetingId, dateIso, originalText)

    /**
     * この商談のフォロー(お礼・確認メール)を「完了」にする(ホーム/ToDo一覧のボードから除外)。
     * 自動起票された「フォローアップメール」ToDo があればそれもチェック済みにして、
     * クライアントの ToDo リストと状態を揃える。
     */
    suspend fun markMeetingFollowedUp(meetingId: Long) {
        todoDao.followupEmailTodoId(meetingId)?.let { todoDao.setDone(it, true) }
        meetingDao.updateFollowedUpAt(meetingId, System.currentTimeMillis())
    }

    /** 完了を取り消して ToDo に戻す(フォローアップメール ToDo も未完了へ)。 */
    suspend fun clearMeetingFollowedUp(meetingId: Long) {
        todoDao.followupEmailTodoId(meetingId)?.let { todoDao.setDone(it, false) }
        meetingDao.updateFollowedUpAt(meetingId, null)
    }

    /** 要約時に付かなかった商談の、後追い生成したフォローアップ下書きを保存する(1回のみ想定)。 */
    suspend fun setMeetingFollowupDraft(meetingId: Long, draft: String) =
        meetingDao.updateFollowupDraft(meetingId, draft)

    fun observeFollowedUpMeetings(): Flow<List<com.meetingnotes.data.local.FollowedUpMeeting>> =
        meetingDao.observeFollowedUpMeetings()

    // --- F7: 予定・リマインド ---

    suspend fun getNextMeetingCandidates(): List<NextMeetingCandidate> = meetingDao.getNextMeetingCandidates()

    fun observeNotificationLog(): Flow<List<NotificationLogEntity>> = notificationLogDao.observeRecent()

    suspend fun notificationAlreadyFired(meetingId: Long, scheduledFor: String): Boolean =
        notificationLogDao.countFor(meetingId, scheduledFor) > 0

    suspend fun logNotification(entity: NotificationLogEntity) = notificationLogDao.insert(entity)

    suspend fun pruneNotificationLog(beforeMillis: Long) = notificationLogDao.deleteOlderThan(beforeMillis)

    fun observeTodos(meetingId: Long): Flow<List<TodoEntity>> = todoDao.observeByMeeting(meetingId)

    fun observeTodosByClient(clientId: Long): Flow<List<TodoEntity>> = todoDao.observeByClient(clientId)

    fun observeOpenTodosWithDueDate(): Flow<List<com.meetingnotes.data.local.OpenTodo>> =
        todoDao.observeOpenTodosWithDueDate()

    fun observeOpenTodoCountByClient(): Flow<Map<Long, Int>> =
        todoDao.observeOpenTodoCountByClient()
            .map { list -> list.associate { it.clientId to it.count } }

    /** 未完了 ToDo の総数(下部ナビのバッジ・ホームのダッシュボード)。 */
    fun observeOpenTodoTotal(): Flow<Int> = todoDao.observeOpenTodoTotal()

    /** 指定時刻以降に録音した商談の件数(ホームのダッシュボード)。 */
    fun observeMeetingCountSince(since: Long): Flow<Int> = meetingDao.observeCountRecordedSince(since)

    suspend fun getTodosDueOn(date: String): List<com.meetingnotes.data.local.OpenTodo> =
        todoDao.getTodosDueOn(date)

    // --- F2: 前回のおさらい(ブリーフィング)---

    fun observeBriefing(clientId: Long): Flow<ClientBriefingEntity?> = clientBriefingDao.observe(clientId)

    suspend fun getBriefing(clientId: Long): ClientBriefingEntity? = clientBriefingDao.get(clientId)

    suspend fun saveBriefing(clientId: Long, flowText: String, sourceMeetingCount: Int) =
        clientBriefingDao.upsert(
            ClientBriefingEntity(clientId, flowText, System.currentTimeMillis(), sourceMeetingCount)
        )

    suspend fun getMeetingsChrono(clientId: Long): List<MeetingEntity> =
        meetingDao.getByClientChrono(clientId)

    suspend fun setTodoDone(todoId: Long, isDone: Boolean) {
        todoDao.setDone(todoId, isDone)
        // 「フォローアップメール」ToDo のチェックは F1 ボードの完了状態(followedUpAt)と同期する。
        val todo = todoDao.getById(todoId)
        if (todo?.isFollowupEmail == true) {
            meetingDao.updateFollowedUpAt(todo.meetingId, if (isDone) System.currentTimeMillis() else null)
        }
    }

    fun observeFolders(clientId: Long): Flow<List<FolderEntity>> = folderDao.observeByClient(clientId)

    suspend fun addFolder(clientId: Long, name: String): Long =
        folderDao.insert(FolderEntity(clientId = clientId, name = name, createdAt = System.currentTimeMillis()))

    suspend fun renameFolder(folderId: Long, name: String) = folderDao.rename(folderId, name)

    suspend fun deleteFolder(folderId: Long) = folderDao.deleteById(folderId)

    // --- クライアント配下のプロジェクト(任意) ---

    fun observeClientProjects(clientId: Long): Flow<List<com.meetingnotes.data.local.ClientProjectEntity>> =
        clientProjectDao.observeByClient(clientId)

    suspend fun addClientProject(clientId: Long, name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        return clientProjectDao.insert(
            com.meetingnotes.data.local.ClientProjectEntity(
                clientId = clientId, name = trimmed, createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun renameClientProject(projectId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        clientProjectDao.rename(projectId, trimmed)
    }

    /** プロジェクトを削除。紐付いていた商談は projectId を null に戻す(商談自体は消さない)。 */
    suspend fun deleteClientProject(projectId: Long) {
        meetingDao.clearProject(projectId)
        clientProjectDao.deleteById(projectId)
    }

    suspend fun setMeetingProject(meetingId: Long, projectId: Long?) =
        meetingDao.updateProject(meetingId, projectId)

    suspend fun saveMeeting(
        clientId: Long,
        title: String,
        transcript: String,
        summary: MeetingSummary,
        recordedAt: Long = System.currentTimeMillis(),
        endedAt: Long? = null,
        meetingType: com.meetingnotes.data.model.MeetingType? = null
    ): Long {
        val meetingId = meetingDao.insert(
            MeetingEntity(
                clientId = clientId,
                title = title,
                recordedAt = recordedAt,
                endedAt = endedAt,
                transcript = transcript,
                summary = summary.summary,
                decisions = summary.decisions.map { it.content },
                concerns = summary.concerns.map { it.content },
                nextMeetingDate = summary.nextMeeting.date,
                nextMeetingOriginalText = summary.nextMeeting.originalText,
                dealPhase = summary.dealPhase?.wireValue,
                followupDraft = summary.followupDraft,
                meetingType = meetingType?.wireValue
            )
        )
        val todos = summary.todos.map {
            TodoEntity(
                meetingId = meetingId,
                task = it.task,
                assignee = it.assignee,
                deadline = it.deadline,
                dueDate = it.deadlineDate
                    ?: com.meetingnotes.data.model.TodoDueDate.parse(it.deadline)
            )
        } + followupEmailTodo(meetingId, recordedAt)
        todoDao.insertAll(todos)
        return meetingId
    }

    /**
     * 要約完了時に必ず1件だけ自動起票する「お礼・フォローアップのメールを送る」ToDo。
     * 期限は録音日の翌日。完了/未完了は `meetings.followedUpAt` と同期される([setTodoDone])。
     */
    private fun followupEmailTodo(meetingId: Long, recordedAt: Long): TodoEntity {
        val due = java.time.Instant.ofEpochMilli(recordedAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .plusDays(1)
        return TodoEntity(
            meetingId = meetingId,
            task = FOLLOWUP_EMAIL_TASK,
            assignee = FOLLOWUP_EMAIL_ASSIGNEE,
            deadline = "翌日",
            dueDate = due.toString(),
            isFollowupEmail = true
        )
    }

    companion object {
        /** 自動起票するフォローアップメール ToDo のタスク文言。 */
        const val FOLLOWUP_EMAIL_TASK = "お礼・フォローアップのメールを送る"
        const val FOLLOWUP_EMAIL_ASSIGNEE = "自分"
    }

    fun observeCredits(deviceIdHash: String): Flow<UserCreditsEntity?> =
        userCreditsDao.observeByHash(deviceIdHash)

    /** 残高が無ければ初回付与、月が変わっていればリセットして返す(仕様書7.1)。 */
    suspend fun getOrInitCredits(deviceIdHash: String): UserCreditsEntity {
        val currentMonth = CreditPolicy.currentYearMonth()
        val existing = userCreditsDao.getByHash(deviceIdHash)

        if (existing == null) {
            val fresh = UserCreditsEntity(
                deviceIdHash = deviceIdHash,
                balance = CreditPolicy.MONTHLY_FREE_CREDITS,
                lastResetYearMonth = currentMonth
            )
            userCreditsDao.insert(fresh)
            return fresh
        }

        if (CreditPolicy.shouldReset(currentMonth, existing.lastResetYearMonth)) {
            val reset = existing.copy(
                balance = CreditPolicy.MONTHLY_FREE_CREDITS,
                lastResetYearMonth = currentMonth,
                onlineTranscriptionsUsed = 0,
                onlineTranscriptionsBonus = 0
            )
            userCreditsDao.update(reset)
            return reset
        }

        return existing
    }

    /** クレジットを1消費する。残高が無ければ何もせずfalseを返す。 */
    suspend fun consumeCredit(deviceIdHash: String): Boolean {
        val current = getOrInitCredits(deviceIdHash)
        if (current.balance <= 0) return false
        userCreditsDao.update(current.copy(balance = current.balance - 1))
        return true
    }

    // --- リモート会議モード(サーバー文字起こし)の月間上限 ---

    /** その月に残っているリモート会議モードの回数。 */
    suspend fun remainingOnlineTranscriptions(deviceIdHash: String, isPro: Boolean): Int {
        val c = getOrInitCredits(deviceIdHash)
        val allowance = CreditPolicy.onlineTranscriptionAllowance(isPro, c.onlineTranscriptionsBonus)
        return (allowance - c.onlineTranscriptionsUsed).coerceAtLeast(0)
    }

    /** リモート会議モードを1回消費する。上限に達していれば false。 */
    suspend fun consumeOnlineTranscription(deviceIdHash: String, isPro: Boolean): Boolean {
        val c = getOrInitCredits(deviceIdHash)
        val allowance = CreditPolicy.onlineTranscriptionAllowance(isPro, c.onlineTranscriptionsBonus)
        if (c.onlineTranscriptionsUsed >= allowance) return false
        userCreditsDao.update(c.copy(onlineTranscriptionsUsed = c.onlineTranscriptionsUsed + 1))
        return true
    }

    /** 文字起こしに失敗したときの返却。 */
    suspend fun refundOnlineTranscription(deviceIdHash: String) {
        val c = getOrInitCredits(deviceIdHash)
        if (c.onlineTranscriptionsUsed > 0) {
            userCreditsDao.update(c.copy(onlineTranscriptionsUsed = c.onlineTranscriptionsUsed - 1))
        }
    }

    /** リワード広告視聴で無料ユーザーのリモート会議モードを1回追加(上限あり)。 */
    suspend fun grantOnlineTranscriptionBonus(deviceIdHash: String) {
        val c = getOrInitCredits(deviceIdHash)
        if (c.onlineTranscriptionsBonus < CreditPolicy.ONLINE_TRANSCRIPTION_FREE_BONUS_CAP) {
            userCreditsDao.update(c.copy(onlineTranscriptionsBonus = c.onlineTranscriptionsBonus + 1))
        }
    }

    /** クレジットを1付与する(リワード広告視聴時・要約失敗時の返却)。 */
    suspend fun grantCredit(deviceIdHash: String) {
        val current = getOrInitCredits(deviceIdHash)
        userCreditsDao.update(current.copy(balance = current.balance + 1))
    }
}
