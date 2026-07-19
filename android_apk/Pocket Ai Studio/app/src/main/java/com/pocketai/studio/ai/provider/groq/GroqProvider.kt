package com.pocketai.studio.ai.provider.groq

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pocketai.studio.ai.provider.AiProvider
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
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class GroqProvider @Inject constructor(
    private val providerRepository: ProviderRepository
) : AiProvider {

    override val providerId: String = "groq"
    override val displayName: String = "Groq"
    override val website: String = "https://groq.com"
    override val requiresApiKey: Boolean = true
    override val supportsStreaming: Boolean = true

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cachedModels: List<CloudModel> = listOf(
        CloudModel(
            id = "llama3-70b-8192",
            name = "Llama 3 70B",
            providerId = providerId,
            providerName = displayName,
            contextWindow = 8192,
            developer = "Meta",
            isFree = true,
            description = "Meta's flagship 70B parameter language model"
        ),
        CloudModel(
            id = "llama3-8b-8192",
            name = "Llama 3 8B",
            providerId = providerId,
            providerName = displayName,
            contextWindow = 8192,
            isFree = true,
            description = "Meta's efficient 8B parameter model"
        ),
        CloudModel(
            id = "mixtral-8x7b-32768",
            name = "Mixtral 8x7B",
            providerId = providerId,
            providerName = displayName,
            contextWindow = 32768,
            isFree = true,
            description = "Mistral's mixture-of-experts model with 32K context"
        ),
        CloudModel(
            id = "gemma2-9b-it",
            name = "Gemma 2 9B",
            providerId = providerId,
            providerName = displayName,
            contextWindow = 8192,
            isFree = true,
            description = "Google's lightweight open model"
        ),
        CloudModel(
            id = "llama-3.3-70b-versatile",
            name = "Llama 3.3 70B",
            providerId = providerId,
            providerName = displayName,
            contextWindow = 32768,
            isFree = true,
            description = "Meta's versatile 70B model with 32K context"
        ),
        CloudModel(
            id = "llama-3.1-8b-instant",
            name = "Llama 3.1 8B Instant",
            providerId = providerId,
            providerName = displayName,
            contextWindow = 131072,
            isFree = true,
            description = "Meta's fast 8B model with 128K context"
        ),
        CloudModel(
            id = "deepseek-r1-distill-llama-70b",
            name = "DeepSeek R1 Distill Llama 70B",
            providerId = providerId,
            providerName = displayName,
            contextWindow = 131072,
            isFree = true,
            description = "DeepSeek R1 reasoning distilled into Llama 70B"
        )
    )

    override fun getCachedModels(): List<CloudModel> = cachedModels

    override fun getDefaultModel(): String = "llama-3.3-70b-versatile"

    override fun getBaseUrl(): String = "https://api.groq.com/openai/v1"

    override suspend fun listModels(): List<CloudModel> {
        return try {
            val apiKey = providerRepository.getActiveApiKey(providerId) ?: return cachedModels
            val url = "${getBaseUrl()}/models"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val body = response.body?.string() ?: return cachedModels
            if (!response.isSuccessful) return cachedModels

            val json = JsonParser.parseString(body).asJsonObject
            val data = json.getAsJsonArray("data") ?: return cachedModels
            val result = mutableListOf<CloudModel>()
            for (element in data) {
                val obj = element.asJsonObject
                val modelId = obj.get("id")?.asString ?: continue
                val ownedBy = obj.get("owned_by")?.asString ?: ""
                result.add(
                    CloudModel(
                        id = modelId,
                        name = modelId,
                        providerId = providerId,
                        providerName = displayName,
                        developer = ownedBy,
                        isFree = true,
                        isStreaming = true
                    )
                )
            }
            result.ifEmpty { cachedModels }
        } catch (_: Exception) {
            cachedModels
        }
    }

    override suspend fun chat(request: ChatRequest): ChatResponse {
        val apiKey = providerRepository.getActiveApiKey(providerId)
            ?: return ChatResponse(
                content = "API key not configured. Please add your Groq API key in Settings.",
                model = request.model,
                providerId = providerId,
                finishReason = "error"
            )

        val jsonBody = buildRequestBody(request, stream = false)
        val url = "${getBaseUrl()}/chat/completions"
        val httpRequest = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string() ?: throw IOException("Empty response body from Groq API")
            if (!response.isSuccessful) {
                return@withContext ChatResponse(
                    content = "Groq API error (${response.code}): ${parseErrorBody(body)}",
                    model = request.model,
                    providerId = providerId,
                    finishReason = "error"
                )
            }
            parseResponse(body, request.model)
        }
    }

    override suspend fun chatStream(request: ChatRequest): Flow<String> = flow {
        val apiKey = providerRepository.getActiveApiKey(providerId)
            ?: throw IOException("API key not configured for Groq")

        val jsonBody = buildRequestBody(request, stream = true)
        val url = "${getBaseUrl()}/chat/completions"
        val httpRequest = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw IOException("Groq API stream error (${response.code}): $errorBody")
        }

        val source = response.body?.source()
            ?: throw IOException("Empty response body from Groq API stream")

        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.startsWith("data: ")) {
                    val data = trimmed.removePrefix("data: ").trim()
                    if (data.isNotEmpty() && data != "[DONE]") {
                        emit(data)
                    }
                }
            }
        } finally {
            source.close()
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(request: ChatRequest, stream: Boolean): String {
        val root = JsonObject()
        root.addProperty("model", request.model)
        root.addProperty("stream", stream)

        val messagesArray = JsonArray()
        // System prompt as a system-role message
        if (request.systemPrompt != null) {
            val systemMsg = JsonObject()
            systemMsg.addProperty("role", "system")
            systemMsg.addProperty("content", request.systemPrompt)
            messagesArray.add(systemMsg)
        }
        // All other messages
        for (msg in request.messages) {
            val msgObj = JsonObject()
            msgObj.addProperty("role", msg.role)
            msgObj.addProperty("content", msg.content)
            messagesArray.add(msgObj)
        }
        root.add("messages", messagesArray)

        val genConfig = JsonObject()
        genConfig.addProperty("temperature", request.temperature.toDouble())
        genConfig.addProperty("max_tokens", request.maxTokens)
        genConfig.addProperty("top_p", request.topP.toDouble())
        root.add("generationConfig", genConfig)

        return gson.toJson(root)
    }

    private fun parseResponse(jsonBody: String, model: String): ChatResponse {
        val root = JsonParser.parseString(jsonBody).asJsonObject

        val choices = root.getAsJsonArray("choices")
        val content = if (choices != null && choices.size() > 0) {
            val message = choices[0].asJsonObject.getAsJsonObject("message")
            message?.get("content")?.asString ?: ""
        } else ""

        val finishReason = if (choices != null && choices.size() > 0) {
            choices[0].asJsonObject.get("finish_reason")?.asString ?: "stop"
        } else "stop"

        val usage = root.getAsJsonObject("usage")
        val inputTokens = usage?.get("prompt_tokens")?.asInt ?: 0
        val outputTokens = usage?.get("completion_tokens")?.asInt ?: 0

        return ChatResponse(
            content = content,
            model = model,
            providerId = providerId,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            finishReason = finishReason
        )
    }

    private fun parseErrorBody(body: String): String {
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            val error = root.getAsJsonObject("error")
            error?.get("message")?.asString ?: body.take(200)
        } catch (_: Exception) {
            body.take(200)
        }
    }
}
