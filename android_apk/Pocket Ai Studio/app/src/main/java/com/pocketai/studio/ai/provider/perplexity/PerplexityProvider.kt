package com.pocketai.studio.ai.provider.perplexity

import com.google.gson.Gson
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

class PerplexityProvider(
    private val providerRepository: ProviderRepository
) : AiProvider {

    override val providerId: String = "perplexity"
    override val displayName: String = "Perplexity"
    override val website: String = "https://perplexity.ai"
    override val requiresApiKey: Boolean = true
    override val supportsStreaming: Boolean = true

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cachedModels: List<CloudModel> = listOf(
        CloudModel(
            id = "sonar-pro",
            name = "Sonar Pro",
            providerId = "perplexity",
            providerName = "Perplexity",
            contextWindow = 127000,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.001,
            pricingPer1kOutput = 0.005,
            isFree = false,
            capabilities = listOf("chat", "streaming", "web_search"),
            isVision = false,
            isStreaming = true,
            description = "Perplexity's most capable model with advanced reasoning and web search integration",
            developer = "Perplexity AI"
        ),
        CloudModel(
            id = "sonar",
            name = "Sonar",
            providerId = "perplexity",
            providerName = "Perplexity",
            contextWindow = 127000,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0008,
            pricingPer1kOutput = 0.0024,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Fast and efficient model with solid reasoning capabilities",
            developer = "Perplexity AI"
        ),
        CloudModel(
            id = "sonar-deep-research",
            name = "Sonar Deep Research",
            providerId = "perplexity",
            providerName = "Perplexity",
            contextWindow = 127000,
            maxOutputTokens = 8192,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming", "web_search", "deep_research"),
            isVision = false,
            isStreaming = true,
            description = "Specialized for deep research tasks with thorough web analysis and citations",
            developer = "Perplexity AI"
        )
    )

    override fun getCachedModels(): List<CloudModel> = cachedModels

    override fun getDefaultModel(): String = "sonar-pro"

    override fun getBaseUrl(): String = "https://api.perplexity.ai"

    override suspend fun listModels(): List<CloudModel> = withContext(Dispatchers.IO) {
        // Perplexity does not have a public models listing API endpoint
        // Return cached models
        cachedModels
    }

    override suspend fun chat(request: ChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val apiKey = providerRepository.getActiveApiKey(providerId)
            ?: throw IllegalStateException("API key not configured for Perplexity")

        val requestBody = buildOpenAiBody(request, stream = false)

        val httpRequest = Request.Builder()
            .url("${getBaseUrl()}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body?.string()
            ?: throw RuntimeException("Empty response from Perplexity")

        if (!response.isSuccessful) {
            throw RuntimeException("Perplexity API error ${response.code}: $responseBody")
        }

        val json = JsonParser.parseString(responseBody).asJsonObject
        val choices = json.getAsJsonArray("choices")
        val firstChoice = choices?.firstOrNull()?.asJsonObject
        val message = firstChoice?.getAsJsonObject("message")
        val content = message?.get("content")?.asString ?: ""
        val finishReason = firstChoice?.get("finish_reason")?.asString ?: "stop"
        val usage = json.getAsJsonObject("usage")
        val inputTokens = usage?.get("prompt_tokens")?.asInt ?: 0
        val outputTokens = usage?.get("completion_tokens")?.asInt ?: 0

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
            ?: throw IllegalStateException("API key not configured for Perplexity")

        val requestBody = buildOpenAiBody(request, stream = true)

        val httpRequest = Request.Builder()
            .url("${getBaseUrl()}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("Perplexity API error ${response.code}: $errorBody")
        }

        val body = response.body ?: throw RuntimeException("Empty response body")
        val reader = BufferedReader(InputStreamReader(body.byteStream()))
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (currentLine.startsWith("data: ")) {
                    val data = currentLine.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    if (data.isEmpty()) continue
                    try {
                        val json = JsonParser.parseString(data).asJsonObject
                        val choices = json.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                            val content = delta?.get("content")?.asString
                            if (content != null) {
                                emit(content)
                            }
                            val finishReason = choices[0].asJsonObject.get("finish_reason")
                            if (finishReason != null && !finishReason.isJsonNull && finishReason.asString != null && finishReason.asString != "null") {
                                // End of stream signal
                                if (finishReason.asString == "stop" || finishReason.asString == "length") {
                                    break
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Skip malformed JSON chunks
                    }
                }
            }
        } finally {
            reader.close()
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildOpenAiBody(request: ChatRequest, stream: Boolean): String {
        val json = JsonObject()

        json.addProperty("model", request.model)
        json.addProperty("stream", stream)
        json.addProperty("temperature", request.temperature)
        json.addProperty("max_tokens", request.maxTokens)
        json.addProperty("top_p", request.topP)

        // Build messages array — Perplexity supports system messages in the messages array
        val messagesArray = com.google.gson.JsonArray()

        // Add system prompt as first message if provided
        if (request.systemPrompt != null) {
            val sysMsg = JsonObject()
            sysMsg.addProperty("role", "system")
            sysMsg.addProperty("content", request.systemPrompt)
            messagesArray.add(sysMsg)
        }

        for (msg in request.messages) {
            val msgObj = JsonObject()
            msgObj.addProperty("role", msg.role)
            msgObj.addProperty("content", msg.content)
            messagesArray.add(msgObj)
        }

        json.add("messages", messagesArray)

        return json.toString()
    }
}
