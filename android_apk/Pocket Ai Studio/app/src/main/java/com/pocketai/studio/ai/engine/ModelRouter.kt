package com.pocketai.studio.ai.engine

import com.pocketai.studio.ai.provider.ChatRequest
import com.pocketai.studio.ai.provider.ChatResponse
import com.pocketai.studio.data.repository.ProviderRepository
import com.pocketai.studio.domain.model.CloudModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

data class ModelRoute(
    val isCloud: Boolean,
    val providerId: String? = null,
    val modelId: String? = null
)

@Singleton
class ModelRouter @Inject constructor(
    private val providerRepository: ProviderRepository
) {
    companion object {
        const val LOCAL_PREFIX = "local:"
    }

    fun resolveModel(modelIdentifier: String): ModelRoute {
        if (modelIdentifier.startsWith(LOCAL_PREFIX)) {
            return ModelRoute(isCloud = false)
        }
        if (modelIdentifier.contains(":")) {
            val parts = modelIdentifier.split(":", limit = 2)
            return ModelRoute(isCloud = true, providerId = parts[0], modelId = parts[1])
        }
        return ModelRoute(isCloud = false)
    }

    fun getCloudModels(): List<CloudModel> = providerRepository.getAllCloudModels()

    fun formatModelKey(providerId: String, modelId: String): String = "$providerId:$modelId"
    fun formatLocalModelKey(modelName: String): String = "$LOCAL_PREFIX$modelName"

    suspend fun chat(request: ChatRequest): ChatResponse {
        val route = resolveModel(request.model)
        if (!route.isCloud || route.providerId == null || route.modelId == null) {
            return ChatResponse("Local model inference not available through cloud router", request.model, "local", finishReason = "error")
        }
        val provider = providerRepository.getProvider(route.providerId)
            ?: return ChatResponse("Provider ${route.providerId} not found", request.model, route.providerId, finishReason = "error")
        return provider.chat(request.copy(model = route.modelId))
    }

    suspend fun chatStream(request: ChatRequest): Flow<String> = flow {
        val route = resolveModel(request.model)
        if (!route.isCloud || route.providerId == null || route.modelId == null) {
            emit("[Error: Local model not supported in cloud router]")
            return@flow
        }
        val provider = providerRepository.getProvider(route.providerId)
            ?: run { emit("[Error: Provider not found]"); return@flow }
        val stream = provider.chatStream(request.copy(model = route.modelId))
        stream.collect { emit(it) }
    }
}
