package com.meetingnotes.data.remote

import com.meetingnotes.BuildConfig
import com.meetingnotes.data.model.MeetingSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 【TEMP】Claude APIキー到着までの一時的な動作確認用クライアント。
 * Google Gemini API(無料枠)を使ってE2Eフローだけを確認する。本番はAnthropicClientを使うこと。
 * 無料枠の入出力データはGoogleの製品改善に利用されうるため、実際の商談データでは絶対に使わない(ダミーデータのみ)。
 */
class GeminiClient(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // gemini-flash-latestはthinking有りで応答が遅いことがあるため長めに設定(TEMP用クライアント)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun summarizeMeeting(transcript: String): MeetingSummary = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "REPLACE_WITH_YOUR_KEY") {
            throw AnthropicApiException("GEMINI_API_KEYが未設定です。local.propertiesに設定してください。")
        }

        val requestBody = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(
                        GeminiPart(
                            "以下は商談の文字起こしテキストです。上記のルールに従って抽出してください。\n\n# 文字起こしテキスト\n$transcript"
                        )
                    )
                )
            ),
            systemInstruction = GeminiContent(role = "system", parts = listOf(GeminiPart(AnthropicClient.SYSTEM_PROMPT))),
            tools = listOf(GeminiTool(functionDeclarations = listOf(FUNCTION_DECLARATION))),
            toolConfig = GeminiToolConfig(
                functionCallingConfig = GeminiFunctionCallingConfig(mode = "ANY", allowedFunctionNames = listOf(TOOL_NAME))
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.2, maxOutputTokens = 1500)
        )

        val bodyJson = json.encodeToString(GeminiRequest.serializer(), requestBody)

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("content-type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body.string()

            if (!response.isSuccessful) {
                throw AnthropicApiException("Gemini APIエラー(${response.code}): $responseBody", response.code)
            }

            parseSummary(responseBody)
        }
    }

    private fun parseSummary(responseBody: String): MeetingSummary {
        val response = json.decodeFromString(GeminiResponse.serializer(), responseBody)
        val functionCall = response.candidates.firstOrNull()
            ?.content?.parts?.firstOrNull { it.functionCall != null }
            ?.functionCall
            ?: throw AnthropicApiException("Gemini APIレスポンスにfunctionCallが含まれていません: $responseBody")

        val dto = json.decodeFromJsonElement(SummaryDto.serializer(), functionCall.args)
        return dto.toDomain()
    }

    companion object {
        // gemini-flash-latest(2026-08時点でgemini-3.7-flash相当)は無料枠で混雑気味だったため、
        // より軽量なlite系エイリアスに変更(2026-08時点でgemini-3.5-flash-lite相当)
        private const val MODEL = "gemini-flash-lite-latest"
        private const val TOOL_NAME = "extract_meeting_summary"

        private val FUNCTION_DECLARATION = GeminiFunctionDeclaration(
            name = TOOL_NAME,
            description = "商談の文字起こしから構造化データを抽出する",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("decisions") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("content") { put("type", "string") }
                            }
                        }
                    }
                    putJsonObject("todos") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("task") { put("type", "string") }
                                putJsonObject("assignee") { put("type", "string") }
                                putJsonObject("deadline") { put("type", "string") }
                            }
                        }
                    }
                    putJsonObject("nextMeeting") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("date") {
                                put("type", "string")
                                put("nullable", true)
                            }
                            putJsonObject("originalText") {
                                put("type", "string")
                                put("nullable", true)
                            }
                        }
                    }
                    putJsonObject("concerns") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("content") { put("type", "string") }
                            }
                        }
                    }
                    putJsonObject("summary") { put("type", "string") }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("decisions"))
                    add(JsonPrimitive("todos"))
                    add(JsonPrimitive("nextMeeting"))
                    add(JsonPrimitive("concerns"))
                    add(JsonPrimitive("summary"))
                }
            }
        )
    }
}

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val tools: List<GeminiTool>,
    val toolConfig: GeminiToolConfig,
    val generationConfig: GeminiGenerationConfig
)

@Serializable
data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val functionCall: GeminiFunctionCall? = null
)

@Serializable
data class GeminiTool(
    val functionDeclarations: List<GeminiFunctionDeclaration>
)

@Serializable
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class GeminiToolConfig(
    val functionCallingConfig: GeminiFunctionCallingConfig
)

@Serializable
data class GeminiFunctionCallingConfig(
    val mode: String,
    val allowedFunctionNames: List<String>? = null
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double,
    @SerialName("maxOutputTokens") val maxOutputTokens: Int
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)

@Serializable
data class GeminiFunctionCall(
    val name: String,
    val args: JsonObject
)
