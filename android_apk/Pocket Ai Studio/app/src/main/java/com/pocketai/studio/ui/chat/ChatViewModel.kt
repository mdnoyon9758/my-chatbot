package com.pocketai.studio.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketai.studio.ai.engine.AiEngine
import com.pocketai.studio.ai.modelmanager.ModelManager
import com.pocketai.studio.domain.model.ChatMessage
import com.pocketai.studio.domain.model.ChatSession
import com.pocketai.studio.domain.model.InferenceConfig
import com.pocketai.studio.domain.model.MessageRole
import com.pocketai.studio.domain.repository.ChatRepository
import com.pocketai.studio.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val session: ChatSession? = null,
    val isGenerating: Boolean = false,
    val currentResponse: String = "",
    val tokenCount: Int = 0,
    val error: String? = null,
    val availableModels: List<String> = emptyList(),
    val selectedModel: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val aiEngine: AiEngine,
    private val modelManager: ModelManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var inferenceConfig = InferenceConfig()

    init {
        viewModelScope.launch {
            settingsRepository.inferenceConfig.collect { config ->
                inferenceConfig = config
            }
        }
        viewModelScope.launch {
            val models = modelManager.getInstalledModels()
            _uiState.update {
                it.copy(
                    availableModels = models.map { m -> m.name },
                    selectedModel = models.firstOrNull()?.name ?: ""
                )
            }
        }
    }

    fun loadChat(chatId: String?) {
        if (chatId == null) return
        viewModelScope.launch {
            chatRepository.getMessagesByChatId(chatId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
        viewModelScope.launch {
            chatRepository.getSessionById(chatId).collect { session ->
                _uiState.update { it.copy(session = session) }
            }
        }
    }

    fun sendMessage(text: String, chatId: String?) {
        viewModelScope.launch {
            val sessionId = if (chatId == null) {
                val session = chatRepository.createSession(
                    title = text.take(50),
                    modelId = _uiState.value.selectedModel,
                    modelName = _uiState.value.selectedModel
                )
                _uiState.update { it.copy(session = session) }
                session.id
            } else chatId

            chatRepository.insertMessage(sessionId, "USER", text)
            _uiState.update { it.copy(isGenerating = true, currentResponse = "", error = null) }

            val systemPrompt = "You are Pocket AI, a helpful local AI assistant. Be concise and accurate."
            var fullResponse = ""
            var tokenCount = 0

            try {
                aiEngine.generate(text, systemPrompt, inferenceConfig).collect { token ->
                    fullResponse += token
                    tokenCount++
                    _uiState.update { it.copy(currentResponse = fullResponse, tokenCount = tokenCount) }
                }
                chatRepository.insertMessage(sessionId, "ASSISTANT", fullResponse, tokenCount)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isGenerating = false, currentResponse = "") }
            }
        }
    }

    fun stopGeneration() {
        aiEngine.stopGeneration()
        _uiState.update { it.copy(isGenerating = false) }
    }

    fun selectModel(modelName: String) {
        _uiState.update { it.copy(selectedModel = modelName) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}