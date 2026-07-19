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
import com.pocketai.studio.ai.provider.ChatMessage as ProviderChatMessage
import com.pocketai.studio.ai.provider.ChatRequest
import com.pocketai.studio.data.repository.ProviderRepository
import com.pocketai.studio.domain.model.Attachment
import com.pocketai.studio.domain.model.AttachmentType
import com.pocketai.studio.domain.model.ChatMessage
import com.pocketai.studio.domain.model.ChatSession
import com.pocketai.studio.domain.model.InferenceConfig
import com.pocketai.studio.domain.model.MessageRole
import com.pocketai.studio.domain.model.ModelOption
import com.pocketai.studio.domain.repository.ChatRepository
import com.pocketai.studio.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val session: ChatSession? = null,
    val isGenerating: Boolean = false,
    val currentResponse: String = "",
    val tokenCount: Int = 0,
    val error: String? = null,
    /** Grouped model options: "Cloud Providers" then "On-device" */
    val modelOptions: List<ModelOption> = emptyList(),
    val selectedModelKey: String = "",
    val isRefreshingModels: Boolean = false,
    val pendingAttachment: Attachment? = null,
    val isProcessingAttachment: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val aiEngine: AiEngine,
    private val modelManager: ModelManager,
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var inferenceConfig = InferenceConfig()

    init {
        viewModelScope.launch {
            settingsRepository.inferenceConfig.collect { config -> inferenceConfig = config }
        }
        refreshModelOptions()
    }

    // ─────────────────────────────────────────────────────────
    // Model options — cloud + local
    // ─────────────────────────────────────────────────────────

    /**
     * Refresh the list of available model options.
     * Cloud models are loaded dynamically from providers that have an API key saved.
     */
    fun refreshModelOptions() {
        viewModelScope.launch {
            if (_uiState.value.isRefreshingModels) return@launch
            _uiState.update { it.copy(isRefreshingModels = true) }

            val options = mutableListOf<ModelOption>()

            // 1. Cloud models from every provider
            val providers = providerRepository.getAllProviders()
            for ((_, provider) in providers) {
                val apiKey = providerRepository.getActiveApiKey(provider.providerId)
                if (apiKey == null) continue

                // Try to fetch dynamic models; fall back to cached on failure
                val models = try {
                    provider.listModels()
                } catch (_: Exception) {
                    provider.getCachedModels()
                }
                options.addAll(models.map { m ->
                    ModelOption.Cloud(
                        providerId = provider.providerId,
                        providerName = provider.displayName,
                        modelId = m.id,
                        modelName = m.name,
                        isVision = m.isVision,
                        description = m.description
                    )
                })
            }

            // 2. Local GGUF models
            val localModels = modelManager.getInstalledModels()
            options.addAll(localModels.map { m ->
                ModelOption.Local(name = m.name, filePath = m.filePath)
            })

            // Auto-select: keep previous if still present, else first
            val prev = _uiState.value.selectedModelKey
            val first = options.firstOrNull()?.key ?: ""
            val selected = if (options.any { it.key == prev }) prev else first

            _uiState.update {
                it.copy(
                    modelOptions = options,
                    selectedModelKey = selected,
                    isRefreshingModels = false
                )
            }
        }
    }

    fun selectModel(key: String) {
        _uiState.update { it.copy(selectedModelKey = key) }

        // If a local model was picked, make sure it's loaded (async)
        viewModelScope.launch {
            val option = _uiState.value.modelOptions.firstOrNull { it.key == key } ?: return@launch
            if (option is ModelOption.Local) {
                if (option.filePath != null && option.filePath.isNotBlank()
                    && aiEngine.getCurrentModelName() != option.name) {
                    aiEngine.loadModel(option.filePath, inferenceConfig)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Chat loading
    // ─────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────
    // Attachments
    // ─────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────
    // Send message
    // ─────────────────────────────────────────────────────────

    fun sendMessage(text: String, chatId: String?) {
        val attachment = _uiState.value.pendingAttachment
        val isSlashCommand = text.startsWith("/")

        viewModelScope.launch {
            // Determine the selected model option
            val modelOption = _uiState.value.modelOptions.firstOrNull { it.key == _uiState.value.selectedModelKey }

            val selectedModelId = modelOption?.let {
                when (it) {
                    is ModelOption.Cloud -> it.modelId
                    is ModelOption.Local -> it.name
                }
            } ?: ""

            val selectedModelName = modelOption?.displayName ?: ""

            val sessionId = if (chatId == null) {
                val session = chatRepository.createSession(
                    title = text.take(50),
                    modelId = selectedModelId,
                    modelName = selectedModelName
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
                    else -> handleNormalChat(sessionId, text, modelOption)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Unknown error") }
            } finally {
                _uiState.update { it.copy(isGenerating = false, currentResponse = "") }
            }
        }
    }

    private suspend fun handleNormalChat(sessionId: String, text: String, modelOption: ModelOption?) {
        when (modelOption) {
            is ModelOption.Cloud -> handleCloudChat(sessionId, text, modelOption)
            is ModelOption.Local -> handleLocalChat(sessionId, text, modelOption)
            null -> handleLocalChat(sessionId, text, null)
        }
    }

    // ─────────────────────────────────────────────────────────
    // Cloud provider chat (streaming)
    // ─────────────────────────────────────────────────────────

    private suspend fun handleCloudChat(sessionId: String, text: String, option: ModelOption.Cloud) {
        val provider = providerRepository.getProvider(option.providerId)
            ?: throw Exception("Provider '${option.providerId}' not found")

        val apiKey = providerRepository.getActiveApiKey(option.providerId)
        if (apiKey == null) {
            chatRepository.insertMessage(
                sessionId, "ASSISTANT",
                "⚠️ API key not configured for ${option.providerName}. Add it in Settings → API Providers."
            )
            return
        }

        // Build message history for context
        val historyMessages = _uiState.value.messages
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .map { msg ->
                ProviderChatMessage(
                    role = if (msg.role == MessageRole.USER) "user" else "assistant",
                    content = msg.content
                )
            }

        val request = ChatRequest(
            model = option.modelId,
            messages = historyMessages + ProviderChatMessage("user", text),
            temperature = inferenceConfig.temperature,
            maxTokens = inferenceConfig.maxTokens,
            topP = inferenceConfig.topP,
            stream = provider.supportsStreaming,
            systemPrompt = "You are Pocket AI, a helpful AI assistant. Be concise and accurate."
        )

        var fullResponse = ""
        var responseSaved = false

        if (provider.supportsStreaming) {
            try {
                provider.chatStream(request).collect { token ->
                    fullResponse += token
                    _uiState.update { it.copy(currentResponse = fullResponse) }
                }
            } catch (e: Exception) {
                // If streaming failed, try non-streaming as fallback
                fullResponse = ""
                _uiState.update { it.copy(currentResponse = "") }
            }
        }

        // If streaming was not attempted or returned no content, do a normal request
        if (fullResponse.isBlank()) {
            val nonStreamRequest = request.copy(stream = false)
            val response = provider.chat(nonStreamRequest)
            if (response.finishReason == "error") {
                chatRepository.insertMessage(sessionId, "ASSISTANT", response.content)
                responseSaved = true
            } else {
                fullResponse = response.content
            }
        }

        if (!responseSaved && fullResponse.isNotBlank()) {
            val tokenCount = fullResponse.length / 4 // rough estimate
            chatRepository.insertMessage(sessionId, "ASSISTANT", fullResponse, tokenCount)
        } else if (!responseSaved) {
            chatRepository.insertMessage(
                sessionId, "ASSISTANT",
                "⚠️ No response received from ${option.providerName}. Check your API key and try again."
            )
        }
    }

    // ─────────────────────────────────────────────────────────
    // Local GGUF chat (existing path, null-safe)
    // ─────────────────────────────────────────────────────────

    private suspend fun handleLocalChat(sessionId: String, text: String, option: ModelOption.Local?) {
        val localModel = option
        val systemPrompt = "You are Pocket AI, a helpful local AI assistant. Be concise and accurate."

        // If no local model selected or model key missing, try to use any loaded model
        if (!aiEngine.isModelLoaded()) {
            // Try to load first available local model automatically
            val models = modelManager.getInstalledModels()
            val modelToLoad = localModel?.let { m ->
                models.firstOrNull { it.name == m.name }
            } ?: models.firstOrNull()

            if (modelToLoad != null && modelToLoad.filePath != null && modelToLoad.filePath.isNotBlank()) {
                val result = aiEngine.loadModel(modelToLoad.filePath, inferenceConfig)
                if (result.isFailure) {
                    chatRepository.insertMessage(
                        sessionId, "ASSISTANT",
                        "⚠️ Could not load model: ${result.exceptionOrNull()?.message}. " +
                                "Download a model from Model Manager first, or select a cloud provider."
                    )
                    return
                }
            } else {
                chatRepository.insertMessage(
                    sessionId, "ASSISTANT",
                    "⚠️ No model selected and no local models available. " +
                            "Please download a model in Model Manager, or add an API key in Settings and select a cloud model."
                )
                return
            }
        }

        var fullResponse = ""
        var tokenCount = 0

        aiEngine.generate(text, systemPrompt, inferenceConfig).collect { token ->
            fullResponse += token
            tokenCount++
            _uiState.update { it.copy(currentResponse = fullResponse, tokenCount = tokenCount) }
        }

        if (fullResponse.isNotBlank() && !fullResponse.startsWith("[Error:")) {
            chatRepository.insertMessage(sessionId, "ASSISTANT", fullResponse, tokenCount)
        } else if (fullResponse.isNotBlank()) {
            chatRepository.insertMessage(sessionId, "ASSISTANT", fullResponse, 0)
        } else {
            chatRepository.insertMessage(
                sessionId, "ASSISTANT",
                "⚠️ Model returned empty response. Try selecting a different model."
            )
        }
    }

    // ─────────────────────────────────────────────────────────
    // Slash commands
    // ─────────────────────────────────────────────────────────

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
            chatRepository.insertMessage(
                sessionId, "ASSISTANT",
                "Please provide text after the command. Example: /$command your text here"
            )
            return
        }

        // Route through normal chat path which handles both cloud and local
        val option = resolveCurrentModelOption()
        handleNormalChat(sessionId, content, option)
        // Update the stored message with the tool used
        // (last inserted assistant message gets toolUsed — we'd need the id, skip for now)
    }

    // ─────────────────────────────────────────────────────────
    // Image & PDF handling (unchanged from original)
    // ─────────────────────────────────────────────────────────

    private suspend fun handleImageAttachment(sessionId: String, text: String, attachment: Attachment) {
        _uiState.update { it.copy(isProcessingAttachment = true) }
        try {
            val imageUri = Uri.parse(attachment.uri)
            val extractedText = withContext(Dispatchers.IO) { performOcr(imageUri) }

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
            val option = resolveCurrentModelOption()
            handleNormalChat(sessionId, prompt, option)
        } catch (e: Exception) {
            _uiState.update { it.copy(isProcessingAttachment = false, error = "OCR failed: ${e.message}") }
        }
    }

    private suspend fun performOcr(uri: Uri): String = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result.text); recognizer.close() }
                .addOnFailureListener { cont.resume(""); recognizer.close() }
        } catch (e: Exception) { cont.resume("") }
    }

    private suspend fun handlePdfAttachment(sessionId: String, text: String, attachment: Attachment) {
        _uiState.update { it.copy(isProcessingAttachment = true) }
        try {
            val pdfUri = Uri.parse(attachment.uri)
            val extractedText = withContext(Dispatchers.IO) { extractPdfText(pdfUri) }

            val prompt = buildString {
                if (extractedText.isNotBlank()) {
                    val truncated = if (extractedText.length > 8000) extractedText.take(8000) + "\n\n[...truncated]" else extractedText
                    append("Content from PDF '${attachment.name}':\n\n")
                    append(truncated)
                    append("\n\n")
                } else {
                    append("[PDF: ${attachment.name} - no text could be extracted]\n\n")
                }
                if (text.isNotBlank()) append(text) else append("What is this document about?")
            }
            _uiState.update { it.copy(isProcessingAttachment = false) }
            val option = resolveCurrentModelOption()
            handleNormalChat(sessionId, prompt, option)
        } catch (e: Exception) {
            _uiState.update { it.copy(isProcessingAttachment = false, error = "PDF extraction failed: ${e.message}") }
        }
    }

    private fun extractPdfText(uri: Uri): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return "Could not open PDF"
            val reader = com.itextpdf.kernel.pdf.PdfReader(inputStream)
            val document = com.itextpdf.kernel.pdf.PdfDocument(reader)
            val text = StringBuilder()
            for (i in 1..document.numberOfPages) {
                val strategy = SimpleTextExtractionStrategy()
                text.append(PdfTextExtractor.getTextFromPage(document.getPage(i), strategy))
                text.append('\n')
            }
            document.close()
            reader.close()
            inputStream.close()
            if (text.isBlank() || text.length < 10) "PDF appears to be image-based or empty." else text.toString()
        } catch (e: Exception) { "Error reading PDF: ${e.message}" }
    }

    // ─────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────

    fun stopGeneration() {
        aiEngine.stopGeneration()
        _uiState.update { it.copy(isGenerating = false) }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    private fun resolveCurrentModelOption(): ModelOption? {
        val key = _uiState.value.selectedModelKey
        return _uiState.value.modelOptions.firstOrNull { it.key == key }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }
}
