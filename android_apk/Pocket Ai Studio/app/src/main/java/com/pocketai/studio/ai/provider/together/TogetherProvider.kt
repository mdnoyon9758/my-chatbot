package com.pocketai.studio.ai.provider.together

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

class TogetherProvider(
    private val providerRepository: ProviderRepository
) : AiProvider {

    override val providerId: String = "together"
    override val displayName: String = "Together AI"
    override val website: String = "https://together.ai"
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
            id = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
            name = "Llama 3.3 70B Turbo",
            providerId = "together",
            providerName = "Together AI",
            contextWindow = 131072,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Meta's leading instruction-tuned 70B model, optimized for high-throughput via Together Turbo",
            developer = "Meta"
        ),
        CloudModel(
            id = "meta-llama/Llama-3.2-11B-Vision-Instruct-Turbo",
            name = "Llama 3.2 11B Vision Turbo",
            providerId = "together",
            providerName = "Together AI",
            contextWindow = 131072,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming", "vision"),
            isVision = true,
            isStreaming = true,
            description = "Multimodal vision-language model with 11B parameters, Turbo-optimized",
            developer = "Meta"
        ),
        CloudModel(
            id = "deepseek-ai/DeepSeek-V3",
            name = "DeepSeek V3",
            providerId = "together",
            providerName = "Together AI",
            contextWindow = 65536,
            maxOutputTokens = 8192,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "DeepSeek's powerful 671B MoE model with strong reasoning",
            developer = "DeepSeek"
        ),
        CloudModel(
            id = "Qwen/Qwen2.5-72B-Instruct-Turbo",
            name = "Qwen 2.5 72B Turbo",
            providerId = "together",
            providerName = "Together AI",
            contextWindow = 131072,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Alibaba's Qwen 2.5 72B model in Turbo variant for fast inference",
            developer = "Alibaba Cloud"
        ),
        CloudModel(
            id = "google/gemma-2-27b-it",
            name = "Gemma 2 27B IT",
            providerId = "together",
            providerName = "Together AI",
            contextWindow = 8192,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Google's open Gemma 2 model, instruction-tuned, 27B parameters",
            developer = "Google"
        ),
        CloudModel(
            id = "mistralai/Mixtral-8x22B-Instruct-v0.1",
            name = "Mixtral 8x22B Instruct",
            providerId = "together",
            providerName = "Together AI",
            contextWindow = 65536,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Mistral's Mixtral MoE 8x22B sparse mixture-of-experts model",
            developer = "Mistral AI"
        )
    )

    override fun getCachedModels(): List<CloudModel> = cachedModels

    override fun getDefaultModel(): String = "meta-llama/Llama-3.3-70B-Instruct-Turbo"

    override fun getBaseUrl(): String = "https://api.together.xyz"

    override suspend fun listModels(): List<CloudModel> = withContext(Dispatchers.IO) {
        val apiKey = providerRepository.getActiveApiKey(providerId)
        if (apiKey == null) {
            return@withContext cachedModels
        }
        try {
            val request = Request.Builder()
                .url("${getBaseUrl()}/models")
                .addHeader("Authorization", "Bearer $apiKey")
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
                val id = obj.get("id")?.asString ?: continue
                val contextWindow = obj.get("context_length")?.asInt ?: 8192
                val description = obj.get("description")?.asString ?: ""
                val pricing = obj.getAsJsonObject("pricing")
                val inputPrice = pricing?.get("input")?.asDouble ?: 0.0
                val outputPrice = pricing?.get("output")?.asDouble ?: 0.0
                val name = id.substringAfterLast("/").replace("-", " ").replaceFirstChar { it.uppercase() }

                fetched.add(
                    CloudModel(
                        id = id,
                        name = name,
                        providerId = "together",
                        providerName = "Together AI",
                        contextWindow = contextWindow,
                        description = description,
                        pricingPer1kInput = inputPrice,
                        pricingPer1kOutput = outputPrice,
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
            ?: throw IllegalStateException("API key not configured for Together AI")

        val requestBody = buildOpenAiBody(request, stream = false)

        val httpRequest = Request.Builder()
            .url("${getBaseUrl()}/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body?.string()
            ?: throw RuntimeException("Empty response from Together AI")

        if (!response.isSuccessful) {
            throw RuntimeException("Together AI API error ${response.code}: $responseBody")
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
            ?: throw IllegalStateException("API key not configured for Together AI")

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
            throw RuntimeException("Together AI API error ${response.code}: $errorBody")
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
                            if (finishReason != null && !finishReason.isJsonNull && finishReason.asString != "null") {
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
