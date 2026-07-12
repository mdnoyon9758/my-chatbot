package com.pocketai.studio.ui.pdf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketai.studio.ai.engine.AiEngine
import com.pocketai.studio.domain.model.InferenceConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

data class PdfUiState(
    val pdfContent: String = "",
    val isLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val question: String = "",
    val answer: String = "",
    val message: String? = null
)

@HiltViewModel
class PdfViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiEngine: AiEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfUiState())
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            try {
                val text = withContext(Dispatchers.IO) { extractTextFromPdf(uri) }
                _uiState.update { it.copy(pdfContent = text, isLoaded = true, isLoading = false, message = "PDF loaded: ${text.length} characters") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, message = "Error: ${e.message}") }
            }
        }
    }

    private fun extractTextFromPdf(uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
        val reader = BufferedReader(InputStreamReader(inputStream))
        return reader.readText()
    }

    fun askQuestion(question: String) {
        _uiState.update { it.copy(question = question, answer = "") }
        viewModelScope.launch {
            try {
                val prompt = "Based on this document: ${_uiState.value.pdfContent.take(2000)}\n\nQuestion: $question\n\nAnswer:"
                val answerFlow = aiEngine.generate(prompt, config = InferenceConfig(maxTokens = 512))
                var answer = ""
                answerFlow.collect { token -> answer += token }
                _uiState.update { it.copy(answer = answer) }
            } catch (e: Exception) {
                _uiState.update { it.copy(answer = "Error: ${e.message}") }
            }
        }
    }

    fun clearMessage() { _uiState.update { it.copy(message = null) } }
}