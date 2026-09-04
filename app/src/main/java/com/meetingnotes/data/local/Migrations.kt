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

val databaseMigrations: Array<Migration> = arrayOf(MIGRATION_5_6, MIGRATION_6_7)
