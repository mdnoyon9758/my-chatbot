package com.pocketai.studio.ui.modelmanager

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketai.studio.ai.engine.AiEngine
import com.pocketai.studio.ai.modelmanager.ModelManager
import com.pocketai.studio.domain.model.AiModel
import com.pocketai.studio.domain.model.DownloadStatus
import com.pocketai.studio.domain.model.InferenceConfig
import com.pocketai.studio.domain.repository.SettingsRepository
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
    val message: String? = null,
    val downloadProgress: Map<String, Float> = emptyMap(),
    val downloadStatus: Map<String, DownloadStatus> = emptyMap()
)

@HiltViewModel
class ModelManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val aiEngine: AiEngine,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelManagerUiState())
    val uiState: StateFlow<ModelManagerUiState> = _uiState.asStateFlow()

    private var currentConfig = InferenceConfig()

    init {
        loadModels()
        _uiState.update { it.copy(loadedModelName = aiEngine.getCurrentModelName()) }

        // Collect saved settings
        viewModelScope.launch {
            settingsRepository.inferenceConfig.collect { config ->
                currentConfig = config
            }
        }

        // Collect download progress and status
        viewModelScope.launch {
            modelManager.downloadProgress.collect { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
        }
        viewModelScope.launch {
            modelManager.downloadStatus.collect { status ->
                _uiState.update { it.copy(downloadStatus = status) }
                if (status.values.any { it == DownloadStatus.COMPLETED }) {
                    loadModels()
                }
            }
        }
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
        modelManager.downloadModel(model)
    }

    fun cancelDownload(modelId: String) {
        modelManager.cancelDownload(modelId)
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(message = "Importing model...") }
            val result = modelManager.importModelFromUri(uri)
            result.onSuccess { model ->
                _uiState.update { it.copy(message = "Imported: ${model.name}") }
                loadModels()
            }.onFailure { e ->
                _uiState.update { it.copy(message = "Import failed: ${e.message}") }
            }
        }
    }

    fun loadModel(model: AiModel) {
        viewModelScope.launch {
            // Use saved settings from SettingsRepository
            val result = aiEngine.loadModel(model.filePath ?: "", currentConfig)
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
