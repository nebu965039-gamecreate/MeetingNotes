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

    @Test
    fun migrate5To7_fullChain_addsBriefingTable() {
        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO clients (name, memo, groupId, createdAt) VALUES ('C', NULL, NULL, 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_5_6, MIGRATION_6_7)

        db.execSQL(
            "INSERT INTO client_briefing (clientId, flowText, generatedAt, sourceMeetingCount) " +
                "VALUES (1, '流れ', 100, 2)"
        )
        db.query("SELECT flowText, sourceMeetingCount FROM client_briefing WHERE clientId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "流れ")
            assertTrue(c.getInt(1) == 2)
        }
    }

    @Test
    fun migrate7To8_addsNotificationLogTable() {
        helper.createDatabase(dbName, 7).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.execSQL(
            "INSERT INTO notification_log " +
                "(meetingId, clientId, title, body, scheduledFor, firedAt) " +
                "VALUES (1, 2, 'T', 'B', '2026-09-10', 500)"
        )
        db.query("SELECT title, scheduledFor FROM notification_log WHERE meetingId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "T")
            assertTrue(c.getString(1) == "2026-09-10")
        }
    }

    @Test
    fun migrate8To9_addsFollowedUpAtColumn_keepsExistingRows() {
        helper.createDatabase(dbName, 8).apply {
            execSQL("INSERT INTO clients (name, memo, groupId, createdAt) VALUES ('C', NULL, NULL, 0)")
            execSQL(
                """
                INSERT INTO meetings
                  (clientId, folderId, title, recordedAt, endedAt, transcript, summary,
                   decisions, concerns, nextMeetingDate, nextMeetingOriginalText, dealPhase, phaseOverride)
                VALUES (1, NULL, '既存商談', 1000, NULL, 't', 's', '[]', '[]', NULL, NULL, NULL, NULL)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 9, true, MIGRATION_8_9)

        db.query("SELECT title, followedUpAt FROM meetings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "既存商談")
            assertNull(c.getString(1))
        }
        db.execSQL("UPDATE meetings SET followedUpAt = 12345 WHERE id = 1")
        db.query("SELECT followedUpAt FROM meetings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getLong(0) == 12345L)
        }
    }

    @Test
    fun migrate9To10_addsFollowupDraftColumn() {
        helper.createDatabase(dbName, 9).apply {
            execSQL("INSERT INTO clients (name, memo, groupId, createdAt) VALUES ('C', NULL, NULL, 0)")
            execSQL(
                """
                INSERT INTO meetings
                  (clientId, folderId, title, recordedAt, endedAt, transcript, summary,
                   decisions, concerns, nextMeetingDate, nextMeetingOriginalText, dealPhase,
                   phaseOverride, followedUpAt)
                VALUES (1, NULL, '既存商談', 1000, NULL, 't', 's', '[]', '[]', NULL, NULL, NULL, NULL, NULL)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 10, true, MIGRATION_9_10)

        db.query("SELECT followupDraft FROM meetings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertNull(c.getString(0))
        }
        db.execSQL("UPDATE meetings SET followupDraft = 'お世話になっております' WHERE id = 1")
        db.query("SELECT followupDraft FROM meetings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "お世話になっております")
        }
    }

    @Test
    fun migrate12To13_addsClientContactsTable() {
        helper.createDatabase(dbName, 12).apply {
            execSQL("INSERT INTO clients (name, memo, groupId, createdAt) VALUES ('C', NULL, NULL, 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 13, true, MIGRATION_12_13)

        db.execSQL(
            "INSERT INTO client_contacts (clientId, name, note, createdAt) VALUES (1, '田中', '部長', 100)"
        )
        db.query("SELECT name, note FROM client_contacts WHERE clientId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "田中")
            assertTrue(c.getString(1) == "部長")
        }
    }

    @Test
    fun migrate13To14_addsContactEmailAndPhone() {
        helper.createDatabase(dbName, 13).apply {
            execSQL("INSERT INTO clients (name, memo, groupId, createdAt) VALUES ('C', NULL, NULL, 0)")
            execSQL("INSERT INTO client_contacts (clientId, name, note, createdAt) VALUES (1, '田中', NULL, 100)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 14, true, MIGRATION_13_14)

        db.query("SELECT email, phone FROM client_contacts WHERE clientId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertNull(c.getString(0))
            assertNull(c.getString(1))
        }
        db.execSQL("UPDATE client_contacts SET email = 't@x.com', phone = '03' WHERE clientId = 1")
        db.query("SELECT email, phone FROM client_contacts WHERE clientId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "t@x.com")
            assertTrue(c.getString(1) == "03")
        }
    }

    @Test
    fun migrate11To12_addsTodoDueDateAndClientContact() {
        helper.createDatabase(dbName, 11).apply {
            execSQL("INSERT INTO clients (name, memo, groupId, createdAt) VALUES ('C', NULL, NULL, 0)")
            execSQL(
                """
                INSERT INTO meetings
                  (clientId, folderId, title, recordedAt, endedAt, transcript, summary,
                   decisions, concerns, nextMeetingDate, nextMeetingOriginalText, dealPhase,
                   phaseOverride, followedUpAt, followupDraft, meetingType)
                VALUES (1, NULL, 'M', 1, NULL, 't', 's', '[]', '[]', NULL, NULL, NULL, NULL, NULL, NULL, NULL)
                """.trimIndent()
            )
            execSQL("INSERT INTO todos (meetingId, task, assignee, deadline, isDone) VALUES (1, 'T', '自分', '金曜', 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 12, true, MIGRATION_11_12)

        db.query("SELECT dueDate FROM todos WHERE meetingId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertNull(c.getString(0))
        }
        db.execSQL("UPDATE clients SET email = 'a@b.com', phone = '090' WHERE id = 1")
        db.query("SELECT email, phone FROM clients WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "a@b.com")
            assertTrue(c.getString(1) == "090")
        }
    }

    @Test
    fun migrate15To16_addsClientProjectsAndMeetingProjectId() {
        helper.createDatabase(dbName, 15).apply {
            execSQL("INSERT INTO clients (name, memo, groupId, createdAt) VALUES ('C', NULL, NULL, 0)")
            execSQL(
                """
                INSERT INTO meetings
                  (clientId, folderId, title, recordedAt, endedAt, transcript, summary,
                   decisions, concerns, nextMeetingDate, nextMeetingOriginalText, dealPhase,
                   phaseOverride, followedUpAt, followupDraft, meetingType)
                VALUES (1, NULL, 'M', 1, NULL, 't', 's', '[]', '[]', NULL, NULL, NULL, NULL, NULL, NULL, NULL)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 16, true, MIGRATION_15_16)

        db.query("SELECT projectId FROM meetings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertNull(c.getString(0))
        }
        db.execSQL("INSERT INTO client_projects (clientId, name, createdAt) VALUES (1, '案件A', 100)")
        db.execSQL("UPDATE meetings SET projectId = 1 WHERE id = 1")
        db.query("SELECT p.name FROM meetings m JOIN client_projects p ON m.projectId = p.id WHERE m.id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0) == "案件A")
        }
    }

    @Test
    fun migrate14To15_addsFollowupEmailFlag_defaultsToZero() {
        helper.createDatabase(dbName, 14).apply {
            execSQL("INSERT INTO clients (name, memo, groupId, createdAt) VALUES ('C', NULL, NULL, 0)")
            execSQL(
                """
                INSERT INTO meetings
                  (clientId, folderId, title, recordedAt, endedAt, transcript, summary,
                   decisions, concerns, nextMeetingDate, nextMeetingOriginalText, dealPhase,
                   phaseOverride, followedUpAt, followupDraft, meetingType)
                VALUES (1, NULL, 'M', 1, NULL, 't', 's', '[]', '[]', NULL, NULL, NULL, NULL, NULL, NULL, NULL)
                """.trimIndent()
            )
            execSQL("INSERT INTO todos (meetingId, task, assignee, deadline, isDone) VALUES (1, 'T', '自分', '金曜', 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 15, true, MIGRATION_14_15)

        db.query("SELECT isFollowupEmail FROM todos WHERE meetingId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getInt(0) == 0)
        }
        db.execSQL(
            "INSERT INTO todos (meetingId, task, assignee, deadline, dueDate, isDone, isFollowupEmail) " +
                "VALUES (1, 'メール', '自分', '翌日', '2026-09-10', 0, 1)"
        )
        db.query("SELECT isFollowupEmail FROM todos WHERE task = 'メール'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getInt(0) == 1)
        }
    }

    @Test
    fun migrate10To11_addsMeetingTypeAndOnlineCounters() {
        helper.createDatabase(dbName, 10).apply {
            execSQL("INSERT INTO clients (name, memo, groupId, createdAt) VALUES ('C', NULL, NULL, 0)")
            execSQL(
                """
                INSERT INTO meetings
                  (clientId, folderId, title, recordedAt, endedAt, transcript, summary,
                   decisions, concerns, nextMeetingDate, nextMeetingOriginalText, dealPhase,
                   phaseOverride, followedUpAt, followupDraft)
                VALUES (1, NULL, '既存商談', 1000, NULL, 't', 's', '[]', '[]', NULL, NULL, NULL, NULL, NULL, NULL)
                """.trimIndent()
            )
            execSQL(
                "INSERT INTO user_credits (deviceIdHash, balance, lastResetYearMonth) VALUES ('h', 3, '2026-09')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 11, true, MIGRATION_10_11)

        db.query("SELECT meetingType FROM meetings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertNull(c.getString(0))
        }
        db.query(
            "SELECT onlineTranscriptionsUsed, onlineTranscriptionsBonus FROM user_credits WHERE deviceIdHash = 'h'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getInt(0) == 0)
            assertTrue(c.getInt(1) == 0)
        }
    }
}
