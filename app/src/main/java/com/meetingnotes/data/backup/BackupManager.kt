package com.meetingnotes.data.backup

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 全データの JSON バックアップ/復元。
 *
 * - 形式: 全テーブルを `sqlite_master` から列挙し `SELECT *` を JSON 配列にシリアライズ。
 *   スキーマに追従するため個別 Entity/DAO には依存しない。
 * - 暗号化: パスワード任意。設定時は PBKDF2(SHA-256) + AES-GCM で本体を包む。
 * - 復元: 全テーブル DELETE → バックアップ内容を INSERT(全置換え)。呼び出し側でアプリ再起動すること。
 */
object BackupManager {

    const val FORMAT_VERSION = 1
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    private val SYSTEM_TABLES = setOf("android_metadata", "sqlite_sequence", "room_master_table")

    class RestoreException(message: String) : Exception(message)

    // ---- Export --------------------------------------------------------------

    /** 全テーブルを JSON 文字列にする。[password] が非 null なら暗号化した封筒を返す。 */
    fun export(db: SupportSQLiteDatabase, password: String?): String {
        val payload = dumpToJson(db).toString()
        return if (password.isNullOrEmpty()) payload else encrypt(payload, password)
    }

    private fun dumpToJson(db: SupportSQLiteDatabase): JSONObject {
        val root = JSONObject()
        root.put("format", FORMAT_VERSION)
        root.put("encrypted", false)
        root.put("dbVersion", db.version)
        root.put("exportedAt", System.currentTimeMillis())

        val tables = JSONObject()
        for (table in userTables(db)) {
            val rows = JSONArray()
            db.query("SELECT * FROM `$table`").use { c ->
                while (c.moveToNext()) rows.put(rowToJson(c))
            }
            tables.put(table, rows)
        }
        root.put("tables", tables)
        return root
    }

    private fun rowToJson(c: Cursor): JSONObject {
        val row = JSONObject()
        for (i in 0 until c.columnCount) {
            val name = c.getColumnName(i)
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> row.put(name, c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> row.put(name, c.getDouble(i))
                Cursor.FIELD_TYPE_BLOB -> row.put(name, "base64:" + Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP))
                else -> row.put(name, c.getString(i))
            }
        }
        return row
    }

    // ---- Import -------------------------------------------------------------

    /**
     * バックアップ文字列を読み、全テーブルを置き換える。
     * @throws RestoreException 形式不正・パスワード相違・新しすぎる DB バージョンのとき。
     */
    @SuppressLint("Range")
    fun import(db: SupportSQLiteDatabase, raw: String, password: String?) {
        val outer = try {
            JSONObject(raw.trim())
        } catch (e: Exception) {
            throw RestoreException("バックアップファイルを読み取れません。")
        }

        val payload = if (outer.optBoolean("encrypted")) {
            if (password.isNullOrEmpty()) throw RestoreException("このバックアップにはパスワードが必要です。")
            JSONObject(decrypt(outer, password))
        } else {
            outer
        }

        if (payload.optInt("format", -1) != FORMAT_VERSION) {
            throw RestoreException("対応していないバックアップ形式です。")
        }
        val backupDbVersion = payload.optInt("dbVersion", 0)
        if (backupDbVersion > db.version) {
            throw RestoreException("アプリより新しいバージョンのバックアップです。アプリを更新してから復元してください。")
        }
        val tables = payload.optJSONObject("tables")
            ?: throw RestoreException("バックアップにデータがありません。")

        val currentTables = userTables(db)
        db.execSQL("PRAGMA foreign_keys = OFF")
        db.beginTransaction()
        try {
            for (table in currentTables) db.execSQL("DELETE FROM `$table`")
            val names = tables.keys()
            while (names.hasNext()) {
                val table = names.next()
                if (table !in currentTables) continue
                val columns = tableColumns(db, table)
                val rows = tables.getJSONArray(table)
                for (i in 0 until rows.length()) {
                    val obj = rows.getJSONObject(i)
                    val values = ContentValues()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key !in columns) continue
                        putValue(values, key, obj.get(key))
                    }
                    if (values.size() > 0) {
                        db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    private fun putValue(values: ContentValues, key: String, value: Any?) {
        when (value) {
            null, JSONObject.NULL -> values.putNull(key)
            is Boolean -> values.put(key, if (value) 1L else 0L)
            is Int -> values.put(key, value.toLong())
            is Long -> values.put(key, value)
            is Double -> values.put(key, value)
            is String ->
                if (value.startsWith("base64:")) {
                    values.put(key, Base64.decode(value.removePrefix("base64:"), Base64.NO_WRAP))
                } else {
                    values.put(key, value)
                }
            else -> values.put(key, value.toString())
        }
    }

    // ---- Schema helpers ---------------------------------------------------------

    private fun userTables(db: SupportSQLiteDatabase): List<String> {
        val names = mutableListOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
        ).use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)
                if (name !in SYSTEM_TABLES) names += name
            }
        }
        return names
    }

    private fun tableColumns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val cols = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) cols += c.getString(nameIdx)
        }
        return cols
    }

    // ---- Crypto ---------------------------------------------------------------

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun encrypt(plain: String, password: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject().apply {
            put("format", FORMAT_VERSION)
            put("encrypted", true)
            put("kdf", "pbkdf2-sha256")
            put("iterations", PBKDF2_ITERATIONS)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(ct, Base64.NO_WRAP))
        }.toString()
    }

    private fun decrypt(envelope: JSONObject, password: String): String {
        val salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val ct = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return try {
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            throw RestoreException("パスワードが違うか、ファイルが壊れています。")
        }
    }

    // ---- Restart -------------------------------------------------------------

    /** 復元後にアプリを再起動して Room のキャッシュを捨てる。 */
    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
