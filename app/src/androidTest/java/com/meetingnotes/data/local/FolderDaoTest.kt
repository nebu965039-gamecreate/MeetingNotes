package com.meetingnotes.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderDaoTest {

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

    private suspend fun insertMeeting(clientId: Long, folderId: Long?): Long =
        database.meetingDao().insert(
            MeetingEntity(
                clientId = clientId,
                folderId = folderId,
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

    @Test
    fun insertAndObserveFolder_returnsCreatedFolder() = runBlocking {
        val clientId = database.clientDao().insert(ClientEntity(name = "クライアント", createdAt = 1_000L))

        database.folderDao().insert(FolderEntity(clientId = clientId, name = "案件A", createdAt = 1_000L))

        val folders = database.folderDao().observeByClient(clientId).first()
        assertEquals(1, folders.size)
        assertEquals("案件A", folders[0].name)
    }

    @Test
    fun renameFolder_persistsNewName() = runBlocking {
        val clientId = database.clientDao().insert(ClientEntity(name = "クライアント", createdAt = 1_000L))
        val folderId = database.folderDao().insert(FolderEntity(clientId = clientId, name = "旧フォルダ名", createdAt = 1_000L))

        database.folderDao().rename(folderId, "新フォルダ名")

        assertEquals("新フォルダ名", database.folderDao().observeByClient(clientId).first().first().name)
    }

    @Test
    fun deletingFolder_setsMeetingFolderIdToNull() = runBlocking {
        val clientId = database.clientDao().insert(ClientEntity(name = "クライアント", createdAt = 1_000L))
        val folderId = database.folderDao().insert(FolderEntity(clientId = clientId, name = "案件A", createdAt = 1_000L))
        val meetingId = insertMeeting(clientId, folderId)

        database.folderDao().deleteById(folderId)

        val meeting = database.meetingDao().observeByClient(clientId).first().first { it.id == meetingId }
        assertNull(meeting.folderId)
    }

    @Test
    fun deletingClient_cascadesToFolders() = runBlocking {
        val clientId = database.clientDao().insert(ClientEntity(name = "クライアント", createdAt = 1_000L))
        database.folderDao().insert(FolderEntity(clientId = clientId, name = "案件A", createdAt = 1_000L))

        database.clientDao().deleteById(clientId)

        assertTrue(database.folderDao().observeByClient(clientId).first().isEmpty())
    }

    @Test
    fun moveMeetingBetweenFolders_updatesFolderId() = runBlocking {
        val clientId = database.clientDao().insert(ClientEntity(name = "クライアント", createdAt = 1_000L))
        val folderA = database.folderDao().insert(FolderEntity(clientId = clientId, name = "案件A", createdAt = 1_000L))
        val folderB = database.folderDao().insert(FolderEntity(clientId = clientId, name = "案件B", createdAt = 1_000L))
        val meetingId = insertMeeting(clientId, folderA)

        database.meetingDao().updateFolder(meetingId, folderB)

        val meeting = database.meetingDao().observeByClient(clientId).first().first { it.id == meetingId }
        assertEquals(folderB, meeting.folderId)
    }
}
