package com.pocketai.studio.ai.arena

import com.pocketai.studio.ai.provider.ChatMessage
import com.pocketai.studio.ai.provider.ChatRequest
import com.pocketai.studio.data.repository.ProviderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed class ArenaProgress {
    data class ModelStarted(val modelId: String, val providerId: String) : ArenaProgress()
    data class Token(val modelId: String, val providerId: String, val token: String) : ArenaProgress()
    data class ModelComplete(val modelId: String, val providerId: String, val fullResponse: String) : ArenaProgress()
    data class ModelError(val modelId: String, val providerId: String, val error: String) : ArenaProgress()
    data class AllComplete(val results: Map<String, String>) : ArenaProgress()
}

@Singleton
class ModelArena @Inject constructor(
    private val providerRepository: ProviderRepository
) {
    /**
     * Compare responses from multiple models by sending the same question to all of them in parallel.
     * @param models List of (providerId, modelId) pairs identifying which models to query
     * @param question The prompt/question to send to all models
     * @return Flow of [ArenaProgress] events as responses come in
     */
    fun compare(
        models: List<Pair<String, String>>,
        question: String
    ): Flow<ArenaProgress> = channelFlow {
        val results = ConcurrentHashMap<String, String>()
        val producer = this // Capture ProducerScope for use inside launch blocks

        if (models.isEmpty()) {
            send(ArenaProgress.AllComplete(emptyMap()))
            return@channelFlow
        }

        coroutineScope {
            models.forEach { (providerId, modelId) ->
                launch(Dispatchers.IO) {
                    try {
                        producer.send(ArenaProgress.ModelStarted(modelId, providerId))

                        val provider = providerRepository.getProvider(providerId)
                        if (provider == null) {
                            producer.send(ArenaProgress.ModelError(modelId, providerId, "Provider '$providerId' not found"))
                            return@launch
                        }

                        val request = ChatRequest(
                            model = modelId,
                            messages = listOf(ChatMessage(role = "user", content = question)),
                            temperature = 0.7f,
                            maxTokens = 4096,
                            topP = 0.95f,
                            stream = true,
                            systemPrompt = "You are a helpful AI assistant. Respond to the user's question concisely and accurately."
                        )

                        val fullResponse = StringBuilder()

                        try {
                            provider.chatStream(request).collect { token ->
                                fullResponse.append(token)
                                producer.send(ArenaProgress.Token(modelId, providerId, token))
                            }
                        } catch (streamError: Exception) {
                            // If streaming fails, try non-streaming as fallback
                            try {
                                val fallbackRequest = request.copy(stream = false)
                                val response = provider.chat(fallbackRequest)
                                fullResponse.append(response.content)
                                producer.send(ArenaProgress.Token(modelId, providerId, response.content))
                            } catch (fallbackError: Exception) {
                                producer.send(ArenaProgress.ModelError(
                                    modelId, providerId,
                                    "Streaming and fallback both failed: ${fallbackError.message}"
                                ))
                                return@launch
                            }
                        }

                        val resultText = fullResponse.toString()
                        results[modelId] = resultText
                        producer.send(ArenaProgress.ModelComplete(modelId, providerId, resultText))
                    } catch (e: Exception) {
                        producer.send(ArenaProgress.ModelError(
                            modelId, providerId,
                            e.message ?: "Unknown error during comparison"
                        ))
                    }
                }
            }
        }

        send(ArenaProgress.AllComplete(results.toMap()))
    }
}
