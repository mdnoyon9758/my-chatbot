package com.pocketai.studio.ai.provider.openai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pocketai.studio.ai.provider.AiProvider
import com.pocketai.studio.ai.provider.ChatMessage
import com.pocketai.studio.ai.provider.ChatRequest
import com.pocketai.studio.ai.provider.ChatResponse
import com.pocketai.studio.data.repository.ProviderRepository
import com.pocketai.studio.domain.model.CloudModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * OpenAI provider implementation.
 *
 * Calls https://api.openai.com/v1/chat/completions for chat completions and
 * https://api.openai.com/v1/models for listing models. Streaming uses SSE
 * with the format `data: {"choices":[{"delta":{"content":"..."}}]}` and
 * terminates on `data: [DONE]`.
 */
class OpenAiProvider(
    private val providerRepository: ProviderRepository
) : AiProvider {

    override val providerId: String = "openai"
    override val displayName: String = "OpenAI"
    override val website: String = "https://openai.com"
    override val requiresApiKey: Boolean = true
    override val supportsStreaming: Boolean = true

    private val gson = Gson()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun getBaseUrl(): String = "https://api.openai.com/v1"

    override fun getDefaultModel(): String = "gpt-4o"

    override fun getCachedModels(): List<CloudModel> {
        val chatCaps = listOf("chat", "streaming")
        val visionCaps = listOf("chat", "streaming", "vision")
        return listOf(
            CloudModel(
                id = "gpt-4o",
                name = "GPT-4o",
                providerId = providerId,
                providerName = displayName,
                contextWindow = 128000,
                maxOutputTokens = 16384,
                pricingPer1kInput = 0.0025,   // $2.50 / 1M tokens = $0.0025 / 1k
                pricingPer1kOutput = 0.0100,  // $10.00 / 1M tokens = $0.01 / 1k
                capabilities = visionCaps,
                isVision = true,
                isStreaming = true,
                description = "OpenAI's most advanced multimodal model with vision capabilities.",
                developer = "OpenAI"
            ),
            CloudModel(
                id = "gpt-4o-mini",
                name = "GPT-4o mini",
                providerId = providerId,
                providerName = displayName,
                contextWindow = 128000,
                maxOutputTokens = 16384,
                pricingPer1kInput = 0.00015,  // $0.15 / 1M tokens
                pricingPer1kOutput = 0.00060, // $0.60 / 1M tokens
                capabilities = visionCaps,
                isVision = true,
                isStreaming = true,
                description = "Affordable small model for fast, lightweight tasks.",
                developer = "OpenAI"
            ),
            CloudModel(
                id = "gpt-4-turbo",
                name = "GPT-4 Turbo",
                providerId = providerId,
                providerName = displayName,
                contextWindow = 128000,
                maxOutputTokens = 4096,
                pricingPer1kInput = 0.01,
                pricingPer1kOutput = 0.03,
                capabilities = visionCaps,
                isVision = true,
                isStreaming = true,
                description = "Previous-generation flagship with vision capabilities.",
                developer = "OpenAI"
            ),
            CloudModel(
                id = "gpt-3.5-turbo",
                name = "GPT-3.5 Turbo",
                providerId = providerId,
                providerName = displayName,
                contextWindow = 16385,
                maxOutputTokens = 4096,
                pricingPer1kInput = 0.0005,
                pricingPer1kOutput = 0.0015,
                capabilities = chatCaps,
                isVision = false,
                isStreaming = true,
                description = "Fast and inexpensive text model.",
                developer = "OpenAI"
            ),
            CloudModel(
                id = "o1",
                name = "o1",
                providerId = providerId,
                providerName = displayName,
                contextWindow = 200000,
                maxOutputTokens = 100000,
                pricingPer1kInput = 0.015,
                pricingPer1kOutput = 0.06,
                capabilities = chatCaps,
                isVision = false,
                isStreaming = false,
                description = "Reasoning model optimized for complex multi-step problem solving.",
                developer = "OpenAI"
            ),
            CloudModel(
                id = "o3-mini",
                name = "o3-mini",
                providerId = providerId,
                providerName = displayName,
                contextWindow = 200000,
                maxOutputTokens = 100000,
                pricingPer1kInput = 0.0011,
                pricingPer1kOutput = 0.0044,
                capabilities = chatCaps,
                isVision = false,
                isStreaming = false,
                description = "Small, fast, cost-efficient reasoning model.",
                developer = "OpenAI"
            )
        )
    }

    override suspend fun listModels(): List<CloudModel> = withContext(Dispatchers.IO) {
        val apiKey = providerRepository.getActiveApiKey(providerId)
        if (apiKey.isNullOrBlank()) {
            return@withContext getCachedModels()
        }

        try {
            val request = Request.Builder()
                .url("${getBaseUrl()}/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext getCachedModels()
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext getCachedModels()

                val cached = getCachedModels().associateBy { it.id }
                val remoteIds = mutableSetOf<String>()

                try {
                    val root = gson.fromJson(body, JsonObject::class.java)
                    val data = root?.getAsJsonArray("data") ?: return@withContext getCachedModels()
                    for (element in data) {
                        val id = element.asJsonObject?.get("id")?.asString ?: continue
                        remoteIds.add(id)
                    }
                } catch (_: Exception) {
                    return@withContext getCachedModels()
                }

                // Return cached models enriched with remote IDs, plus any unknown models
                val result = cached.values.filter { it.id in remoteIds }.toMutableList()
                for (id in remoteIds.sorted()) {
                    if (id !in cached) {
                        result.add(
                            CloudModel(
                                id = id,
                                name = id,
                                providerId = providerId,
                                providerName = displayName,
                                contextWindow = 8192,
                                maxOutputTokens = 4096,
                                isStreaming = true,
                                description = "Available on OpenAI API.",
                                developer = "OpenAI"
                            )
                        )
                    }
                }
                result
            }
        } catch (_: Exception) {
            getCachedModels()
        }
    }

    override suspend fun chat(request: ChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val apiKey = providerRepository.getActiveApiKey(providerId)
        if (apiKey.isNullOrBlank()) {
            return@withContext ChatResponse(
                content = "Error: No API key configured for OpenAI. Add your API key in Settings.",
                model = request.model,
                providerId = providerId,
                finishReason = "error"
            )
        }

        val payload = buildChatPayload(request, stream = false)

        try {
            val httpRequest = Request.Builder()
                .url("${getBaseUrl()}/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(httpRequest).execute().use { response ->
                val raw = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val message = parseError(raw, response.code)
                    return@withContext ChatResponse(
                        content = message,
                        model = request.model,
                        providerId = providerId,
                        finishReason = "error"
                    )
                }

                try {
                    val root = gson.fromJson(raw, JsonObject::class.java)
                    val choices = root?.getAsJsonArray("choices")
                    val firstChoice = choices?.firstOrNull()?.asJsonObject
                    val messageObj = firstChoice?.getAsJsonObject("message")
                    val content = messageObj?.get("content")?.asString ?: ""
                    val finishReason = firstChoice?.get("finish_reason")?.asString ?: "stop"
                    val usage = root.getAsJsonObject("usage")
                    val inputTokens = usage?.get("prompt_tokens")?.asInt ?: 0
                    val outputTokens = usage?.get("completion_tokens")?.asInt ?: 0
                    val modelUsed = root.get("model")?.asString ?: request.model

                    ChatResponse(
                        content = content,
                        model = modelUsed,
                        providerId = providerId,
                        inputTokens = inputTokens,
                        outputTokens = outputTokens,
                        finishReason = finishReason
                    )
                } catch (e: Exception) {
                    ChatResponse(
                        content = "Error parsing OpenAI response: ${e.message}",
                        model = request.model,
                        providerId = providerId,
                        finishReason = "error"
                    )
                }
            }
        } catch (e: Exception) {
            ChatResponse(
                content = "Network error contacting OpenAI: ${e.message}",
                model = request.model,
                providerId = providerId,
                finishReason = "error"
            )
        }
    }

    override suspend fun chatStream(request: ChatRequest): Flow<String> = flow {
        val apiKey = providerRepository.getActiveApiKey(providerId)
        if (apiKey.isNullOrBlank()) {
            emit("Error: No API key configured for OpenAI. Add your API key in Settings.")
            return@flow
        }

        val payload = buildChatPayload(request, stream = true)

        try {
            val httpRequest = Request.Builder()
                .url("${getBaseUrl()}/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    emit(parseError(raw, response.code))
                    return@use
                }

                val body = response.body ?: run {
                    emit("Error: empty response body from OpenAI.")
                    return@use
                }

                BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                    var line: String?
                    while (true) {
                        line = try {
                            reader.readLine()
                        } catch (_: Exception) {
                            null
                        }
                        if (line == null) break

                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        if (!trimmed.startsWith("data:")) continue

                        val data = trimmed.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        if (data.isEmpty()) continue

                        try {
                            val chunk = gson.fromJson(data, JsonObject::class.java) ?: continue
                            val choices = chunk.getAsJsonArray("choices") ?: continue
                            if (choices.size() == 0) continue

                            val firstChoice = choices[0].asJsonObject
                            val delta = firstChoice.getAsJsonObject("delta") ?: continue
                            val contentEl = delta.get("content")
                            if (contentEl != null && !contentEl.isJsonNull) {
                                val text = contentEl.asString
                                if (text.isNotEmpty()) {
                                    emit(text)
                                }
                            }

                            val finishReason = firstChoice.get("finish_reason")
                            if (finishReason != null && !finishReason.isJsonNull) {
                                val reason = finishReason.asString
                                if (reason == "stop" || reason == "length" || reason == "content_filter") {
                                    break
                                }
                            }
                        } catch (_: Exception) {
                            // Skip malformed JSON chunk
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit("Network error during streaming: ${e.message}")
        }
    }

    // ---- Private helpers ----

    private fun buildChatPayload(request: ChatRequest, stream: Boolean): String {
        val root = JsonObject()
        root.addProperty("model", request.model)

        val messagesArray = JsonArray()

        // System prompt as a system-role message (first in the array)
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { sp ->
            val sysMsg = JsonObject()
            sysMsg.addProperty("role", "system")
            sysMsg.addProperty("content", sp)
            messagesArray.add(sysMsg)
        }

        for (msg in request.messages) {
            val obj = JsonObject()
            obj.addProperty("role", msg.role)
            obj.addProperty("content", msg.content)
            messagesArray.add(obj)
        }
        root.add("messages", messagesArray)

        root.addProperty("temperature", request.temperature.toDouble())
        root.addProperty("max_tokens", request.maxTokens)
        root.addProperty("top_p", request.topP.toDouble())
        root.addProperty("stream", stream)

        return gson.toJson(root)
    }

    private fun parseError(body: String, code: Int): String {
        val apiMessage = try {
            gson.fromJson(body, JsonObject::class.java)
                ?.getAsJsonObject("error")
                ?.get("message")
                ?.asString
        } catch (_: Exception) {
            null
        }

        return when (code) {
            400 -> apiMessage ?: "Bad request (400). Check your request parameters."
            401 -> apiMessage ?: "Authentication failed (401). Check your OpenAI API key."
            403 -> apiMessage ?: "Access forbidden (403). Your API key may lack permission."
            404 -> apiMessage ?: "Not found (404). The requested endpoint or model does not exist."
            429 -> apiMessage ?: "Rate limit reached (429). Please slow down or check your OpenAI plan."
            in 500..599 -> apiMessage ?: "OpenAI server error ($code). Try again later."
            else -> apiMessage ?: "OpenAI request failed with HTTP $code."
        }
    }
}
