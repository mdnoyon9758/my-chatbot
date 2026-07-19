package com.pocketai.studio.ui.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pocketai.studio.ai.engine.AiEngine
import com.pocketai.studio.domain.model.InferenceConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume

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

    private suspend fun performOcr(uri: Uri): String = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val text = result.text
                    recognizer.close()
                    cont.resume(text)
                }
                .addOnFailureListener {
                    recognizer.close()
                    cont.resume("")
                }
        } catch (e: Exception) { cont.resume("") }
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