package com.pocketai.studio.ai.provider.mistral

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

class MistralProvider(
    private val providerRepository: ProviderRepository
) : AiProvider {

    override val providerId: String = "mistral"
    override val displayName: String = "Mistral"
    override val website: String = "https://mistral.ai"
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
            id = "mistral-large-latest",
            name = "Mistral Large",
            providerId = "mistral",
            providerName = "Mistral",
            contextWindow = 128000,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.002,
            pricingPer1kOutput = 0.006,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = true,
            isStreaming = true,
            description = "Mistral's flagship model with state-of-the-art reasoning, multilingual capabilities, and vision understanding",
            developer = "Mistral AI"
        ),
        CloudModel(
            id = "mistral-small-latest",
            name = "Mistral Small",
            providerId = "mistral",
            providerName = "Mistral",
            contextWindow = 32000,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.001,
            pricingPer1kOutput = 0.003,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Optimized for low-latency workloads with strong performance on code and reasoning tasks",
            developer = "Mistral AI"
        ),
        CloudModel(
            id = "codestral-latest",
            name = "Codestral",
            providerId = "mistral",
            providerName = "Mistral",
            contextWindow = 256000,
            maxOutputTokens = 8192,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "code", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Specialized for code generation with 256K context window",
            developer = "Mistral AI"
        ),
        CloudModel(
            id = "open-mistral-nemo",
            name = "Mistral Nemo",
            providerId = "mistral",
            providerName = "Mistral",
            contextWindow = 128000,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = true,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Free tier model with Mistral Nemo architecture, great for experimentation",
            developer = "Mistral AI"
        )
    )

    override fun getCachedModels(): List<CloudModel> = cachedModels

    override fun getDefaultModel(): String = "mistral-small-latest"

    override fun getBaseUrl(): String = "https://api.mistral.ai"

    override suspend fun listModels(): List<CloudModel> = withContext(Dispatchers.IO) {
        val apiKey = providerRepository.getActiveApiKey(providerId)
        if (apiKey == null) {
            return@withContext cachedModels
        }
        try {
            val request = Request.Builder()
                .url("${getBaseUrl()}/v1/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) {
                return@withContext cachedModels
            }

            val json = JsonParser.parseString(body).asJsonObject
            val modelsArray = json.getAsJsonArray("data") ?: return@withContext cachedModels
            val fetched = mutableListOf<CloudModel>()
            for (element in modelsArray) {
                val obj = element.asJsonObject
                val id = obj.get("id")?.asString ?: continue
                val contextWindow = obj.get("max_context_length")?.asInt ?: 32000
                fetched.add(
                    CloudModel(
                        id = id,
                        name = id.replace("-latest", "").replace("-", " ").replaceFirstChar { it.uppercase() },
                        providerId = "mistral",
                        providerName = "Mistral",
                        contextWindow = contextWindow,
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
            ?: throw IllegalStateException("API key not configured for Mistral")

        val requestBody = buildOpenAiBody(request, stream = false)

        val httpRequest = Request.Builder()
            .url("${getBaseUrl()}/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body?.string()
            ?: throw RuntimeException("Empty response from Mistral")

        if (!response.isSuccessful) {
            throw RuntimeException("Mistral API error ${response.code}: $responseBody")
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
            ?: throw IllegalStateException("API key not configured for Mistral")

        val requestBody = buildOpenAiBody(request, stream = true)

        val httpRequest = Request.Builder()
            .url("${getBaseUrl()}/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("Mistral API error ${response.code}: $errorBody")
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
                            if (finishReason != null && !finishReason.isJsonNull && finishReason.asString == "stop") {
                                break
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
        json.addProperty("temperature", request.temperature.toDouble())
        json.addProperty("max_tokens", request.maxTokens)
        json.addProperty("top_p", request.topP.toDouble())

        val messagesArray = JsonArray()

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
