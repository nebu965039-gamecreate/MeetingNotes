package com.meetingnotes.data

import com.meetingnotes.data.local.ClientDao
import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientLatestMeeting
import com.meetingnotes.data.local.ClientGroupDao
import com.meetingnotes.data.local.ClientGroupEntity
import com.meetingnotes.data.local.FolderDao
import com.meetingnotes.data.local.FolderEntity
import com.meetingnotes.data.local.MeetingDao
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoDao
import com.meetingnotes.data.local.TodoEntity
import com.meetingnotes.data.local.UserCreditsDao
import com.meetingnotes.data.local.UserCreditsEntity
import com.meetingnotes.data.model.MeetingSummary
import kotlinx.coroutines.flow.Flow

class MeetingRepository(
    private val clientDao: ClientDao,
    private val meetingDao: MeetingDao,
    private val todoDao: TodoDao,
    private val userCreditsDao: UserCreditsDao,
    private val folderDao: FolderDao,
    private val clientGroupDao: ClientGroupDao
) {
    fun observeClients(): Flow<List<ClientEntity>> = clientDao.observeAll()

    suspend fun addClient(name: String): Long =
        clientDao.insert(ClientEntity(name = name, createdAt = System.currentTimeMillis()))

    suspend fun getClient(clientId: Long): ClientEntity? = clientDao.getById(clientId)

    fun observeClient(clientId: Long): Flow<ClientEntity?> = clientDao.observeById(clientId)

    suspend fun renameClient(clientId: Long, name: String) = clientDao.rename(clientId, name)

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

    fun observeTodos(meetingId: Long): Flow<List<TodoEntity>> = todoDao.observeByMeeting(meetingId)

    fun observeTodosByClient(clientId: Long): Flow<List<TodoEntity>> = todoDao.observeByClient(clientId)

    suspend fun setTodoDone(todoId: Long, isDone: Boolean) = todoDao.setDone(todoId, isDone)

    fun observeFolders(clientId: Long): Flow<List<FolderEntity>> = folderDao.observeByClient(clientId)

    suspend fun addFolder(clientId: Long, name: String): Long =
        folderDao.insert(FolderEntity(clientId = clientId, name = name, createdAt = System.currentTimeMillis()))

    suspend fun renameFolder(folderId: Long, name: String) = folderDao.rename(folderId, name)

    suspend fun deleteFolder(folderId: Long) = folderDao.deleteById(folderId)

    suspend fun saveMeeting(
        clientId: Long,
        title: String,
        transcript: String,
        summary: MeetingSummary,
        recordedAt: Long = System.currentTimeMillis(),
        endedAt: Long? = null
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
                dealPhase = summary.dealPhase?.wireValue
            )
        )
        if (summary.todos.isNotEmpty()) {
            todoDao.insertAll(
                summary.todos.map {
                    TodoEntity(
                        meetingId = meetingId,
                        task = it.task,
                        assignee = it.assignee,
                        deadline = it.deadline
                    )
                }
            )
        }
        return meetingId
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
            val reset = existing.copy(balance = CreditPolicy.MONTHLY_FREE_CREDITS, lastResetYearMonth = currentMonth)
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

    /** クレジットを1付与する(リワード広告視聴時・要約失敗時の返却)。 */
    suspend fun grantCredit(deviceIdHash: String) {
        val current = getOrInitCredits(deviceIdHash)
        userCreditsDao.update(current.copy(balance = current.balance + 1))
    }
}
