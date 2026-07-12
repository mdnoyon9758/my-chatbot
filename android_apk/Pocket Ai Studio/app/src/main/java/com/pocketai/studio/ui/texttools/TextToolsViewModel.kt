package com.pocketai.studio.ui.texttools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketai.studio.ai.engine.AiEngine
import com.pocketai.studio.domain.model.InferenceConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TextToolsUiState(
    val inputText: String = "",
    val outputText: String = "",
    val selectedTool: TextTool = TextTool.SUMMARIZE,
    val isProcessing: Boolean = false
)

enum class TextTool(val displayName: String, val prompt: String) {
    SUMMARIZE("Summarize", "Summarize the following text concisely:\n\n"),
    REWRITE("Rewrite", "Rewrite the following text with improved clarity:\n\n"),
    GRAMMAR("Grammar", "Fix grammar and spelling in the following text:\n\n"),
    TRANSLATE("Translate", "Translate the following text to English:\n\n"),
    EXPAND("Expand", "Expand the following text with more details:\n\n"),
    SHORTEN("Shorten", "Make the following text more concise:\n\n"),
    BULLET_POINTS("Bullet Points", "Convert the following text into bullet points:\n\n")
}

@HiltViewModel
class TextToolsViewModel @Inject constructor(
    private val aiEngine: AiEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(TextToolsUiState())
    val uiState: StateFlow<TextToolsUiState> = _uiState.asStateFlow()

    fun setInputText(text: String) { _uiState.update { it.copy(inputText = text) } }
    fun selectTool(tool: TextTool) { _uiState.update { it.copy(selectedTool = tool) } }

    fun process() {
        val input = _uiState.value.inputText
        if (input.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, outputText = "") }
            try {
                val prompt = _uiState.value.selectedTool.prompt + input
                val resultFlow = aiEngine.generate(prompt, config = InferenceConfig(maxTokens = 1024))
                var result = ""
                resultFlow.collect { token -> result += token }
                _uiState.update { it.copy(outputText = result, isProcessing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(outputText = "Error: ${e.message}", isProcessing = false) }
            }
        }
    }
}