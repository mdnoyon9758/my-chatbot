package com.pocketai.studio.ui.document

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketai.studio.data.local.entity.DocumentEntity
import com.pocketai.studio.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentChatState(
    val documents: List<DocumentEntity> = emptyList(),
    val question: String = "",
    val answer: String = "",
    val sources: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DocumentChatViewModel @Inject constructor(
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DocumentChatState())
    val state: StateFlow<DocumentChatState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            documentRepository.getAllDocuments().collect { docs ->
                _state.update { it.copy(documents = docs) }
            }
        }
    }

    fun importDocument(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val fileName = uri.lastPathSegment ?: "document"
            val result = documentRepository.importDocument(uri, fileName)
            result.onSuccess {
                _state.update { it.copy(isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun askQuestion(question: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, question = question, answer = "", error = null) }
            try {
                val result = documentRepository.queryDocument(question)
                _state.update { it.copy(answer = result.answer, sources = result.sources, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch { documentRepository.deleteDocument(id) }
    }
}
