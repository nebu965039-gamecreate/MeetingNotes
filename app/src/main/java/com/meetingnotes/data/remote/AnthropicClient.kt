package com.meetingnotes.data.remote

import com.meetingnotes.BuildConfig
import com.meetingnotes.data.model.MeetingSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import kotlin.math.pow

class AnthropicApiException(message: String, val statusCode: Int? = null) : IOException(message)

class AnthropicClient(
    private val apiKey: String = BuildConfig.ANTHROPIC_API_KEY
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val jsonMediaType = "application/json".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun summarizeMeeting(transcript: String): MeetingSummary = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "REPLACE_WITH_YOUR_KEY") {
            throw AnthropicApiException("ANTHROPIC_API_KEYが未設定です。local.propertiesに設定してください。")
        }

        val requestBody = MessagesRequest(
            model = "claude-haiku-4-5-20251001",
            maxTokens = 1500,
            temperature = 0.2,
            system = SYSTEM_PROMPT,
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = "以下は商談の文字起こしテキストです。上記のルールに従って抽出してください。\n\n# 文字起こしテキスト\n$transcript"
                )
            ),
            tools = listOf(
                ToolDefinition(
                    name = TOOL_NAME,
                    description = "商談の文字起こしから構造化データを抽出する",
                    inputSchema = SUMMARY_TOOL_SCHEMA
                )
            ),
            toolChoice = ToolChoice(type = "tool", name = TOOL_NAME)
        )

        val bodyJson = json.encodeToString(MessagesRequest.serializer(), requestBody)

        var lastError: Exception? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            try {
                val request = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
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
                            "Claude APIエラー(${response.code}): $responseBody",
                            response.code
                        )
                    }
                    lastError = AnthropicApiException("Claude APIエラー(${response.code})", response.code)
                }
            } catch (e: IOException) {
                lastError = e
                if (attempt == MAX_ATTEMPTS - 1) throw e
            }

            val backoffMillis = (INITIAL_BACKOFF_MS * 2.0.pow(attempt)).toLong()
            kotlinx.coroutines.delay(backoffMillis)
        }

        throw lastError ?: AnthropicApiException("Claude APIの呼び出しに失敗しました。")
    }

    private fun parseSummary(responseBody: String): MeetingSummary {
        val response = json.decodeFromString(MessagesResponse.serializer(), responseBody)
        val toolUseBlock = response.content.firstOrNull { it.type == "tool_use" && it.name == TOOL_NAME }
            ?: throw AnthropicApiException("Claude APIレスポンスにtool_useが含まれていません: $responseBody")

        val input = toolUseBlock.input as? JsonObject
            ?: throw AnthropicApiException("Claude APIレスポンスのinputが不正です: $responseBody")

        val dto = json.decodeFromJsonElement(SummaryDto.serializer(), input)
        return dto.toDomain()
    }

    companion object {
        private const val TOOL_NAME = "extract_meeting_summary"
        private const val MAX_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_MS = 1000L

        const val SYSTEM_PROMPT = """あなたはフリーランス・個人事業主向けの商談記録アシスタントです。
以下の商談の文字起こしテキストを読み、内容を構造化して抽出してください。

# 抽出ルール
1. 「決定事項」: その商談内で合意・確定した内容のみを抽出する。
   検討中・保留になった内容は含めない。
2. 「ToDo」: 誰が(担当者)、何を、いつまでに行うかが
   話されているものを抽出する。担当者や期限が不明な場合は
   「担当者: 未定」「期限: 未定」と明記する。
3. 「次回打ち合わせ」: 日時が明言されている場合のみ抽出する。
   曖昧な表現(例:「また来週あたり」)は
   originalText にそのまま記録し、date は null とする。
4. 「懸念点・注意点」: クライアントが不安・懸念・要望として
   話した内容を抽出する。
5. 文字起こしには誤字・脱字が含まれる可能性がある。
   文脈から明らかな誤変換は自然な形に補正してよいが、
   意味を変えるような推測は行わない。
6. 該当する情報がない項目は、空配列 [] または null とする。
   存在しない情報を作り出さない。
7. summary・decisions・concernsなど、すべてのテキスト項目の文体は
   体言止め中心の簡潔な記録調で統一すること。
   (例:「月額プランで契約に合意」「導入コストへの懸念」のように
   名詞で言い切り、「です・ます」「だ・である」などの
   語尾は付けない。)"""

        val SUMMARY_TOOL_SCHEMA: JsonObject = buildJsonObject {
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
                            putJsonArray("type") { add(JsonPrimitive("string")); add(JsonPrimitive("null")) }
                        }
                        putJsonObject("originalText") {
                            putJsonArray("type") { add(JsonPrimitive("string")); add(JsonPrimitive("null")) }
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
    }
}
