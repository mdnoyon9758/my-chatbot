package com.pocketai.studio.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketai.studio.ai.engine.AiEngine
import com.pocketai.studio.ai.modelmanager.ModelManager
import com.pocketai.studio.domain.model.Attachment
import com.pocketai.studio.domain.model.AttachmentType
import com.pocketai.studio.domain.model.ChatMessage
import com.pocketai.studio.domain.model.ChatSession
import com.pocketai.studio.domain.model.InferenceConfig
import com.pocketai.studio.domain.model.MessageRole
import com.pocketai.studio.domain.repository.ChatRepository
import com.pocketai.studio.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val session: ChatSession? = null,
    val isGenerating: Boolean = false,
    val currentResponse: String = "",
    val tokenCount: Int = 0,
    val error: String? = null,
    val availableModels: List<String> = emptyList(),
    val selectedModel: String = "",
    val pendingAttachment: Attachment? = null,
    val isProcessingAttachment: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val aiEngine: AiEngine,
    private val modelManager: ModelManager,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
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

    fun selectModel(modelName: String) {
        _uiState.update { it.copy(selectedModel = modelName) }
    }

    fun attachImage(uri: Uri) {
        val name = getFileName(uri) ?: "image.jpg"
        _uiState.update { it.copy(pendingAttachment = Attachment(uri.toString(), AttachmentType.IMAGE, name)) }
    }

    fun attachPdf(uri: Uri) {
        val name = getFileName(uri) ?: "document.pdf"
        _uiState.update { it.copy(pendingAttachment = Attachment(uri.toString(), AttachmentType.PDF, name)) }
    }

    fun removeAttachment() {
        _uiState.update { it.copy(pendingAttachment = null) }
    }

    fun sendMessage(text: String, chatId: String?) {
        val attachment = _uiState.value.pendingAttachment
        val isSlashCommand = text.startsWith("/")

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

            // Save user message with attachment
            chatRepository.insertMessage(
                sessionId, "USER", text,
                attachments = if (attachment != null) listOf(attachment) else emptyList()
            )
            _uiState.update { it.copy(isGenerating = true, currentResponse = "", error = null, pendingAttachment = null) }

            try {
                when {
                    // Slash commands for text tools
                    isSlashCommand -> handleSlashCommand(sessionId, text)
                    // Image attachment → OCR
                    attachment?.type == AttachmentType.IMAGE -> handleImageAttachment(sessionId, text, attachment)
                    // PDF attachment → extract and answer
                    attachment?.type == AttachmentType.PDF -> handlePdfAttachment(sessionId, text, attachment)
                    // Normal chat
                    else -> handleNormalChat(sessionId, text)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isGenerating = false, currentResponse = "") }
            }
        }
    }

    private suspend fun handleNormalChat(sessionId: String, text: String) {
        val systemPrompt = "You are Pocket AI, a helpful local AI assistant. Be concise and accurate. You can help with text tasks, answer questions, and process files."
        var fullResponse = ""
        var tokenCount = 0

        aiEngine.generate(text, systemPrompt, inferenceConfig).collect { token ->
            fullResponse += token
            tokenCount++
            _uiState.update { it.copy(currentResponse = fullResponse, tokenCount = tokenCount) }
        }
        chatRepository.insertMessage(sessionId, "ASSISTANT", fullResponse, tokenCount)
    }

    private suspend fun handleSlashCommand(sessionId: String, text: String) {
        val parts = text.split(" ", limit = 2)
        val command = parts[0].lowercase().removePrefix("/")
        val content = parts.getOrElse(1) { "" }

        val systemPrompt = when (command) {
            "summarize" -> "Summarize the following text concisely, keeping key points:"
            "rewrite" -> "Rewrite the following text for clarity and better flow:"
            "grammar" -> "Fix all grammar, spelling, and punctuation errors in this text:"
            "translate" -> "Translate the following text to English (if already English, translate to Spanish):"
            "expand" -> "Expand the following text with more detail and context:"
            "shorten" -> "Shorten the following text while keeping the core meaning:"
            "bullets" -> "Convert the following text into concise bullet points:"
            else -> "Process the following text:"
        }

        if (content.isBlank()) {
            chatRepository.insertMessage(sessionId, "ASSISTANT", "Please provide text after the command. Example: /$command your text here")
            return
        }

        var fullResponse = ""
        var tokenCount = 0

        aiEngine.generate(content, systemPrompt, inferenceConfig).collect { token ->
            fullResponse += token
            tokenCount++
            _uiState.update { it.copy(currentResponse = fullResponse, tokenCount = tokenCount) }
        }
        chatRepository.insertMessage(sessionId, "ASSISTANT", fullResponse, tokenCount, toolUsed = command)
    }

    private suspend fun handleImageAttachment(sessionId: String, text: String, attachment: Attachment) {
        _uiState.update { it.copy(isProcessingAttachment = true) }

        // For now, include a note about the image in the prompt
        // In production, this would run OCR via ML Kit
        val prompt = buildString {
            append("[Image attached: ${attachment.name}]\n")
            if (text.isNotBlank()) append(text) else append("Describe what you see in this image.")
        }

        _uiState.update { it.copy(isProcessingAttachment = false) }
        handleNormalChat(sessionId, prompt)
    }

    private suspend fun handlePdfAttachment(sessionId: String, text: String, attachment: Attachment) {
        _uiState.update { it.copy(isProcessingAttachment = true) }

        // For now, include a note about the PDF in the prompt
        // In production, this would extract PDF content via iText
        val prompt = buildString {
            append("[PDF attached: ${attachment.name}]\n")
            if (text.isNotBlank()) append(text) else append("What is this document about?")
        }

        _uiState.update { it.copy(isProcessingAttachment = false) }
        handleNormalChat(sessionId, prompt)
    }

    fun stopGeneration() {
        aiEngine.stopGeneration()
        _uiState.update { it.copy(isGenerating = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
