package com.pocketai.studio.ui.modelmanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketai.studio.ai.engine.AiEngine
import com.pocketai.studio.ai.modelmanager.ModelManager
import com.pocketai.studio.domain.model.AiModel
import com.pocketai.studio.domain.model.InferenceConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelManagerUiState(
    val installedModels: List<AiModel> = emptyList(),
    val availableModels: List<AiModel> = emptyList(),
    val isLoading: Boolean = true,
    val loadedModelName: String? = null,
    val message: String? = null
)

@HiltViewModel
class ModelManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val aiEngine: AiEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelManagerUiState())
    val uiState: StateFlow<ModelManagerUiState> = _uiState.asStateFlow()

    init {
        loadModels()
        _uiState.update { it.copy(loadedModelName = aiEngine.getCurrentModelName()) }
    }

    private fun loadModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val installed = modelManager.getInstalledModels()
            val available = modelManager.getAvailableModels()
            _uiState.update { it.copy(installedModels = installed, availableModels = available, isLoading = false) }
        }
    }

    fun downloadModel(model: AiModel) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(model.downloadUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun loadModel(model: AiModel) {
        viewModelScope.launch {
            val result = aiEngine.loadModel(model.filePath ?: "", InferenceConfig())
            result.onSuccess {
                _uiState.update { it.copy(loadedModelName = model.name, message = "Model loaded: ${model.name}") }
            }.onFailure { e ->
                _uiState.update { it.copy(message = "Failed to load: ${e.message}") }
            }
        }
    }

    fun deleteModel(model: AiModel) {
        viewModelScope.launch {
            val deleted = modelManager.deleteModel(model)
            if (deleted) {
                _uiState.update { it.copy(message = "Deleted: ${model.name}") }
                loadModels()
            } else {
                _uiState.update { it.copy(message = "Failed to delete: ${model.name}") }
            }
        }
    }

    fun clearMessage() { _uiState.update { it.copy(message = null) } }
    fun refresh() { loadModels() }
}