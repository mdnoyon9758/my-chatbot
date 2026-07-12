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
    val storageUsed: String = "0 MB",
    val modelsCount: Int = 0,
    val chatsCount: Int = 0
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
            settingsRepository.themeMode.collect { _uiState.update { it.copy(themeMode = it.themeMode) } }
        }
        viewModelScope.launch {
            settingsRepository.fontSize.collect { _uiState.update { it.copy(fontSize = it.fontSize) } }
        }
        viewModelScope.launch {
            settingsRepository.inferenceConfig.collect { _uiState.update { it.copy(inferenceConfig = it.inferenceConfig) } }
        }
        viewModelScope.launch {
            val models = modelManager.getInstalledModels()
            val size = modelManager.getModelsSize()
            _uiState.update { it.copy(modelsCount = models.size, storageUsed = formatSize(size)) }
        }
    }

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settingsRepository.setThemeMode(mode) } }
    fun setFontSize(size: Int) { viewModelScope.launch { settingsRepository.setFontSize(size) } }
    fun setContextSize(size: Int) { viewModelScope.launch { settingsRepository.setContextSize(size) } }
    fun setThreads(threads: Int) { viewModelScope.launch { settingsRepository.setThreads(threads) } }
    fun setUseGpu(useGpu: Boolean) { viewModelScope.launch { settingsRepository.setUseGpu(useGpu) } }
    fun setPerformanceMode(mode: PerformanceMode) { viewModelScope.launch { settingsRepository.setPerformanceMode(mode) } }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
    }
}