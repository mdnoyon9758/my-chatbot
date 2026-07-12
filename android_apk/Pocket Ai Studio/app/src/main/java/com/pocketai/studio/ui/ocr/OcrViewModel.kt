package com.pocketai.studio.ui.ocr

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketai.studio.ai.engine.AiEngine
import com.pocketai.studio.domain.model.InferenceConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class OcrUiState(
    val extractedText: String = "",
    val isProcessing: Boolean = false,
    val selectedImageUri: Uri? = null,
    val message: String? = null
)

@HiltViewModel
class OcrViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiEngine: AiEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    fun processImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, selectedImageUri = uri, message = null) }
            try {
                val text = withContext(kotlinx.coroutines.Dispatchers.IO) { performOcr(uri) }
                _uiState.update { it.copy(extractedText = text, isProcessing = false, message = "Text extracted: ${text.length} chars") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, message = "Error: ${e.message}") }
            }
        }
    }

    private fun performOcr(uri: Uri): String {
        return "Sample extracted text from image.\n\n" +
               "In production, this would use ML Kit or Tesseract OCR for offline text recognition."
    }

    fun sendToChat(text: String) {
        viewModelScope.launch {
            try {
                val prompt = "Process this text: $text"
                aiEngine.generate(prompt, config = InferenceConfig(maxTokens = 512)).collect { }
            } catch (e: Exception) { }
        }
    }

    fun clearMessage() { _uiState.update { it.copy(message = null) } }
}