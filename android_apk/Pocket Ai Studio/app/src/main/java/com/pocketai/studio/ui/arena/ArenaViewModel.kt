package com.pocketai.studio.ui.arena

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketai.studio.ai.arena.ArenaProgress
import com.pocketai.studio.ai.arena.ArenaRanker
import com.pocketai.studio.ai.arena.ModelArena
import com.pocketai.studio.ai.arena.RankedResponse
import com.pocketai.studio.data.repository.ArenaMatchup
import com.pocketai.studio.data.repository.ArenaRepository
import com.pocketai.studio.data.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArenaUiState(
    val availableModels: List<ModelInfo> = emptyList(),
    val selectedModels: Set<String> = emptySet(), // Set of "providerId:modelId"
    val question: String = "",
    val isComparing: Boolean = false,
    val responses: Map<String, String> = emptyMap(), // "providerId:modelId" -> accumulated response text
    val modelStatuses: Map<String, ArenaModelStatus> = emptyMap(),
    val rankings: List<RankedResponse>? = null,
    val matchupHistory: List<ArenaMatchup> = emptyList(),
    val currentMatchupId: String? = null,
    val showHistory: Boolean = false,
    val error: String? = null,
    val snackbarMessage: String? = null
)

enum class ArenaModelStatus {
    IDLE, LOADING, COMPLETE, ERROR
}

data class ModelInfo(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val providerName: String
) {
    val compositeKey: String get() = "$providerId:$modelId"
}

@HiltViewModel
class ArenaViewModel @Inject constructor(
    private val modelArena: ModelArena,
    private val arenaRanker: ArenaRanker,
    private val arenaRepository: ArenaRepository,
    private val providerRepository: ProviderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArenaUiState())
    val uiState: StateFlow<ArenaUiState> = _uiState.asStateFlow()

    private var comparisonJob: Job? = null

    init {
        loadAvailableModels()
        loadHistory()
    }

    fun loadAvailableModels() {
        viewModelScope.launch {
            try {
                val cloudModels = providerRepository.getAllCloudModels()
                val modelInfos = cloudModels.map { cm ->
                    ModelInfo(
                        providerId = cm.providerId,
                        modelId = cm.id,
                        displayName = cm.name,
                        providerName = cm.providerName
                    )
                }
                _uiState.update { it.copy(availableModels = modelInfos) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load models: ${e.message}") }
            }
        }
    }

    fun toggleModelSelection(compositeKey: String) {
        _uiState.update { state ->
            val newSelection = if (compositeKey in state.selectedModels) {
                state.selectedModels - compositeKey
            } else {
                state.selectedModels + compositeKey
            }
            state.copy(selectedModels = newSelection)
        }
    }

    fun updateQuestion(question: String) {
        _uiState.update { it.copy(question = question, error = null) }
    }

    fun startComparison() {
        val state = _uiState.value
        if (state.selectedModels.size < 2 || state.question.isBlank()) {
            _uiState.update { it.copy(error = "Select at least 2 models and enter a question.") }
            return
        }

        val modelPairs = state.selectedModels.map { compositeKey ->
            val parts = compositeKey.split(":", limit = 2)
            parts[0] to parts[1]
        }

        // Clear previous results
        _uiState.update {
            it.copy(
                isComparing = true,
                responses = emptyMap(),
                modelStatuses = state.selectedModels.associateWith { ArenaModelStatus.LOADING },
                rankings = null,
                error = null,
                snackbarMessage = null,
                currentMatchupId = null
            )
        }

        comparisonJob = viewModelScope.launch {
            modelArena.compare(modelPairs, state.question)
                .catch { e ->
                    _uiState.update {
                        it.copy(isComparing = false, error = "Comparison failed: ${e.message}")
                    }
                }
                .collect { progress ->
                    when (progress) {
                        is ArenaProgress.ModelStarted -> {
                            val key = "${progress.providerId}:${progress.modelId}"
                            _uiState.update {
                                it.copy(
                                    modelStatuses = it.modelStatuses + (key to ArenaModelStatus.LOADING)
                                )
                            }
                        }
                        is ArenaProgress.Token -> {
                            val key = "${progress.providerId}:${progress.modelId}"
                            _uiState.update {
                                val currentText = it.responses[key] ?: ""
                                it.copy(
                                    responses = it.responses + (key to currentText + progress.token)
                                )
                            }
                        }
                        is ArenaProgress.ModelComplete -> {
                            val key = "${progress.providerId}:${progress.modelId}"
                            _uiState.update {
                                it.copy(
                                    modelStatuses = it.modelStatuses + (key to ArenaModelStatus.COMPLETE),
                                    responses = it.responses + (key to progress.fullResponse)
                                )
                            }
                        }
                        is ArenaProgress.ModelError -> {
                            val key = "${progress.providerId}:${progress.modelId}"
                            _uiState.update {
                                it.copy(
                                    modelStatuses = it.modelStatuses + (key to ArenaModelStatus.ERROR),
                                    error = "${progress.modelId}: ${progress.error}"
                                )
                            }
                        }
                        is ArenaProgress.AllComplete -> {
                            _uiState.update { currentState ->
                                currentState.copy(isComparing = false)
                            }
                            // Save to history
                            val currentState = _uiState.value
                            val matchupId = arenaRepository.createMatchup(
                                question = state.question,
                                models = modelPairs,
                                answers = currentState.responses
                            )
                            _uiState.update { it.copy(currentMatchupId = matchupId) }
                            loadHistory()
                        }
                    }
                }
        }
    }

    fun stopComparison() {
        comparisonJob?.cancel()
        comparisonJob = null
        _uiState.update {
            it.copy(
                isComparing = false,
                modelStatuses = it.modelStatuses.mapValues { (_, v) ->
                    if (v == ArenaModelStatus.LOADING) ArenaModelStatus.IDLE else v
                }
            )
        }
    }

    fun runAutoRanking() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.responses.isEmpty()) return@launch

            _uiState.update { it.copy(error = null) }

            try {
                val rankings = arenaRanker.rankResponses(state.question, state.responses)
                _uiState.update { it.copy(rankings = rankings) }

                // Persist rankings
                state.currentMatchupId?.let { matchupId ->
                    arenaRepository.updateMatchupRankings(matchupId, rankings)
                    loadHistory()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Auto-ranking failed: ${e.message}") }
            }
        }
    }

    fun voteForModel(compositeKey: String) {
        viewModelScope.launch {
            val matchupId = _uiState.value.currentMatchupId
            if (matchupId != null) {
                arenaRepository.recordVote(matchupId, compositeKey)
                val modelInfo = _uiState.value.availableModels.find { it.compositeKey == compositeKey }
                val modelName = modelInfo?.displayName ?: compositeKey
                _uiState.update {
                    it.copy(snackbarMessage = "Voted for $modelName")
                }
                loadHistory()
            }
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun toggleHistory() {
        _uiState.update { it.copy(showHistory = !it.showHistory, error = null) }
    }

    fun loadMatchup(matchup: ArenaMatchup) {
        val compositeKeys = matchup.models.map { "${it.first}:${it.second}" }.toSet()
        _uiState.update {
            it.copy(
                showHistory = false,
                question = matchup.question,
                responses = matchup.answers,
                selectedModels = compositeKeys,
                rankings = matchup.rankings,
                currentMatchupId = matchup.id,
                error = null
            )
        }
    }

    fun clearResponses() {
        _uiState.update {
            it.copy(
                responses = emptyMap(),
                modelStatuses = emptyMap(),
                rankings = null,
                currentMatchupId = null,
                error = null
            )
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                arenaRepository.getAllMatchups().collect { matchups ->
                    _uiState.update { it.copy(matchupHistory = matchups) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load history: ${e.message}") }
            }
        }
    }

    fun getModelDisplayName(compositeKey: String): String {
        return _uiState.value.availableModels.firstOrNull { it.compositeKey == compositeKey }
            ?.displayName
            ?: compositeKey.split(":").lastOrNull()
            ?: compositeKey
    }

    fun getModelProviderName(compositeKey: String): String {
        return _uiState.value.availableModels.firstOrNull { it.compositeKey == compositeKey }
            ?.providerName
            ?: compositeKey.split(":").firstOrNull()
            ?: ""
    }
}
