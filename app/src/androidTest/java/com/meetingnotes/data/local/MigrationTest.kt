package com.meetingnotes.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MeetingNotesDatabase::class.java
    )

    @Test
    fun migrate5To6_addsPhaseColumns_keepsExistingRows() {
        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO clients (name, memo, groupId, createdAt) VALUES ('C', NULL, NULL, 0)")
            execSQL(
                """
                INSERT INTO meetings
                  (clientId, folderId, title, recordedAt, endedAt, transcript, summary,
                   decisions, concerns, nextMeetingDate, nextMeetingOriginalText)
                VALUES (1, NULL, '既存商談', 1000, NULL, 't', 's', '[]', '[]', NULL, NULL)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        db.query("SELECT title, dealPhase, phaseOverride FROM meetings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "既存商談")
            assertNull(c.getString(1))
            assertNull(c.getString(2))
        }
    }
}
