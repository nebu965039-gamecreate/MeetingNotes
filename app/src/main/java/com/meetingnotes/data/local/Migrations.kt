package com.meetingnotes.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room スキーマの正式なマイグレーション。
 *
 * 手順(スキーマ変更時):
 *  1. Entity を変更し `MeetingNotesDatabase` の `version` を +1
 *  2. ビルドすると `app/schemas/<db>/<新version>.json` が生成される → コミットする
 *  3. 直前バージョン → 新バージョンの `Migration` をここに追加し `databaseMigrations` に含める
 *  4. `app/src/androidTest` にマイグレーションテストを追加(`MigrationTestHelper`)
 *
 * version 5 がクローズドテストの初回配信バージョン。実利用者は全員 v5 以降のため、
 * v5 → v6 以降は破壊的マイグレーションにフォールバックせず、必ずここに Migration を書く。
 * (`MeetingNotesApp` は v1〜v4 のみ `fallbackToDestructiveMigrationFrom` で許容している)
 *
 * 追加例:
 *   val MIGRATION_5_6 = object : Migration(5, 6) {
 *       override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
 *           db.execSQL("ALTER TABLE meetings ADD COLUMN newColumn TEXT")
 *       }
 *   }
 *   val databaseMigrations: Array<Migration> = arrayOf(MIGRATION_5_6)
 */
/** v5 → v6: 商談フェーズ(F3)。`meetings` に AI 推定値とユーザー上書き値の2列を追加。 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meetings ADD COLUMN dealPhase TEXT")
        db.execSQL("ALTER TABLE meetings ADD COLUMN phaseOverride TEXT")
    }
}

/** v6 → v7: F2 用の `client_briefing` テーブルを追加。 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `client_briefing` (" +
                "`clientId` INTEGER NOT NULL, `flowText` TEXT NOT NULL, " +
                "`generatedAt` INTEGER NOT NULL, `sourceMeetingCount` INTEGER NOT NULL, " +
                "PRIMARY KEY(`clientId`), " +
                "FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_client_briefing_clientId` " +
                "ON `client_briefing` (`clientId`)"
        )
    }
}

/** v7 → v8: F7 リマインド通知の履歴テーブル。 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notification_log` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`meetingId` INTEGER NOT NULL, `clientId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                "`scheduledFor` TEXT NOT NULL, `firedAt` INTEGER NOT NULL )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_notification_log_meetingId_scheduledFor` " +
                "ON `notification_log` (`meetingId`, `scheduledFor`)"
        )
    }
}

/** v8 → v9: F1 用に `meetings.followedUpAt`(メールフォロー済み時刻)を追加。 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meetings ADD COLUMN followedUpAt INTEGER")
    }
}

/** v9 → v10: F5 用に `meetings.followupDraft`(要約時に生成したフォローアップ下書き)を追加。 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meetings ADD COLUMN followupDraft TEXT")
    }
}

/** v10 → v11: リモート会議モード。`meetings.meetingType` と `user_credits` の月次オンライン回数を追加。 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meetings ADD COLUMN meetingType TEXT")
        db.execSQL("ALTER TABLE user_credits ADD COLUMN onlineTranscriptionsUsed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE user_credits ADD COLUMN onlineTranscriptionsBonus INTEGER NOT NULL DEFAULT 0")
    }
}

/** v11 → v12: ToDo 期限の日付解決(`todos.dueDate`)と顧客の連絡先(`clients.email` / `clients.phone`)。 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE todos ADD COLUMN dueDate TEXT")
        db.execSQL("ALTER TABLE clients ADD COLUMN email TEXT")
        db.execSQL("ALTER TABLE clients ADD COLUMN phone TEXT")
    }
}

/** v12 → v13: クライアントの担当者(先方窓口)テーブル。 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `client_contacts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`clientId` INTEGER NOT NULL, `name` TEXT NOT NULL, `note` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_client_contacts_clientId` ON `client_contacts` (`clientId`)")
    }
}

/** v13 → v14: 担当者ごとのメール・電話。 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE client_contacts ADD COLUMN email TEXT")
        db.execSQL("ALTER TABLE client_contacts ADD COLUMN phone TEXT")
    }
}

/** v14 → v15: 要約完了時に自動起票する「フォローアップメール」ToDo の識別フラグ。 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE todos ADD COLUMN isFollowupEmail INTEGER NOT NULL DEFAULT 0")
    }
}

/** v15 → v16: クライアント配下の任意プロジェクト。`client_projects` テーブル + `meetings.projectId`(FKなし)。 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `client_projects` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`clientId` INTEGER NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_client_projects_clientId` ON `client_projects` (`clientId`)")
        db.execSQL("ALTER TABLE meetings ADD COLUMN projectId INTEGER")
    }
}

/**
 * v16 → v17: 独立した「予定」テーブル。1クライアントに複数持てる。
 * 既存の `meetings.nextMeetingDate` は Kotlin 側(`MeetingRepository.backfillSchedules`)で
 * 起動時に取り込む(日付文字列のパースが必要なため SQL では行わない)。
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `schedules` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`clientId` INTEGER NOT NULL, `sourceMeetingId` INTEGER, " +
                "`startAtMillis` INTEGER NOT NULL, `hasTime` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `note` TEXT NOT NULL, `participants` TEXT NOT NULL, " +
                "`phase` TEXT, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedules_clientId` ON `schedules` (`clientId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedules_sourceMeetingId` ON `schedules` (`sourceMeetingId`)")
    }
}

/** v17 → v18: 案件(= プロジェクト)の金額・状態。`client_projects` に列追加。 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE client_projects ADD COLUMN phase TEXT")
        db.execSQL("ALTER TABLE client_projects ADD COLUMN currency TEXT NOT NULL DEFAULT 'JPY'")
        db.execSQL("ALTER TABLE client_projects ADD COLUMN estimatedAmount INTEGER")
        db.execSQL("ALTER TABLE client_projects ADD COLUMN wonAmount INTEGER")
        db.execSQL("ALTER TABLE client_projects ADD COLUMN wonAt INTEGER")
    }
}

/**
 * v18 → v19: 手動 ToDo 追加のための `todos` 再構築。
 * `meetingId` を nullable に、`clientId`(NOT NULL・clients へ FK CASCADE)を追加。
 * 既存行の `clientId` は由来商談の `meetings.clientId` から補完する。
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `todos_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`meetingId` INTEGER, `clientId` INTEGER NOT NULL, " +
                "`task` TEXT NOT NULL, `assignee` TEXT NOT NULL, `deadline` TEXT NOT NULL, " +
                "`dueDate` TEXT, `isDone` INTEGER NOT NULL, `isFollowupEmail` INTEGER NOT NULL, " +
                "FOREIGN KEY(`meetingId`) REFERENCES `meetings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "INSERT INTO `todos_new` " +
                "(`id`, `meetingId`, `clientId`, `task`, `assignee`, `deadline`, `dueDate`, `isDone`, `isFollowupEmail`) " +
                "SELECT t.`id`, t.`meetingId`, m.`clientId`, t.`task`, t.`assignee`, t.`deadline`, " +
                "t.`dueDate`, t.`isDone`, t.`isFollowupEmail` " +
                "FROM `todos` t INNER JOIN `meetings` m ON m.`id` = t.`meetingId`"
        )
        db.execSQL("DROP TABLE `todos`")
        db.execSQL("ALTER TABLE `todos_new` RENAME TO `todos`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_meetingId` ON `todos` (`meetingId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_clientId` ON `todos` (`clientId`)")
    }
}

/** v19 → v20: 失注理由。`client_projects` に `lostReason` 列を追加。 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE client_projects ADD COLUMN lostReason TEXT")
    }
}

val databaseMigrations: Array<Migration> = arrayOf(
    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
    MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20
)
