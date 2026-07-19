package com.pocketai.studio.data.repository

import com.pocketai.studio.ai.provider.AiProvider
import com.pocketai.studio.ai.provider.openai.OpenAiProvider
import com.pocketai.studio.ai.provider.anthropic.AnthropicProvider
import com.pocketai.studio.ai.provider.google.GoogleProvider
import com.pocketai.studio.ai.provider.groq.GroqProvider
import com.pocketai.studio.ai.provider.deepseek.DeepSeekProvider
import com.pocketai.studio.ai.provider.mistral.MistralProvider
import com.pocketai.studio.ai.provider.cohere.CohereProvider
import com.pocketai.studio.ai.provider.perplexity.PerplexityProvider
import com.pocketai.studio.ai.provider.together.TogetherProvider
import com.pocketai.studio.ai.provider.openrouter.OpenRouterProvider
import com.pocketai.studio.data.local.dao.ApiKeyDao
import com.pocketai.studio.data.local.entity.ApiKeyEntity
import com.pocketai.studio.domain.model.CloudModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

@Singleton
class ProviderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiKeyDao: ApiKeyDao
) {
    // All providers are created here
    private val providers: Map<String, AiProvider> = listOf(
        OpenAiProvider(this),
        AnthropicProvider(this),
        GoogleProvider(this),
        GroqProvider(this),
        DeepSeekProvider(this),
        MistralProvider(this),
        CohereProvider(this),
        PerplexityProvider(this),
        TogetherProvider(this),
        OpenRouterProvider(this)
    ).associateBy { it.providerId }

    // Encrypted storage for API keys
    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_api_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getAllProviders(): Map<String, AiProvider> = providers

    fun getProvider(providerId: String): AiProvider? = providers[providerId]

    fun getAllCloudModels(): List<CloudModel> = providers.values.flatMap { it.getCachedModels() }

    suspend fun getActiveApiKey(providerId: String): String? {
        // Try encrypted prefs first, fall back to Room
        val encrypted = securePrefs.getString(providerId, null)
        if (encrypted != null) return encrypted
        return apiKeyDao.getActiveApiKey(providerId)
    }

    suspend fun saveApiKey(providerId: String, apiKey: String) {
        securePrefs.edit().putString(providerId, apiKey).apply()
        apiKeyDao.insertKey(ApiKeyEntity(providerId = providerId, apiKey = apiKey))
    }

    suspend fun deleteApiKey(providerId: String) {
        securePrefs.edit().remove(providerId).apply()
        apiKeyDao.deleteKey(providerId)
    }

    fun hasApiKey(providerId: String): Boolean = securePrefs.contains(providerId)

    fun getAllApiKeysFlow(): Flow<List<String>> = apiKeyDao.getAllKeys().map { entities ->
        entities.map { it.providerId }
    }
}
