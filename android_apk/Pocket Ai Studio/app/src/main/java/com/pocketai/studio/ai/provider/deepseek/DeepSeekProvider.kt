package com.pocketai.studio.ai.provider.deepseek

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

class DeepSeekProvider @Inject constructor(
    private val providerRepository: ProviderRepository
) : AiProvider {

    override val providerId: String = "deepseek"
    override val displayName: String = "DeepSeek"
    override val website: String = "https://deepseek.com"
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
            id = "deepseek-chat",
            name = "DeepSeek Chat",
            providerId = providerId,
            providerName = displayName,
            contextWindow = 64000,
            maxOutputTokens = 8192,
            pricingPer1kInput = 0.00014,
            pricingPer1kOutput = 0.00028,
            developer = "DeepSeek",
            description = "DeepSeek's general-purpose chat model with 64K context"
        ),
        CloudModel(
            id = "deepseek-reasoner",
            name = "DeepSeek Reasoner",
            providerId = providerId,
            providerName = displayName,
            contextWindow = 64000,
            maxOutputTokens = 8192,
            pricingPer1kInput = 0.00055,
            pricingPer1kOutput = 0.00219,
            developer = "DeepSeek",
            description = "DeepSeek's reasoning model with chain-of-thought"
        )
    )

    override fun getCachedModels(): List<CloudModel> = cachedModels

    override fun getDefaultModel(): String = "deepseek-chat"

    override fun getBaseUrl(): String = "https://api.deepseek.com/v1"

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
                content = "API key not configured. Please add your DeepSeek API key in Settings.",
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
            val body = response.body?.string() ?: throw IOException("Empty response body from DeepSeek API")
            if (!response.isSuccessful) {
                return@withContext ChatResponse(
                    content = "DeepSeek API error (${response.code}): ${parseErrorBody(body)}",
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
            ?: throw IOException("API key not configured for DeepSeek")

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
            throw IOException("DeepSeek API stream error (${response.code}): $errorBody")
        }

        val source = response.body?.source()
            ?: throw IOException("Empty response body from DeepSeek API stream")

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
            // Keep original role; DeepSeek supports "system", "user", "assistant"
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
