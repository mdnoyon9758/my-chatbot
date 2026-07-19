package com.pocketai.studio.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketai.studio.data.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderUiState(
    val providerId: String,
    val displayName: String,
    val website: String,
    val hasApiKey: Boolean,
    val apiKey: String = "",
    val isEnabled: Boolean = true,
    val showField: Boolean = false
)

data class ProviderSettingsUiState(
    val providerStates: Map<String, ProviderUiState> = emptyMap(),
    val searchQuery: String = "",
    val isSaved: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class ProviderSettingsViewModel @Inject constructor(
    private val providerRepository: ProviderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderSettingsUiState())
    val uiState: StateFlow<ProviderSettingsUiState> = _uiState.asStateFlow()

    // Hard-coded provider metadata since our ProviderRepository doesn't expose display info directly
    private val providerMeta = listOf(
        ProviderMeta("openai", "OpenAI", "https://openai.com"),
        ProviderMeta("anthropic", "Anthropic", "https://anthropic.com"),
        ProviderMeta("google", "Google AI", "https://ai.google.dev"),
        ProviderMeta("groq", "Groq", "https://groq.com"),
        ProviderMeta("deepseek", "DeepSeek", "https://deepseek.com"),
        ProviderMeta("mistral", "Mistral AI", "https://mistral.ai"),
        ProviderMeta("cohere", "Cohere", "https://cohere.com"),
        ProviderMeta("perplexity", "Perplexity", "https://perplexity.ai"),
        ProviderMeta("together", "Together AI", "https://together.ai"),
        ProviderMeta("openrouter", "OpenRouter", "https://openrouter.ai")
    )

    init { loadProviders() }

    fun loadProviders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val map = mutableMapOf<String, ProviderUiState>()
            for (meta in providerMeta) {
                val hasKey = providerRepository.hasApiKey(meta.id)
                map[meta.id] = ProviderUiState(
                    providerId = meta.id,
                    displayName = meta.name,
                    website = meta.url,
                    hasApiKey = hasKey,
                    apiKey = if (hasKey) providerRepository.getActiveApiKey(meta.id) ?: "" else "",
                    isEnabled = hasKey,
                    showField = false
                )
            }
            _uiState.update { it.copy(providerStates = map, isLoading = false) }
        }
    }

    fun setSearchQuery(query: String) { _uiState.update { it.copy(searchQuery = query) } }

    fun toggleProvider(providerId: String) {
        _uiState.update { current ->
            val updated = current.providerStates.mapValues { (pid, state) ->
                if (pid == providerId) state.copy(showField = !state.showField)
                else state.copy(showField = false)
            }
            current.copy(providerStates = updated)
        }
    }

    fun setApiKey(providerId: String, key: String) {
        _uiState.update { current ->
            val updated = current.providerStates.mapValues { (pid, state) ->
                if (pid == providerId) state.copy(apiKey = key) else state
            }
            current.copy(providerStates = updated)
        }
    }

    fun saveApiKey(providerId: String) {
        viewModelScope.launch {
            val key = _uiState.value.providerStates[providerId]?.apiKey?.trim() ?: ""
            if (key.isNotBlank()) {
                providerRepository.saveApiKey(providerId, key)
                _uiState.update { current ->
                    val updated = current.providerStates.mapValues { (pid, state) ->
                        if (pid == providerId) state.copy(hasApiKey = true) else state
                    }
                    current.copy(providerStates = updated, isSaved = true)
                }
            }
        }
    }

    fun removeApiKey(providerId: String) {
        viewModelScope.launch {
            providerRepository.deleteApiKey(providerId)
            _uiState.update { current ->
                val updated = current.providerStates.mapValues { (pid, state) ->
                    if (pid == providerId) state.copy(hasApiKey = false, apiKey = "", showField = false) else state
                }
                current.copy(providerStates = updated, isSaved = true)
            }
        }
    }

    fun clearSaved() { _uiState.update { it.copy(isSaved = false) } }

    private data class ProviderMeta(val id: String, val name: String, val url: String)
}
