package com.pocketai.studio.ai.provider

import com.pocketai.studio.domain.model.CloudModel
import kotlinx.coroutines.flow.Flow

interface AiProvider {
    val providerId: String
    val displayName: String
    val website: String
    val requiresApiKey: Boolean
    val supportsStreaming: Boolean

    suspend fun listModels(): List<CloudModel>
    fun getCachedModels(): List<CloudModel>
    suspend fun chat(request: ChatRequest): ChatResponse
    suspend fun chatStream(request: ChatRequest): Flow<String>
    fun getDefaultModel(): String
    fun getBaseUrl(): String
}
