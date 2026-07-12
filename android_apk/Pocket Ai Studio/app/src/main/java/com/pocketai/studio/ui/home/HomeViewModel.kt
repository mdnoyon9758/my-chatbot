package com.pocketai.studio.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketai.studio.ai.modelmanager.ModelManager
import com.pocketai.studio.domain.model.AiModel
import com.pocketai.studio.domain.model.ChatSession
import com.pocketai.studio.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val installedModels: List<AiModel> = emptyList(),
    val recentChats: List<ChatSession> = emptyList(),
    val isLoading: Boolean = true,
    val storageUsed: String = "0 MB",
    val storageTotal: String = "0 MB"
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val modelManager: ModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val models = modelManager.getInstalledModels()
            val storage = modelManager.getStorageInfo()
            val modelsSize = modelManager.getModelsSize()
            _uiState.update {
                it.copy(
                    installedModels = models,
                    storageUsed = formatSize(modelsSize),
                    storageTotal = formatSize(storage.first),
                    isLoading = false
                )
            }
        }

        viewModelScope.launch {
            chatRepository.getAllSessions().collect { chats ->
                _uiState.update { it.copy(recentChats = chats.take(10)) }
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch { chatRepository.deleteSession(chatId) }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
    }
}