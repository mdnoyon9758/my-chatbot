package com.pocketai.studio.ai.provider.anthropic

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
 * Anthropic provider implementation.
 *
 * Calls https://api.anthropic.com/v1/messages for chat completions.
 * Uses the `x-api-key` header for authentication (not the standard
 * Authorization: Bearer pattern). Streaming uses SSE with
 * `event: content_block_delta` / `data: {"type":"content_block_delta","delta":{"text":"..."}}`
 * and signals the end of a stream with `event: message_stop`.
 */
class AnthropicProvider(
    private val providerRepository: ProviderRepository
) : AiProvider {

    override val providerId: String = "anthropic"
    override val displayName: String = "Anthropic"
    override val website: String = "https://anthropic.com"
    override val requiresApiKey: Boolean = true
    override val supportsStreaming: Boolean = true

    private val gson = Gson()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun getBaseUrl(): String = "https://api.anthropic.com/v1"

    override fun getDefaultModel(): String = "claude-sonnet-4-20250514"

    override fun getCachedModels(): List<CloudModel> {
        val caps = listOf("chat", "streaming", "vision")
        val textCaps = listOf("chat", "streaming")
        return listOf(
            CloudModel(
                id = "claude-sonnet-4-20250514",
                name = "Claude Sonnet 4",
                providerId = providerId,
                providerName = displayName,
                contextWindow = 200000,
                maxOutputTokens = 8192,
                pricingPer1kInput = 0.003,   // $3 / 1M tokens
                pricingPer1kOutput = 0.015,  // $15 / 1M tokens
                capabilities = caps,
                isVision = true,
                isStreaming = true,
                description = "Anthropic's most intelligent model, balancing speed and capability.",
                developer = "Anthropic"
            ),
            CloudModel(
                id = "claude-3-5-sonnet-20241022",
                name = "Claude 3.5 Sonnet",
                providerId = providerId,
                providerName = displayName,
                contextWindow = 200000,
                maxOutputTokens = 8192,
                pricingPer1kInput = 0.003,
                pricingPer1kOutput = 0.015,
                capabilities = caps,
                isVision = true,
                isStreaming = true,
                description = "Previous Sonnet generation with strong reasoning and vision.",
                developer = "Anthropic"
            ),
            CloudModel(
                id = "claude-3-opus-20240229",
                name = "Claude 3 Opus",
                providerId = providerId,
                providerName = displayName,
                contextWindow = 200000,
                maxOutputTokens = 4096,
                pricingPer1kInput = 0.015,
                pricingPer1kOutput = 0.075,
                capabilities = caps,
                isVision = true,
                isStreaming = true,
                description = "Powerful model for complex analysis and tasks.",
                developer = "Anthropic"
            ),
            CloudModel(
                id = "claude-3-haiku-20240307",
                name = "Claude 3 Haiku",
                providerId = providerId,
                providerName = displayName,
                contextWindow = 200000,
                maxOutputTokens = 4096,
                pricingPer1kInput = 0.00025,
                pricingPer1kOutput = 0.00125,
                capabilities = caps,
                isVision = true,
                isStreaming = true,
                description = "Fastest and most compact model for near-instant responses.",
                developer = "Anthropic"
            ),
            CloudModel(
                id = "claude-opus-4-20250514",
                name = "Claude Opus 4",
                providerId = providerId,
                providerName = displayName,
                contextWindow = 200000,
                maxOutputTokens = 8192,
                pricingPer1kInput = 0.015,
                pricingPer1kOutput = 0.075,
                capabilities = textCaps,
                isVision = false,
                isStreaming = true,
                description = "Latest Opus model with improved reasoning and depth.",
                developer = "Anthropic"
            )
        )
    }

    override suspend fun listModels(): List<CloudModel> = withContext(Dispatchers.IO) {
        val apiKey = providerRepository.getActiveApiKey(providerId)
        if (apiKey == null || apiKey.isBlank()) {
            return@withContext getCachedModels()
        }

        // Anthropic does not have a public /models endpoint, so return cached models.
        // If the key is present we could attempt to verify connectivity, but for now
        // just return the hardcoded model list.
        getCachedModels()
    }

    override suspend fun chat(request: ChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val apiKey = providerRepository.getActiveApiKey(providerId)
        if (apiKey == null || apiKey.isBlank()) {
            return@withContext ChatResponse(
                content = "Error: No API key configured for Anthropic. Add your API key in Settings.",
                model = request.model,
                providerId = providerId,
                finishReason = "error"
            )
        }

        val payload = buildChatPayload(request, stream = false)

        try {
            val httpRequest = Request.Builder()
                .url("${getBaseUrl()}/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
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
                    val contentArray = root?.getAsJsonArray("content") ?: JsonArray()
                    val textContent = StringBuilder()
                    for (block in contentArray) {
                        val blockObj = block.asJsonObject
                        val type = blockObj.get("type")?.asString
                        if (type == "text") {
                            textContent.append(blockObj.get("text")?.asString ?: "")
                        }
                    }

                    val usage = root.getAsJsonObject("usage")
                    val inputTokens = usage?.get("input_tokens")?.asInt ?: 0
                    val outputTokens = usage?.get("output_tokens")?.asInt ?: 0
                    val modelUsed = root.get("model")?.asString ?: request.model

                    // Determine finish_reason from the "stop_reason" field
                    val stopReason = root.get("stop_reason")?.asString
                    val finishReason = when (stopReason) {
                        "end_turn" -> "stop"
                        "max_tokens" -> "length"
                        "tool_use" -> "tool_calls"
                        else -> stopReason ?: "stop"
                    }

                    ChatResponse(
                        content = textContent.toString(),
                        model = modelUsed,
                        providerId = providerId,
                        inputTokens = inputTokens,
                        outputTokens = outputTokens,
                        finishReason = finishReason
                    )
                } catch (e: Exception) {
                    ChatResponse(
                        content = "Error parsing Anthropic response: ${e.message}",
                        model = request.model,
                        providerId = providerId,
                        finishReason = "error"
                    )
                }
            }
        } catch (e: Exception) {
            ChatResponse(
                content = "Network error contacting Anthropic: ${e.message}",
                model = request.model,
                providerId = providerId,
                finishReason = "error"
            )
        }
    }

    override suspend fun chatStream(request: ChatRequest): Flow<String> = flow {
        val apiKey = providerRepository.getActiveApiKey(providerId)
        if (apiKey == null || apiKey.isBlank()) {
            emit("Error: No API key configured for Anthropic. Add your API key in Settings.")
            return@flow
        }

        val payload = buildChatPayload(request, stream = true)

        try {
            val httpRequest = Request.Builder()
                .url("${getBaseUrl()}/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
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
                    emit("Error: empty response body from Anthropic.")
                    return@use
                }

                BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                    // Track the most recent event type to associate the next data line with it
                    var currentEvent = ""

                    while (true) {
                        val line = try {
                            reader.readLine()
                        } catch (_: Exception) {
                            null
                        }
                        if (line == null) break

                        val trimmed = line.trim()

                        // Skip empty lines (SSE blank line = end of event; we don't need special handling)
                        if (trimmed.isEmpty()) {
                            currentEvent = ""
                            continue
                        }

                        // Track event type
                        if (trimmed.startsWith("event:")) {
                            currentEvent = trimmed.removePrefix("event:").trim()
                            continue
                        }

                        // Process data lines
                        if (trimmed.startsWith("data:")) {
                            val data = trimmed.removePrefix("data:").trim()
                            if (data.isEmpty()) continue

                            // Handle ping events
                            if (data == "{\"type\":\"ping\"}") continue

                            try {
                                val json = gson.fromJson(data, JsonObject::class.java) ?: continue
                                val type = json.get("type")?.asString

                                if (type == "error") {
                                    val errorMsg = json.get("error")?.asJsonObject?.get("message")?.asString
                                    emit("Error: ${errorMsg ?: "Unknown Anthropic error"}")
                                    break
                                }

                                // Stream events for content_block_delta carry the actual text
                                if (currentEvent == "content_block_delta" || type == "content_block_delta") {
                                    val delta = json.getAsJsonObject("delta") ?: continue
                                    val text = delta.get("text")?.asString
                                    if (text != null && text.isNotEmpty()) {
                                        emit(text)
                                    }
                                }

                                // Stop conditions
                                if (currentEvent == "message_stop" || type == "message_stop" ||
                                    currentEvent == "message_error" || type == "message_error"
                                ) {
                                    break
                                }
                            } catch (_: Exception) {
                                // Skip malformed JSON data
                            }
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
        root.addProperty("max_tokens", request.maxTokens)
        root.addProperty("stream", stream)

        // Anthropic puts the system prompt in a top-level "system" field, not in messages
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { sp ->
            root.addProperty("system", sp)
        }

        // Build the messages array. Anthropic expects messages with role "user" or "assistant".
        // Map "system" role messages into the system prompt if not already set.
        val messagesArray = JsonArray()
        for (msg in request.messages) {
            val obj = JsonObject()
            // Anthropic only accepts "user" and "assistant" roles in the messages array
            val role = when (msg.role.lowercase()) {
                "system" -> "user" // map system to user if no top-level system
                else -> msg.role.lowercase()
            }
            obj.addProperty("role", role)
            obj.addProperty("content", msg.content)
            messagesArray.add(obj)
        }
        root.add("messages", messagesArray)

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
            401 -> apiMessage ?: "Authentication failed (401). Check your Anthropic API key."
            403 -> apiMessage ?: "Access forbidden (403). Your API key may lack permission."
            404 -> apiMessage ?: "Not found (404). The requested endpoint or model does not exist."
            429 -> apiMessage ?: "Rate limit reached (429). Please slow down or check your Anthropic plan."
            in 500..599 -> apiMessage ?: "Anthropic server error ($code). Try again later."
            else -> apiMessage ?: "Anthropic request failed with HTTP $code."
        }
    }
}
