package com.pocketai.studio.ai.provider.cohere

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pocketai.studio.ai.provider.AiProvider
import com.pocketai.studio.ai.provider.ChatMessage
import com.pocketai.studio.ai.provider.ChatRequest
import com.pocketai.studio.ai.provider.ChatResponse
import com.pocketai.studio.data.repository.ProviderRepository
import com.pocketai.studio.domain.model.CloudModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class CohereProvider(
    private val providerRepository: ProviderRepository
) : AiProvider {

    override val providerId: String = "cohere"
    override val displayName: String = "Cohere"
    override val website: String = "https://cohere.com"
    override val requiresApiKey: Boolean = true
    override val supportsStreaming: Boolean = true

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cachedModels: List<CloudModel> = listOf(
        CloudModel(
            id = "command-r-plus",
            name = "Command R+",
            providerId = "cohere",
            providerName = "Cohere",
            contextWindow = 128000,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0025,
            pricingPer1kOutput = 0.01,
            isFree = false,
            capabilities = listOf("chat", "streaming", "rag"),
            isVision = false,
            isStreaming = true,
            description = "Cohere's most powerful model with advanced RAG capabilities and large context window",
            developer = "Cohere"
        ),
        CloudModel(
            id = "command-r",
            name = "Command R",
            providerId = "cohere",
            providerName = "Cohere",
            contextWindow = 128000,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0005,
            pricingPer1kOutput = 0.0015,
            isFree = false,
            capabilities = listOf("chat", "streaming", "rag"),
            isVision = false,
            isStreaming = true,
            description = "Balanced model with strong RAG and multilingual capabilities",
            developer = "Cohere"
        ),
        CloudModel(
            id = "command-light",
            name = "Command Light",
            providerId = "cohere",
            providerName = "Cohere",
            contextWindow = 4000,
            maxOutputTokens = 2000,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Lightweight model optimized for speed and low-latency applications",
            developer = "Cohere"
        ),
        CloudModel(
            id = "command",
            name = "Command",
            providerId = "cohere",
            providerName = "Cohere",
            contextWindow = 4000,
            maxOutputTokens = 2000,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Cohere's original generation model, reliable and efficient",
            developer = "Cohere"
        )
    )

    override fun getCachedModels(): List<CloudModel> = cachedModels

    override fun getDefaultModel(): String = "command-r"

    override fun getBaseUrl(): String = "https://api.cohere.ai"

    override suspend fun listModels(): List<CloudModel> = withContext(Dispatchers.IO) {
        val apiKey = providerRepository.getActiveApiKey(providerId)
        if (apiKey == null) {
            return@withContext cachedModels
        }
        try {
            val request = Request.Builder()
                .url("${getBaseUrl()}/v1/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) {
                return@withContext cachedModels
            }

            val json = JsonParser.parseString(body).asJsonObject
            val modelsArray = json.getAsJsonArray("models") ?: return@withContext cachedModels
            val fetched = mutableListOf<CloudModel>()
            for (element in modelsArray) {
                val obj = element.asJsonObject
                val id = obj.get("name")?.asString ?: continue
                val contextWindow = obj.get("context_length")?.asInt ?: 4000
                val description = obj.get("description")?.asString ?: ""
                fetched.add(
                    CloudModel(
                        id = id,
                        name = id.replaceFirstChar { it.uppercase() },
                        providerId = "cohere",
                        providerName = "Cohere",
                        contextWindow = contextWindow,
                        description = description,
                        capabilities = listOf("chat", "streaming"),
                        isStreaming = true
                    )
                )
            }
            if (fetched.isNotEmpty()) fetched else cachedModels
        } catch (_: Exception) {
            cachedModels
        }
    }

    override suspend fun chat(request: ChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val apiKey = providerRepository.getActiveApiKey(providerId)
            ?: throw IllegalStateException("API key not configured for Cohere")

        // Build Cohere-specific request body
        val jsonBody = buildCohereRequest(request, stream = false)

        val httpRequest = Request.Builder()
            .url("${getBaseUrl()}/v1/chat")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body?.string()
            ?: throw RuntimeException("Empty response from Cohere")

        if (!response.isSuccessful) {
            throw RuntimeException("Cohere API error ${response.code}: $responseBody")
        }

        val json = JsonParser.parseString(responseBody).asJsonObject
        val content = json.get("text")?.asString ?: ""
        val finishReason = mapCohereFinishReason(json.get("finish_reason")?.asString)
        val meta = json.getAsJsonObject("meta")
        val billedUnits = meta?.getAsJsonObject("billed_units")
        val inputTokens = billedUnits?.get("input_tokens")?.asInt ?: 0
        val outputTokens = billedUnits?.get("output_tokens")?.asInt ?: 0

        ChatResponse(
            content = content,
            model = request.model,
            providerId = providerId,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            finishReason = finishReason
        )
    }

    override suspend fun chatStream(request: ChatRequest): Flow<String> = flow {
        val apiKey = providerRepository.getActiveApiKey(providerId)
            ?: throw IllegalStateException("API key not configured for Cohere")

        val jsonBody = buildCohereRequest(request, stream = true)

        val httpRequest = Request.Builder()
            .url("${getBaseUrl()}/v1/chat")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("Cohere API error ${response.code}: $errorBody")
        }

        val body = response.body ?: throw RuntimeException("Empty response body")
        val reader = BufferedReader(InputStreamReader(body.byteStream()))
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (currentLine.isEmpty()) continue
                // Cohere SSE format: each line is a JSON event
                // Events: stream-start, text-generation, stream-end
                try {
                    val json = JsonParser.parseString(currentLine).asJsonObject
                    val eventType = json.get("event_type")?.asString
                    if (eventType == "text-generation") {
                        val text = json.get("text")?.asString ?: continue
                        emit(text)
                    } else if (eventType == "stream-end") {
                        break
                    }
                } catch (_: Exception) {
                    // Skip malformed lines
                }
            }
        } finally {
            reader.close()
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildCohereRequest(request: ChatRequest, stream: Boolean): String {
        val json = JsonObject()

        // Model
        json.addProperty("model", request.model)

        // Extract system prompt and chat history
        val systemMessages = request.messages.filter { it.role == "system" }
        val conversationMessages = request.messages.filter { it.role != "system" }

        // System prompt goes to preamble
        if (request.systemPrompt != null) {
            json.addProperty("preamble", request.systemPrompt)
        } else if (systemMessages.isNotEmpty()) {
            json.addProperty("preamble", systemMessages.joinToString("\n") { it.content })
        }

        // Last user message goes to "message" field
        val lastUserIndex = conversationMessages.lastOrNull { it.role == "user" }
        val userMessage = lastUserIndex?.content ?: ""
        json.addProperty("message", userMessage)

        // Previous messages go to chat_history
        val historyMessages = conversationMessages.filter { it != lastUserIndex }
        if (historyMessages.isNotEmpty()) {
            val chatHistory = JsonArray()
            for (msg in historyMessages) {
                val historyEntry = JsonObject()
                // Cohere uses USER and CHATBOT roles (uppercase)
                when (msg.role.lowercase()) {
                    "user" -> historyEntry.addProperty("role", "USER")
                    "assistant" -> historyEntry.addProperty("role", "CHATBOT")
                    "system" -> {
                        // System messages are handled separately via preamble
                        continue
                    }
                    else -> historyEntry.addProperty("role", "USER")
                }
                historyEntry.addProperty("message", msg.content)
                chatHistory.add(historyEntry)
            }
            if (chatHistory.size() > 0) {
                json.add("chat_history", chatHistory)
            }
        }

        json.addProperty("stream", stream)
        json.addProperty("temperature", request.temperature)
        json.addProperty("max_tokens", request.maxTokens)
        // Cohere uses "p" for top_p
        json.addProperty("p", request.topP)

        return json.toString()
    }

    private fun mapCohereFinishReason(reason: String?): String {
        return when (reason?.uppercase()) {
            "COMPLETE" -> "stop"
            "MAX_TOKENS" -> "length"
            "ERROR" -> "error"
            "ERROR_LIMIT" -> "error"
            null -> "stop"
            else -> reason.lowercase()
        }
    }
}
