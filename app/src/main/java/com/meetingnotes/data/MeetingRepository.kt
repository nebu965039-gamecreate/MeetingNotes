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
    private val clientProjectDao: com.meetingnotes.data.local.ClientProjectDao,
    private val scheduleDao: com.meetingnotes.data.local.ScheduleDao
) {
    fun observeClients(): Flow<List<ClientEntity>> = clientDao.observeAll()

    suspend fun addClient(name: String, groupId: Long? = null): Long =
        clientDao.insert(ClientEntity(name = name, groupId = groupId, createdAt = System.currentTimeMillis()))

    suspend fun getClient(clientId: Long): ClientEntity? = clientDao.getById(clientId)

    fun observeClient(clientId: Long): Flow<ClientEntity?> = clientDao.observeById(clientId)

    suspend fun renameClient(clientId: Long, name: String) = clientDao.rename(clientId, name)

    /** ToDo ボードでこのクライアントを [untilMillis] まで一時的に伏せる(スヌーズ)。null で解除。 */
    suspend fun setClientFollowSnooze(clientId: Long, untilMillis: Long?) =
        clientDao.updateFollowBoardSnooze(clientId, untilMillis)

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

    /**
     * 商談の「次回打ち合わせ」を設定/解除する。連動して `schedules` の該当行(`sourceMeetingId == meetingId`)も
     * upsert / 削除する。
     */
    suspend fun setNextMeeting(meetingId: Long, dateIso: String?, originalText: String? = null) {
        meetingDao.updateNextMeeting(meetingId, dateIso, originalText)
        syncNextMeetingSchedule(meetingId, dateIso)
    }

    private suspend fun syncNextMeetingSchedule(meetingId: Long, dateIso: String?) {
        val parsed = dateIso?.let { com.meetingnotes.data.model.NextMeetingTime.parse(it) }
        if (parsed == null) {
            scheduleDao.deleteBySourceMeeting(meetingId)
            return
        }
        val meeting = meetingDao.getById(meetingId) ?: return
        val millis = com.meetingnotes.data.model.NextMeetingTime.toMillis(parsed.start)
        val phase = meeting.phaseOverride ?: meeting.dealPhase
        val existing = scheduleDao.getBySourceMeeting(meetingId)
        if (existing == null) {
            scheduleDao.insert(
                com.meetingnotes.data.local.ScheduleEntity(
                    clientId = meeting.clientId,
                    sourceMeetingId = meetingId,
                    startAtMillis = millis,
                    hasTime = !parsed.allDay,
                    title = "次回打ち合わせ",
                    phase = phase,
                    createdAt = System.currentTimeMillis()
                )
            )
        } else {
            scheduleDao.update(
                existing.copy(startAtMillis = millis, hasTime = !parsed.allDay, phase = phase)
            )
        }
    }

    // --- 予定(schedules) ---

    fun observeSchedules(): Flow<List<com.meetingnotes.data.local.ScheduleWithClient>> =
        scheduleDao.observeAll()

    fun observeClientSchedules(clientId: Long): Flow<List<com.meetingnotes.data.local.ScheduleEntity>> =
        scheduleDao.observeByClient(clientId)

    suspend fun addSchedule(
        clientId: Long,
        startAtMillis: Long,
        hasTime: Boolean,
        title: String,
        note: String,
        participants: String,
        phase: com.meetingnotes.data.model.DealPhase?,
        meetingUrl: String? = null,
        location: String? = null
    ): Long = scheduleDao.insert(
        com.meetingnotes.data.local.ScheduleEntity(
            clientId = clientId,
            sourceMeetingId = null,
            startAtMillis = startAtMillis,
            hasTime = hasTime,
            title = title.trim().ifBlank { "打ち合わせ" },
            note = note.trim(),
            participants = participants.trim(),
            phase = phase?.wireValue,
            meetingUrl = meetingUrl?.trim()?.ifBlank { null },
            location = location?.trim()?.ifBlank { null },
            createdAt = System.currentTimeMillis()
        )
    )

    suspend fun updateSchedule(
        id: Long,
        startAtMillis: Long,
        hasTime: Boolean,
        title: String,
        note: String,
        participants: String,
        phase: com.meetingnotes.data.model.DealPhase?,
        meetingUrl: String? = null,
        location: String? = null
    ) {
        val row = scheduleDao.getById(id) ?: return
        scheduleDao.update(
            row.copy(
                startAtMillis = startAtMillis,
                hasTime = hasTime,
                title = title.trim().ifBlank { "打ち合わせ" },
                note = note.trim(),
                participants = participants.trim(),
                phase = phase?.wireValue,
                meetingUrl = meetingUrl?.trim()?.ifBlank { null },
                location = location?.trim()?.ifBlank { null }
            )
        )
        // AI 由来なら商談側の nextMeetingDate も合わせる。
        if (row.sourceMeetingId != null) {
            val iso = com.meetingnotes.data.model.NextMeetingTime.toIso(
                com.meetingnotes.data.model.NextMeetingTime.toLocalDateTime(startAtMillis),
                includeTime = hasTime
            )
            meetingDao.updateNextMeeting(row.sourceMeetingId, iso, null)
        }
    }

    /** 予定を削除。AI 由来(sourceMeetingId あり)なら商談の nextMeetingDate も消す。 */
    suspend fun deleteSchedule(id: Long) {
        val row = scheduleDao.getById(id)
        scheduleDao.deleteById(id)
        if (row?.sourceMeetingId != null) {
            meetingDao.updateNextMeeting(row.sourceMeetingId, null, null)
        }
    }

    suspend fun getSchedulesForReminder(fromMillis: Long, toMillis: Long) =
        scheduleDao.getBetween(fromMillis, toMillis)

    /** 起動時に呼ぶ。既存 `meetings.nextMeetingDate` を `schedules` に取り込む(冪等)。 */
    suspend fun backfillSchedules() {
        meetingDao.getAll().forEach { m ->
            val parsed = com.meetingnotes.data.model.NextMeetingTime.parse(m.nextMeetingDate) ?: return@forEach
            if (scheduleDao.getBySourceMeeting(m.id) != null) return@forEach
            scheduleDao.insert(
                com.meetingnotes.data.local.ScheduleEntity(
                    clientId = m.clientId,
                    sourceMeetingId = m.id,
                    startAtMillis = com.meetingnotes.data.model.NextMeetingTime.toMillis(parsed.start),
                    hasTime = !parsed.allDay,
                    title = "次回打ち合わせ",
                    phase = m.phaseOverride ?: m.dealPhase,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

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
            todo.meetingId?.let {
                meetingDao.updateFollowedUpAt(it, if (isDone) System.currentTimeMillis() else null)
            }
        }
    }

    /** クライアント直下に手動 ToDo を追加(商談に紐付かない)。期限は任意の ISO 日付。 */
    suspend fun addManualTodo(clientId: Long, task: String, dueDate: String?): Long {
        val trimmed = task.trim()
        if (trimmed.isEmpty()) return -1L
        return todoDao.insert(
            TodoEntity(
                meetingId = null,
                clientId = clientId,
                task = trimmed,
                assignee = "自分",
                deadline = "",
                dueDate = dueDate
            )
        )
    }

    /** ToDo の本文・期限を編集する(手動追加した ToDo 向け)。 */
    suspend fun updateTodoContent(todoId: Long, task: String, dueDate: String?) {
        val trimmed = task.trim()
        if (trimmed.isEmpty()) return
        todoDao.updateContent(todoId, trimmed, deadline = "", dueDate = dueDate)
    }

    /** ToDo を削除する。 */
    suspend fun deleteTodo(todoId: Long) = todoDao.deleteById(todoId)

    fun observeFolders(clientId: Long): Flow<List<FolderEntity>> = folderDao.observeByClient(clientId)

    suspend fun addFolder(clientId: Long, name: String): Long =
        folderDao.insert(FolderEntity(clientId = clientId, name = name, createdAt = System.currentTimeMillis()))

    suspend fun renameFolder(folderId: Long, name: String) = folderDao.rename(folderId, name)

    suspend fun deleteFolder(folderId: Long) = folderDao.deleteById(folderId)

    // --- クライアント配下のプロジェクト(任意) ---

    fun observeClientProjects(clientId: Long): Flow<List<com.meetingnotes.data.local.ClientProjectEntity>> =
        clientProjectDao.observeByClient(clientId)

    fun observeAllProjects(): Flow<List<com.meetingnotes.data.local.ClientProjectEntity>> =
        clientProjectDao.observeAll()

    suspend fun addClientProject(
        clientId: Long,
        name: String,
        phase: com.meetingnotes.data.model.DealPhase? = null,
        currency: String = "JPY",
        estimatedAmount: Long? = null,
        wonAmount: Long? = null,
        wonAt: Long? = null,
        lostReason: String? = null,
        expectedCloseAt: Long? = null,
        probability: Int? = null
    ): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        val active = phase?.isActive != false
        val now = System.currentTimeMillis()
        return clientProjectDao.insert(
            com.meetingnotes.data.local.ClientProjectEntity(
                clientId = clientId,
                name = trimmed,
                createdAt = now,
                phaseChangedAt = now,
                phase = phase?.wireValue,
                currency = currency,
                estimatedAmount = estimatedAmount,
                wonAmount = wonAmount,
                wonAt = if (phase == com.meetingnotes.data.model.DealPhase.WON) (wonAt ?: System.currentTimeMillis()) else wonAt,
                lostReason = if (phase == com.meetingnotes.data.model.DealPhase.LOST) lostReason?.trim()?.ifBlank { null } else null,
                expectedCloseAt = if (active) expectedCloseAt else null,
                probability = if (active) probability?.coerceIn(0, 100) else null
            )
        )
    }

    /**
     * 案件フォームからの更新。フェーズを成約にしたら `wonAt` を補完(手動指定があればそれを優先)。
     * `lostReason` は `phase == LOST` のときのみ保持する。
     */
    suspend fun updateClientProject(
        projectId: Long,
        name: String,
        phase: com.meetingnotes.data.model.DealPhase?,
        currency: String,
        estimatedAmount: Long?,
        wonAmount: Long?,
        wonAt: Long?,
        lostReason: String?,
        expectedCloseAt: Long?,
        probability: Int?
    ) {
        val current = clientProjectDao.getById(projectId) ?: return
        val wasWon = current.phase == com.meetingnotes.data.model.DealPhase.WON.wireValue
        val isWon = phase == com.meetingnotes.data.model.DealPhase.WON
        val resolvedWonAt = when {
            !isWon -> null
            wonAt != null -> wonAt
            wasWon -> current.wonAt ?: System.currentTimeMillis()
            else -> System.currentTimeMillis()
        }
        val active = phase?.isActive != false
        val phaseChanged = current.phase != phase?.wireValue
        clientProjectDao.update(
            current.copy(
                name = name.trim().ifBlank { current.name },
                phase = phase?.wireValue,
                currency = currency,
                estimatedAmount = estimatedAmount,
                wonAmount = wonAmount,
                wonAt = resolvedWonAt,
                lostReason = if (phase == com.meetingnotes.data.model.DealPhase.LOST) lostReason?.trim()?.ifBlank { null } else null,
                expectedCloseAt = if (active) expectedCloseAt else null,
                probability = if (active) probability?.coerceIn(0, 100) else null,
                phaseChangedAt = if (phaseChanged) System.currentTimeMillis()
                    else (current.phaseChangedAt ?: current.createdAt)
            )
        )
    }

    /** 案件のフェーズだけを変更する(パイプラインボード用)。変わったら `phaseChangedAt` を更新。 */
    suspend fun setClientProjectPhase(projectId: Long, phase: com.meetingnotes.data.model.DealPhase) {
        val current = clientProjectDao.getById(projectId) ?: return
        if (current.phase == phase.wireValue) return
        val isWon = phase == com.meetingnotes.data.model.DealPhase.WON
        val isLost = phase == com.meetingnotes.data.model.DealPhase.LOST
        clientProjectDao.update(
            current.copy(
                phase = phase.wireValue,
                phaseChangedAt = System.currentTimeMillis(),
                wonAt = if (isWon) (current.wonAt ?: System.currentTimeMillis()) else null,
                lostReason = if (isLost) current.lostReason else null,
                expectedCloseAt = if (phase.isActive) current.expectedCloseAt else null,
                probability = if (phase.isActive) current.probability else null
            )
        )
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
                clientId = clientId,
                task = it.task,
                assignee = it.assignee,
                deadline = it.deadline,
                dueDate = it.deadlineDate
                    ?: com.meetingnotes.data.model.TodoDueDate.parse(it.deadline)
            )
        } + followupEmailTodo(meetingId, clientId, recordedAt)
        todoDao.insertAll(todos)
        // AI が「次回打ち合わせ」を拾っていれば、予定(schedules)にも1件作る。
        summary.nextMeeting.date?.let { syncNextMeetingSchedule(meetingId, it) }
        return meetingId
    }

    /**
     * 要約完了時に必ず1件だけ自動起票する「お礼・フォローアップのメールを送る」ToDo。
     * 期限は録音日の翌日。完了/未完了は `meetings.followedUpAt` と同期される([setTodoDone])。
     */
    private fun followupEmailTodo(meetingId: Long, clientId: Long, recordedAt: Long): TodoEntity {
        val due = java.time.Instant.ofEpochMilli(recordedAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .plusDays(1)
        return TodoEntity(
            meetingId = meetingId,
            clientId = clientId,
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
