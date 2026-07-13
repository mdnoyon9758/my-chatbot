package com.pocketai.studio.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketai.studio.ai.modelmanager.ModelManager
import com.pocketai.studio.domain.model.InferenceConfig
import com.pocketai.studio.domain.model.PerformanceMode
import com.pocketai.studio.domain.repository.SettingsRepository
import com.pocketai.studio.domain.repository.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontSize: Int = 16,
    val inferenceConfig: InferenceConfig = InferenceConfig(),
    val modelsCount: Int = 0,
    val storageUsed: String = "0 MB"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val modelManager: ModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsRepository.fontSize.collect { size ->
                _uiState.update { it.copy(fontSize = size) }
            }
        }
        viewModelScope.launch {
            settingsRepository.inferenceConfig.collect { config ->
                _uiState.update { it.copy(inferenceConfig = config) }
            }
        }
        viewModelScope.launch {
            val models = modelManager.getInstalledModels()
            val modelsSize = modelManager.getModelsSize()
            _uiState.update {
                it.copy(
                    modelsCount = models.size,
                    storageUsed = formatSize(modelsSize)
                )
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setFontSize(size: Int) {
        viewModelScope.launch { settingsRepository.setFontSize(size) }
    }

    fun setPerformanceMode(mode: PerformanceMode) {
        viewModelScope.launch { settingsRepository.setPerformanceMode(mode) }
    }

    fun setContextSize(size: Int) {
        viewModelScope.launch { settingsRepository.setContextSize(size) }
    }

    fun setThreads(threads: Int) {
        viewModelScope.launch { settingsRepository.setThreads(threads) }
    }

    fun setUseGpu(use: Boolean) {
        viewModelScope.launch { settingsRepository.setUseGpu(use) }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
    }
}
