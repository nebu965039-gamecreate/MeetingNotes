package com.meetingnotes.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeetingDatabaseTest {

    private lateinit var database: MeetingNotesDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MeetingNotesDatabase::class.java
        ).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertClientMeetingAndTodo_thenQueryByClient() = runBlocking {
        val clientId = database.clientDao().insert(
            ClientEntity(name = "テスト株式会社", createdAt = 1_000L)
        )

        val meetingId = database.meetingDao().insert(
            MeetingEntity(
                clientId = clientId,
                title = "初回商談",
                recordedAt = 2_000L,
                transcript = "文字起こし本文",
                summary = "契約条件について合意した。",
                decisions = listOf("月額プランで契約する"),
                concerns = listOf("導入コストが気になる"),
                nextMeetingDate = null,
                nextMeetingOriginalText = "また来週あたり"
            )
        )

        database.todoDao().insertAll(
            listOf(
                TodoEntity(meetingId = meetingId, task = "見積書を送付する", assignee = "山田", deadline = "2026-09-01")
            )
        )

        val meetings = database.meetingDao().observeByClient(clientId).first()
        assertEquals(1, meetings.size)
        assertEquals("契約条件について合意した。", meetings[0].summary)
        assertEquals(listOf("月額プランで契約する"), meetings[0].decisions)

        val todos = database.todoDao().observeByMeeting(meetingId).first()
        assertEquals(1, todos.size)
        assertEquals(false, todos[0].isDone)
    }

    @Test
    fun toggleTodoDone_persistsFlag() = runBlocking {
        val clientId = database.clientDao().insert(ClientEntity(name = "クライアントA", createdAt = 1_000L))
        val meetingId = database.meetingDao().insert(
            MeetingEntity(
                clientId = clientId,
                title = "商談",
                recordedAt = 2_000L,
                transcript = "本文",
                summary = "サマリー",
                decisions = emptyList(),
                concerns = emptyList(),
                nextMeetingDate = null,
                nextMeetingOriginalText = null
            )
        )
        database.todoDao().insertAll(
            listOf(TodoEntity(meetingId = meetingId, task = "タスク", assignee = "未定", deadline = "未定"))
        )
        val todoId = database.todoDao().observeByMeeting(meetingId).first().first().id

        database.todoDao().setDone(todoId, true)

        val updated = database.todoDao().observeByMeeting(meetingId).first().first()
        assertTrue(updated.isDone)
    }

    @Test
    fun deletingClient_cascadesToMeetingsAndTodos() = runBlocking {
        val clientId = database.clientDao().insert(ClientEntity(name = "削除対象", createdAt = 1_000L))
        val meetingId = database.meetingDao().insert(
            MeetingEntity(
                clientId = clientId,
                title = "商談",
                recordedAt = 2_000L,
                transcript = "本文",
                summary = "サマリー",
                decisions = emptyList(),
                concerns = emptyList(),
                nextMeetingDate = null,
                nextMeetingOriginalText = null
            )
        )
        database.todoDao().insertAll(
            listOf(TodoEntity(meetingId = meetingId, task = "タスク", assignee = "未定", deadline = "未定"))
        )

        database.clientDao().deleteById(clientId)

        val remainingMeetings = database.meetingDao().observeByClient(clientId).first()
        val remainingTodos = database.todoDao().observeByMeeting(meetingId).first()
        assertTrue(remainingMeetings.isEmpty())
        assertTrue(remainingTodos.isEmpty())
    }

    @Test
    fun renameClient_persistsNewName() = runBlocking {
        val clientId = database.clientDao().insert(ClientEntity(name = "旧名称", createdAt = 1_000L))

        database.clientDao().rename(clientId, "新名称")

        assertEquals("新名称", database.clientDao().getById(clientId)?.name)
    }

    @Test
    fun deleteMeeting_removesItFromClientList() = runBlocking {
        val clientId = database.clientDao().insert(ClientEntity(name = "クライアントB", createdAt = 1_000L))
        val meetingId = database.meetingDao().insert(
            MeetingEntity(
                clientId = clientId,
                title = "削除される商談",
                recordedAt = 2_000L,
                transcript = "本文",
                summary = "サマリー",
                decisions = emptyList(),
                concerns = emptyList(),
                nextMeetingDate = null,
                nextMeetingOriginalText = null
            )
        )

        database.meetingDao().deleteById(meetingId)

        assertTrue(database.meetingDao().observeByClient(clientId).first().isEmpty())
    }

    @Test
    fun updateTitle_persistsNewTitle() = runBlocking {
        val clientId = database.clientDao().insert(ClientEntity(name = "クライアントC", createdAt = 1_000L))
        val meetingId = database.meetingDao().insert(
            MeetingEntity(
                clientId = clientId,
                title = "旧タイトル",
                recordedAt = 2_000L,
                transcript = "本文",
                summary = "サマリー",
                decisions = emptyList(),
                concerns = emptyList(),
                nextMeetingDate = null,
                nextMeetingOriginalText = null
            )
        )

        database.meetingDao().updateTitle(meetingId, "新タイトル")

        assertEquals("新タイトル", database.meetingDao().observeByClient(clientId).first().first().title)
    }
}
