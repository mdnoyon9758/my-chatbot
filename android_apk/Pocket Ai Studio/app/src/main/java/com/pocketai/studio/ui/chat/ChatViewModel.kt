package com.pocketai.studio.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import kotlin.coroutines.resume

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
            // Auto-load the first model if none is loaded
            if (!aiEngine.isModelLoaded() && models.isNotEmpty()) {
                val firstModel = models.first()
                aiEngine.loadModel(firstModel.filePath ?: "", inferenceConfig)
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
        // Load the selected model if not already loaded
        viewModelScope.launch {
            val models = modelManager.getInstalledModels()
            val model = models.find { it.name == modelName }
            if (model != null && aiEngine.getCurrentModelName() != model.name) {
                aiEngine.loadModel(model.filePath ?: "", inferenceConfig)
            }
        }
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

            chatRepository.insertMessage(
                sessionId, "USER", text,
                attachments = if (attachment != null) listOf(attachment) else emptyList()
            )
            _uiState.update { it.copy(isGenerating = true, currentResponse = "", error = null, pendingAttachment = null) }

            try {
                when {
                    isSlashCommand -> handleSlashCommand(sessionId, text)
                    attachment?.type == AttachmentType.IMAGE -> handleImageAttachment(sessionId, text, attachment)
                    attachment?.type == AttachmentType.PDF -> handlePdfAttachment(sessionId, text, attachment)
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
        val systemPrompt = "You are Pocket AI, a helpful local AI assistant. Be concise and accurate."
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

        try {
            val imageUri = Uri.parse(attachment.uri)
            val extractedText = withContext(Dispatchers.IO) {
                performOcr(imageUri)
            }

            val prompt = buildString {
                if (extractedText.isNotBlank()) {
                    append("Text extracted from image '${attachment.name}':\n\n")
                    append(extractedText)
                    append("\n\n")
                } else {
                    append("[Image: ${attachment.name} - no text could be extracted]\n\n")
                }
                if (text.isNotBlank()) append(text) else append("Analyze this text.")
            }

            _uiState.update { it.copy(isProcessingAttachment = false) }
            handleNormalChat(sessionId, prompt)
        } catch (e: Exception) {
            _uiState.update { it.copy(isProcessingAttachment = false, error = "OCR failed: ${e.message}") }
        }
    }

    private suspend fun performOcr(uri: Uri): String = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    cont.resume(result.text)
                    recognizer.close()
                }
                .addOnFailureListener { e ->
                    cont.resume("")
                    recognizer.close()
                }
        } catch (e: Exception) {
            cont.resume("")
        }
    }

    private suspend fun handlePdfAttachment(sessionId: String, text: String, attachment: Attachment) {
        _uiState.update { it.copy(isProcessingAttachment = true) }

        try {
            val pdfUri = Uri.parse(attachment.uri)
            val extractedText = withContext(Dispatchers.IO) {
                extractPdfText(pdfUri)
            }

            val prompt = buildString {
                if (extractedText.isNotBlank()) {
                    val truncated = if (extractedText.length > 8000) {
                        extractedText.take(8000) + "\n\n[...truncated]"
                    } else {
                        extractedText
                    }
                    append("Content from PDF '${attachment.name}':\n\n")
                    append(truncated)
                    append("\n\n")
                } else {
                    append("[PDF: ${attachment.name} - no text could be extracted]\n\n")
                }
                if (text.isNotBlank()) append(text) else append("What is this document about?")
            }

            _uiState.update { it.copy(isProcessingAttachment = false) }
            handleNormalChat(sessionId, prompt)
        } catch (e: Exception) {
            _uiState.update { it.copy(isProcessingAttachment = false, error = "PDF extraction failed: ${e.message}") }
        }
    }

    private fun extractPdfText(uri: Uri): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return "Could not open PDF file"

            val reader = BufferedReader(InputStreamReader(inputStream))
            val text = reader.readText()
            reader.close()
            inputStream.close()

            if (text.isBlank() || text.length < 10) {
                "PDF appears to be image-based or empty."
            } else {
                text
            }
        } catch (e: Exception) {
            "Error reading PDF: ${e.message}"
        }
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
