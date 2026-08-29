package com.meetingnotes.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClientGroupDaoTest {

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
    fun insertAndObserveGroup_returnsCreatedGroup() = runBlocking {
        database.clientGroupDao().insert(ClientGroupEntity(name = "重要顧客", createdAt = 1_000L))

        val groups = database.clientGroupDao().observeAll().first()
        assertEquals(1, groups.size)
        assertEquals("重要顧客", groups[0].name)
    }

    @Test
    fun renameGroup_persistsNewName() = runBlocking {
        val groupId = database.clientGroupDao().insert(ClientGroupEntity(name = "旧グループ名", createdAt = 1_000L))

        database.clientGroupDao().rename(groupId, "新グループ名")

        assertEquals("新グループ名", database.clientGroupDao().observeAll().first().first().name)
    }

    @Test
    fun deletingGroup_setsClientGroupIdToNull() = runBlocking {
        val groupId = database.clientGroupDao().insert(ClientGroupEntity(name = "重要顧客", createdAt = 1_000L))
        val clientId = database.clientDao().insert(ClientEntity(name = "クライアントA", groupId = groupId, createdAt = 1_000L))

        database.clientGroupDao().deleteById(groupId)

        val client = database.clientDao().getById(clientId)
        assertNull(client?.groupId)
    }

    @Test
    fun moveClientBetweenGroups_updatesGroupId() = runBlocking {
        val groupA = database.clientGroupDao().insert(ClientGroupEntity(name = "グループA", createdAt = 1_000L))
        val groupB = database.clientGroupDao().insert(ClientGroupEntity(name = "グループB", createdAt = 1_000L))
        val clientId = database.clientDao().insert(ClientEntity(name = "クライアントA", groupId = groupA, createdAt = 1_000L))

        database.clientDao().updateGroup(clientId, groupB)

        assertEquals(groupB, database.clientDao().getById(clientId)?.groupId)
    }
}
