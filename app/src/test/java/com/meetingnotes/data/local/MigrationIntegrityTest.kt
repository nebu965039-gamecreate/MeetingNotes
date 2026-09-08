package com.meetingnotes.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * DBマイグレーションの登録漏れを CI(JVMユニットテスト)で検出する。
 *
 * 実際の SQL は流さない(それは androidTest の `MigrationTest`)。ここで見るのは:
 *  - `app/schemas/.../<N>.json` がコミットされている最大バージョン = 現行 DB バージョン
 *  - v5 → 現行まで、各 1 段ずつ `databaseMigrations` に Migration があること(gap 無し)
 *  - Migration が全て +1 ステップで、重複・逆行が無いこと
 *
 * 「version を上げたが Migration を配列に足し忘れた」を確実に落とす。
 */
class MigrationIntegrityTest {

    /** クローズドテスト初回配信。v5 以降は破壊的フォールバック禁止 = 全段に Migration が必要。 */
    private val firstShippedVersion = 5

    private fun schemaDir(): File {
        val name = "schemas/com.meetingnotes.data.local.MeetingNotesDatabase"
        // テストの作業ディレクトリは通常 app/ だが、念のため両方を見る。
        return listOf(File(name), File("app/$name")).firstOrNull { it.isDirectory }
            ?: error("スキーマディレクトリが見つかりません: $name")
    }

    private fun currentSchemaVersion(): Int =
        schemaDir().listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            ?.maxOrNull()
            ?: error("スキーマ json が1つもありません")

    @Test
    fun `every version step from first-shipped to current has a migration`() {
        val current = currentSchemaVersion()
        val edges = databaseMigrations.map { it.startVersion to it.endVersion }.toSet()
        for (v in firstShippedVersion until current) {
            assertTrue(
                "v$v → v${v + 1} の Migration が databaseMigrations にありません" +
                    "(Entity/version を変えたら Migrations.kt への追加も必要)",
                (v to (v + 1)) in edges
            )
        }
    }

    @Test
    fun `migrations are single-step with no duplicates or backward steps`() {
        val steps = databaseMigrations.map { it.startVersion to it.endVersion }
        assertEquals("Migration に重複があります: $steps", steps.size, steps.toSet().size)
        steps.forEach { (from, to) ->
            assertTrue("Migration $from→$to が 1 段ステップではありません", to == from + 1)
            assertTrue("Migration の開始バージョンが不正です: $from", from >= firstShippedVersion)
        }
    }

    @Test
    fun `databaseMigrations reaches exactly the current schema version`() {
        val current = currentSchemaVersion()
        val maxEnd = databaseMigrations.maxOf { it.endVersion }
        assertEquals(
            "databaseMigrations の最終バージョン($maxEnd)が現行スキーマ($current)と一致しません",
            current, maxEnd
        )
    }
}
