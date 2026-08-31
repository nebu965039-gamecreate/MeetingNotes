package com.meetingnotes.data.local

import androidx.room.migration.Migration

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
val databaseMigrations: Array<Migration> = emptyArray()
