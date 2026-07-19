package com.pocketai.studio.ai.provider.google

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
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class GoogleProvider @Inject constructor(
    private val providerRepository: ProviderRepository
) : AiProvider {

    override val providerId: String = "google"
    override val displayName: String = "Google"
    override val website: String = "https://ai.google.dev"
    override val requiresApiKey: Boolean = true
    override val supportsStreaming: Boolean = true

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cachedModels: List<CloudModel> = listOf(
        CloudModel(
            id = "gemini-2.0-flash",
            name = "Gemini 2.0 Flash",
            providerId = providerId,
            providerName = displayName,
            contextWindow = 1048576,
            maxOutputTokens = 8192,
            pricingPer1kInput = 0.10,
            pricingPer1kOutput = 0.40,
            isFree = true,
            capabilities = listOf("chat", "vision"),
            isVision = true,
            description = "Google's fastest multimodal model with a 1M token context window"
        ),
        CloudModel(
            id = "gemini-2.0-flash-lite",
            name = "Gemini 2.0 Flash-Lite",
            providerId = providerId,
            providerName = displayName,
            contextWindow = 1048576,
            maxOutputTokens = 8192,
            isFree = true,
            capabilities = listOf("chat", "vision"),
            isVision = true,
            description = "Lightweight and cost-efficient version of Gemini 2.0 Flash"
        ),
        CloudModel(
            id = "gemini-1.5-flash",
            name = "Gemini 1.5 Flash",
            providerId = providerId,
            providerName = displayName,
            contextWindow = 1048576,
            maxOutputTokens = 8192,
            isFree = true,
            capabilities = listOf("chat", "vision"),
            isVision = true,
            description = "Fast multimodal model for high-volume tasks"
        ),
        CloudModel(
            id = "gemini-1.5-pro",
            name = "Gemini 1.5 Pro",
            providerId = providerId,
            providerName = displayName,
            contextWindow = 1048576,
            maxOutputTokens = 8192,
            capabilities = listOf("chat", "vision"),
            isVision = true,
            description = "Google's best performing multimodal model"
        ),
        CloudModel(
            id = "gemini-2.5-pro-exp-03-25",
            name = "Gemini 2.5 Pro Experimental",
            providerId = providerId,
            providerName = displayName,
            contextWindow = 1048576,
            maxOutputTokens = 65536,
            capabilities = listOf("chat", "vision"),
            isVision = true,
            description = "Google's most capable experimental model with thinking capabilities"
        )
    )

    override fun getCachedModels(): List<CloudModel> = cachedModels

    override fun getDefaultModel(): String = "gemini-2.0-flash"

    override fun getBaseUrl(): String = "https://generativelanguage.googleapis.com/v1beta"

    override suspend fun listModels(): List<CloudModel> {
        return try {
            val apiKey = providerRepository.getActiveApiKey(providerId) ?: return cachedModels
            val url = "${getBaseUrl()}/models?key=$apiKey"
            val request = Request.Builder().url(url).get().build()
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val body = response.body?.string() ?: return cachedModels
            if (!response.isSuccessful) return cachedModels

            val json = JsonParser.parseString(body).asJsonObject
            val modelsArray = json.getAsJsonArray("models") ?: return cachedModels
            val result = mutableListOf<CloudModel>()
            for (element in modelsArray) {
                val obj = element.asJsonObject
                val name = obj.get("name")?.asString ?: continue
                val modelId = name.removePrefix("models/")
                val supportedMethods = obj.getAsJsonArray("supportedGenerationMethods")
                    ?.map { it.asString } ?: emptyList()
                val hasContentGen = supportedMethods.contains("generateContent")
                if (!hasContentGen) continue
                result.add(
                    CloudModel(
                        id = modelId,
                        name = modelId,
                        providerId = providerId,
                        providerName = displayName,
                        capabilities = listOf("chat"),
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
                content = "API key not configured. Please add your Google API key in Settings.",
                model = request.model,
                providerId = providerId,
                finishReason = "error"
            )

        val jsonBody = buildRequestBody(request)
        val url = "${getBaseUrl()}/models/${request.model}:generateContent?key=$apiKey"
        val httpRequest = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string() ?: throw IOException("Empty response body from Google API")
            if (!response.isSuccessful) {
                return@withContext ChatResponse(
                    content = "Google API error (${response.code}): ${parseErrorBody(body)}",
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
            ?: throw IOException("API key not configured for Google")

        val jsonBody = buildRequestBody(request)
        val url = "${getBaseUrl()}/models/${request.model}:streamGenerateContent?alt=sse&key=$apiKey"
        val httpRequest = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw IOException("Google API stream error (${response.code}): $errorBody")
        }

        val source = response.body?.source()
            ?: throw IOException("Empty response body from Google API stream")

        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.startsWith("data: ")) {
                    val data = trimmed.removePrefix("data: ").trim()
                    if (data.isNotEmpty() && data != "[DONE]") {
                        // Extract text from the SSE JSON chunk
                        val text = try {
                            val root = JsonParser.parseString(data).asJsonObject
                            val candidates = root.getAsJsonArray("candidates")
                            if (candidates != null && candidates.size() > 0) {
                                val candidate = candidates[0].asJsonObject
                                val content = candidate.getAsJsonObject("content")
                                val parts = content?.getAsJsonArray("parts")
                                if (parts != null && parts.size() > 0) {
                                    parts[0].asJsonObject.get("text")?.asString
                                } else null
                            } else null
                        } catch (_: Exception) { null }
                        if (text != null && text.isNotEmpty()) {
                            emit(text)
                        }
                    }
                }
            }
        } finally {
            source.close()
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(request: ChatRequest): String {
        val root = JsonObject()

        // Build contents array from messages
        val contentsArray = com.google.gson.JsonArray()
        for (msg in request.messages) {
            val contentObj = JsonObject()
            // Convert role: "assistant" -> "model", everything else -> "user"
            contentObj.addProperty("role", if (msg.role == "assistant") "model" else "user")

            val partsArray = com.google.gson.JsonArray()
            val partObj = JsonObject()
            partObj.addProperty("text", msg.content)
            partsArray.add(partObj)
            contentObj.add("parts", partsArray)
            contentsArray.add(contentObj)
        }
        root.add("contents", contentsArray)

        // System instruction at top level (Gemini API)
        request.systemPrompt?.let { system ->
            val systemInstruction = JsonObject()
            val systemParts = com.google.gson.JsonArray()
            val systemPart = JsonObject()
            systemPart.addProperty("text", system)
            systemParts.add(systemPart)
            systemInstruction.add("parts", systemParts)
            root.add("system_instruction", systemInstruction)
        }

        // Generation config
        val genConfig = JsonObject()
        genConfig.addProperty("temperature", request.temperature.toDouble())
        genConfig.addProperty("maxOutputTokens", request.maxTokens)
        genConfig.addProperty("topP", request.topP.toDouble())
        root.add("generationConfig", genConfig)

        return gson.toJson(root)
    }

    private fun parseResponse(jsonBody: String, model: String): ChatResponse {
        val root = JsonParser.parseString(jsonBody).asJsonObject

        // Check for block reason / error
        val promptFeedback = root.getAsJsonObject("promptFeedback")
        val blockReason = promptFeedback?.get("blockReason")?.asString
        if (blockReason != null) {
            return ChatResponse(
                content = "Content blocked: $blockReason",
                model = model,
                providerId = providerId,
                finishReason = "blocked"
            )
        }

        // Extract text from candidates
        val candidates = root.getAsJsonArray("candidates")
        val sb = StringBuilder()
        var finishReason = "stop"

        if (candidates != null) {
            for (i in 0 until candidates.size()) {
                val candidate = candidates[i].asJsonObject
                val content = candidate.getAsJsonObject("content")
                val parts = content?.getAsJsonArray("parts")
                if (parts != null) {
                    for (j in 0 until parts.size()) {
                        val text = parts[j].asJsonObject.get("text")?.asString
                        if (text != null) sb.append(text)
                    }
                }
                // Use the finish reason from the first candidate
                if (i == 0 && candidate.has("finishReason")) {
                    finishReason = candidate.get("finishReason").asString.lowercase()
                }
            }
        }

        // Usage metadata
        val usage = root.getAsJsonObject("usageMetadata")
        val inputTokens = usage?.get("promptTokenCount")?.asInt ?: 0
        val outputTokens = usage?.get("candidatesTokenCount")?.asInt ?: 0

        return ChatResponse(
            content = sb.toString(),
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
