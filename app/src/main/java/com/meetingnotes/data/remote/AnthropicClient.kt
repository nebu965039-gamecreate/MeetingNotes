package com.meetingnotes.data.remote

import com.meetingnotes.BuildConfig
import com.meetingnotes.data.model.MeetingSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class AnthropicApiException(message: String, val statusCode: Int? = null) : IOException(message)

/**
 * 要約クライアント。APIキーはアプリに持たず、自前の中継Worker(`server/`)経由で
 * Claude を呼ぶ。Worker は Anthropic のレスポンスをそのまま返すため、パース処理は
 * 従来どおり `MessagesResponse` / `SummaryDto` を使う。
 */
class AnthropicClient(
    private val proxyUrl: String = BuildConfig.SUMMARY_PROXY_URL,
    private val appToken: String = BuildConfig.SUMMARY_PROXY_APP_TOKEN,
    private val integrityProvider: IntegrityTokenProvider? = null
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val jsonMediaType = "application/json".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun summarizeMeeting(transcript: String): MeetingSummary = withContext(Dispatchers.IO) {
        if (proxyUrl.isBlank()) {
            throw AnthropicApiException(
                "要約サーバーのURLが未設定です。local.propertiesにSUMMARY_PROXY_URLを設定してください。"
            )
        }

        val bodyJson = json.encodeToString(
            SummarizeRequest.serializer(),
            SummarizeRequest(transcript = transcript)
        )

        // Play Integrity(フェーズ2)。プロバイダ未設定・非対応端末では null(=ヘッダを付けない)。
        val integrityToken = integrityProvider?.let { provider ->
            provider.tokenFor(provider.requestHashOf(transcript))
        }

        var lastError: Exception? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            try {
                val request = Request.Builder()
                    .url(proxyUrl)
                    .addHeader("x-app-token", appToken)
                    .addHeader("content-type", "application/json")
                    .apply { if (integrityToken != null) addHeader("x-integrity-token", integrityToken) }
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body.string()

                    if (response.isSuccessful) {
                        return@withContext parseSummary(responseBody)
                    }

                    val retryable = response.code == 429 || response.code >= 500
                    if (!retryable || attempt == MAX_ATTEMPTS - 1) {
                        throw AnthropicApiException(
                            "要約APIエラー(${response.code}): $responseBody",
                            response.code
                        )
                    }
                    lastError = AnthropicApiException("要約APIエラー(${response.code})", response.code)
                }
            } catch (e: IOException) {
                lastError = e
                if (attempt == MAX_ATTEMPTS - 1) throw e
            }

            val backoffMillis = (INITIAL_BACKOFF_MS * 2.0.pow(attempt)).toLong()
            kotlinx.coroutines.delay(backoffMillis)
        }

        throw lastError ?: AnthropicApiException("要約APIの呼び出しに失敗しました。")
    }

    private fun parseSummary(responseBody: String): MeetingSummary {
        val response = json.decodeFromString(MessagesResponse.serializer(), responseBody)
        val toolUseBlock = response.content.firstOrNull { it.type == "tool_use" && it.name == TOOL_NAME }
            ?: throw AnthropicApiException("要約レスポンスにtool_useが含まれていません: $responseBody")

        val input = toolUseBlock.input as? JsonObject
            ?: throw AnthropicApiException("要約レスポンスのinputが不正です: $responseBody")

        val dto = json.decodeFromJsonElement(SummaryDto.serializer(), input)
        return dto.toDomain()
    }

    companion object {
        private const val TOOL_NAME = "extract_meeting_summary"
        private const val MAX_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_MS = 1000L
    }
}
