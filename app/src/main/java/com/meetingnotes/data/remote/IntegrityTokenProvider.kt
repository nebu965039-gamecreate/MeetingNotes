package com.meetingnotes.data.remote

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.meetingnotes.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

private const val TAG = "IntegrityTokenProvider"

/**
 * Play Integrity(Standard API)のトークン発行ラッパー。
 *
 * `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` が未設定、または Play サービスが使えない端末では
 * 常に `null` を返す(= プロキシ呼び出しに Integrity ヘッダを付けない)。
 * Worker 側が `PLAY_INTEGRITY_ENABLED=off` の間は影響なし。
 */
class IntegrityTokenProvider(context: Context) {

    private val appContext = context.applicationContext
    private val cloudProjectNumber: Long? =
        BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER.toLongOrNull()

    private val prepareMutex = Mutex()
    @Volatile
    private var tokenProvider: StandardIntegrityTokenProvider? = null

    /** transcript と対応する requestHash(SHA-256 hex)。Worker 側と一致させる。 */
    fun requestHashOf(transcript: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(transcript.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** 指定 requestHash に紐づく Integrity トークン。取得できなければ null。 */
    suspend fun tokenFor(requestHash: String): String? {
        val projectNumber = cloudProjectNumber ?: return null
        val provider = ensureProvider(projectNumber) ?: return null
        return runCatching {
            provider.request(
                StandardIntegrityTokenRequest.builder()
                    .setRequestHash(requestHash)
                    .build()
            ).await().token()
        }.onFailure { Log.w(TAG, "integrity token request failed: ${it.message}") }
            .getOrNull()
    }

    private suspend fun ensureProvider(projectNumber: Long): StandardIntegrityTokenProvider? {
        tokenProvider?.let { return it }
        return prepareMutex.withLock {
            tokenProvider ?: runCatching {
                IntegrityManagerFactory.createStandard(appContext)
                    .prepareIntegrityToken(
                        PrepareIntegrityTokenRequest.builder()
                            .setCloudProjectNumber(projectNumber)
                            .build()
                    ).await()
            }.onFailure { Log.w(TAG, "prepareIntegrityToken failed: ${it.message}") }
                .getOrNull()
                ?.also { tokenProvider = it }
        }
    }
}
