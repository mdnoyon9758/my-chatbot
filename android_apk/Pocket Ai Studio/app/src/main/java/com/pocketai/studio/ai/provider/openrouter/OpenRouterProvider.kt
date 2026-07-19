package com.pocketai.studio.ai.provider.openrouter

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

class OpenRouterProvider(
    private val providerRepository: ProviderRepository
) : AiProvider {

    override val providerId: String = "openrouter"
    override val displayName: String = "OpenRouter"
    override val website: String = "https://openrouter.ai"
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
            id = "openai/gpt-4o",
            name = "GPT-4o",
            providerId = "openrouter",
            providerName = "OpenRouter",
            contextWindow = 128000,
            maxOutputTokens = 16384,
            pricingPer1kInput = 0.0025,
            pricingPer1kOutput = 0.01,
            isFree = false,
            capabilities = listOf("chat", "streaming", "vision"),
            isVision = true,
            isStreaming = true,
            description = "OpenAI's flagship multimodal model routed through OpenRouter",
            developer = "OpenAI"
        ),
        CloudModel(
            id = "openai/gpt-4o-mini",
            name = "GPT-4o Mini",
            providerId = "openrouter",
            providerName = "OpenRouter",
            contextWindow = 128000,
            maxOutputTokens = 16384,
            pricingPer1kInput = 0.00015,
            pricingPer1kOutput = 0.0006,
            isFree = false,
            capabilities = listOf("chat", "streaming", "vision"),
            isVision = true,
            isStreaming = true,
            description = "OpenAI's cost-efficient small model with broad capability",
            developer = "OpenAI"
        ),
        CloudModel(
            id = "anthropic/claude-sonnet-4",
            name = "Claude Sonnet 4",
            providerId = "openrouter",
            providerName = "OpenRouter",
            contextWindow = 200000,
            maxOutputTokens = 8192,
            pricingPer1kInput = 0.003,
            pricingPer1kOutput = 0.015,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Anthropic's Claude Sonnet 4 model for balanced performance",
            developer = "Anthropic"
        ),
        CloudModel(
            id = "anthropic/claude-3.5-sonnet",
            name = "Claude 3.5 Sonnet",
            providerId = "openrouter",
            providerName = "OpenRouter",
            contextWindow = 200000,
            maxOutputTokens = 8192,
            pricingPer1kInput = 0.003,
            pricingPer1kOutput = 0.015,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = true,
            isStreaming = true,
            description = "Anthropic's Claude 3.5 Sonnet with excellent reasoning and coding",
            developer = "Anthropic"
        ),
        CloudModel(
            id = "google/gemini-2.0-flash",
            name = "Gemini 2.0 Flash",
            providerId = "openrouter",
            providerName = "OpenRouter",
            contextWindow = 1048576,
            maxOutputTokens = 8192,
            pricingPer1kInput = 0.0001,
            pricingPer1kOutput = 0.0004,
            isFree = false,
            capabilities = listOf("chat", "streaming", "vision"),
            isVision = true,
            isStreaming = true,
            description = "Google's fast, multimodal Gemini 2.0 Flash with 1M context",
            developer = "Google"
        ),
        CloudModel(
            id = "meta-llama/llama-3.3-70b-instruct",
            name = "Llama 3.3 70B Instruct",
            providerId = "openrouter",
            providerName = "OpenRouter",
            contextWindow = 131072,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Meta's Llama 3.3 70B instruction-tuned model",
            developer = "Meta"
        ),
        CloudModel(
            id = "deepseek/deepseek-chat",
            name = "DeepSeek Chat",
            providerId = "openrouter",
            providerName = "OpenRouter",
            contextWindow = 128000,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "DeepSeek's general-purpose chat model",
            developer = "DeepSeek"
        ),
        CloudModel(
            id = "mistralai/mistral-small-3.1-24b",
            name = "Mistral Small 3.1 24B",
            providerId = "openrouter",
            providerName = "OpenRouter",
            contextWindow = 32000,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Mistral's lightweight 24B model for fast inference",
            developer = "Mistral AI"
        ),
        CloudModel(
            id = "qwen/qwen-2.5-72b-instruct",
            name = "Qwen 2.5 72B Instruct",
            providerId = "openrouter",
            providerName = "OpenRouter",
            contextWindow = 131072,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Alibaba's Qwen 2.5 72B instruct model",
            developer = "Alibaba Cloud"
        ),
        CloudModel(
            id = "cohere/command-r-plus",
            name = "Command R+",
            providerId = "openrouter",
            providerName = "OpenRouter",
            contextWindow = 128000,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming", "rag"),
            isVision = false,
            isStreaming = true,
            description = "Cohere's Command R+ with RAG capabilities via OpenRouter",
            developer = "Cohere"
        ),
        CloudModel(
            id = "nousresearch/hermes-3-llama-3.1-405b",
            name = "Hermes 3 Llama 3.1 405B",
            providerId = "openrouter",
            providerName = "OpenRouter",
            contextWindow = 131072,
            maxOutputTokens = 4096,
            pricingPer1kInput = 0.0,
            pricingPer1kOutput = 0.0,
            isFree = false,
            capabilities = listOf("chat", "streaming"),
            isVision = false,
            isStreaming = true,
            description = "Nous Research's Hermes 3 fine-tune on Llama 3.1 405B",
            developer = "Nous Research"
        )
    )

    override fun getCachedModels(): List<CloudModel> = cachedModels

    override fun getDefaultModel(): String = "openai/gpt-4o"

    override fun getBaseUrl(): String = "https://openrouter.ai"

    override suspend fun listModels(): List<CloudModel> = withContext(Dispatchers.IO) {
        val apiKey = providerRepository.getActiveApiKey(providerId)
        if (apiKey == null) {
            return@withContext cachedModels
        }
        try {
            val request = Request.Builder()
                .url("${getBaseUrl()}/api/v1/models")
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
                val contextWindow = obj.get("context_length")?.asInt ?: 8192
                val description = obj.get("description")?.asString ?: ""
                val name = id.substringAfter("/").replace("-", " ").replaceFirstChar { it.uppercase() }

                fetched.add(
                    CloudModel(
                        id = id,
                        name = name,
                        providerId = "openrouter",
                        providerName = "OpenRouter",
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
            ?: throw IllegalStateException("API key not configured for OpenRouter")

        val requestBody = buildOpenAiBody(request, stream = false)

        val httpRequest = Request.Builder()
            .url("${getBaseUrl()}/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://pocketai.studio")
            .addHeader("X-Title", "Pocket AI Studio")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body?.string()
            ?: throw RuntimeException("Empty response from OpenRouter")

        if (!response.isSuccessful) {
            throw RuntimeException("OpenRouter API error ${response.code}: $responseBody")
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
            ?: throw IllegalStateException("API key not configured for OpenRouter")

        val requestBody = buildOpenAiBody(request, stream = true)

        val httpRequest = Request.Builder()
            .url("${getBaseUrl()}/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .addHeader("HTTP-Referer", "https://pocketai.studio")
            .addHeader("X-Title", "Pocket AI Studio")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("OpenRouter API error ${response.code}: $errorBody")
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
