package com.meetingnotes.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meetingnotes.data.CreditPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserCreditsDaoTest {

    private lateinit var database: MeetingNotesDatabase
    private lateinit var dao: UserCreditsDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MeetingNotesDatabase::class.java
        ).build()
        dao = database.userCreditsDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun missingRowReturnsNull() = runBlocking {
        assertNull(dao.getByHash("device-a"))
    }

    @Test
    fun insertAndReadBack() = runBlocking {
        dao.insert(UserCreditsEntity("device-a", balance = CreditPolicy.MONTHLY_FREE_CREDITS, lastResetYearMonth = "2026-08"))

        val stored = dao.getByHash("device-a")
        assertEquals(CreditPolicy.MONTHLY_FREE_CREDITS, stored?.balance)

        val observed = dao.observeByHash("device-a").first()
        assertEquals("2026-08", observed?.lastResetYearMonth)
    }

    @Test
    fun updateBalancePersists() = runBlocking {
        dao.insert(UserCreditsEntity("device-a", balance = 3, lastResetYearMonth = "2026-08"))

        val current = dao.getByHash("device-a")!!
        dao.update(current.copy(balance = current.balance - 1))

        assertEquals(2, dao.getByHash("device-a")?.balance)
    }
}
