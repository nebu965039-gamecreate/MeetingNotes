package com.meetingnotes.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * 無料枠の不正リセット対策フェーズ1(仕様書7.4)。
 * Settings.Secure.ANDROID_ID は端末+署名鍵+ユーザーに紐づき、再インストールでは変わらない。
 * 生の値は保存せず、SHA-256ハッシュのみをRoom DBに保存する。
 */
object DeviceIdentifier {

    @SuppressLint("HardwareIds")
    fun getHashedId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
        return sha256(androidId)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
