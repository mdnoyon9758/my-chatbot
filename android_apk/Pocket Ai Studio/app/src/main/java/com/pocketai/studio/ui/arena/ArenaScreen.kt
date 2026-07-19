package com.pocketai.studio.ui.arena

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketai.studio.ai.arena.RankedResponse
import com.pocketai.studio.data.repository.ArenaMatchup
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArenaScreen(
    viewModel: ArenaViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Arena") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleHistory() }) {
                        Icon(
                            if (uiState.showHistory) Icons.Filled.Close else Icons.Filled.History,
                            contentDescription = if (uiState.showHistory) "Close history" else "History"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (uiState.showHistory) {
                HistoryContent(
                    matchups = uiState.matchupHistory,
                    onSelectMatchup = { viewModel.loadMatchup(it) },
                    onDeleteMatchup = { viewModel.let { /* TODO: delete */ } },
                    getModelDisplayName = { viewModel.getModelDisplayName(it) }
                )
            } else {
                MainArenaContent(
                    uiState = uiState,
                    onQuestionChange = { viewModel.updateQuestion(it) },
                    onToggleModel = { viewModel.toggleModelSelection(it) },
                    onCompare = { viewModel.startComparison() },
                    onStop = { viewModel.stopComparison() },
                    onAutoRank = { viewModel.runAutoRanking() },
                    onVote = { viewModel.voteForModel(it) },
                    onCopy = { text -> copyToClipboard(context, text) },
                    onClearResponses = { viewModel.clearResponses() },
                    getModelDisplayName = { viewModel.getModelDisplayName(it) },
                    getModelProviderName = { viewModel.getModelProviderName(it) }
                )
            }
        }
    }
}

@Composable
private fun MainArenaContent(
    uiState: ArenaUiState,
    onQuestionChange: (String) -> Unit,
    onToggleModel: (String) -> Unit,
    onCompare: () -> Unit,
    onStop: () -> Unit,
    onAutoRank: () -> Unit,
    onVote: (String) -> Unit,
    onCopy: (String) -> Unit,
    onClearResponses: () -> Unit,
    getModelDisplayName: (String) -> String,
    getModelProviderName: (String) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Model Selection
        Text(
            "Select Models",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (uiState.availableModels.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No models available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Configure API keys in Settings to enable cloud models.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            ModelSelectorContent(
                availableModels = uiState.availableModels,
                selectedModels = uiState.selectedModels,
                onToggle = onToggleModel
            )

            Text(
                "${uiState.selectedModels.size} model(s) selected (minimum 2 required)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        // Section: Question Input
        Text(
            "Question",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = uiState.question,
            onValueChange = onQuestionChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter a question to ask all models...") },
            minLines = 2,
            maxLines = 6,
            shape = MaterialTheme.shapes.medium
        )

        // Compare / Stop Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onCompare,
                modifier = Modifier.weight(1f),
                enabled = !uiState.isComparing && uiState.selectedModels.size >= 2 && uiState.question.isNotBlank()
            ) {
                Icon(Icons.Filled.Compare, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compare")
            }

            if (uiState.isComparing) {
                OutlinedButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop")
                }
            }
        }

        // Error display
        uiState.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Section: Comparison Progress
        if (uiState.isComparing) {
            Text(
                "Comparing...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            ComparisonProgressContent(
                selectedModels = uiState.selectedModels,
                modelStatuses = uiState.modelStatuses,
                getModelDisplayName = getModelDisplayName,
                getModelProviderName = getModelProviderName
            )
        }

        // Section: Results
        if (uiState.responses.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearResponses) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Comparison")
                }
            }

            uiState.selectedModels.forEach { compositeKey ->
                val responseText = uiState.responses[compositeKey] ?: ""
                val status = uiState.modelStatuses[compositeKey] ?: ArenaModelStatus.IDLE
                ResultCard(
                    modelName = getModelDisplayName(compositeKey),
                    providerName = getModelProviderName(compositeKey),
                    responseText = responseText,
                    status = status,
                    onCopy = { onCopy(responseText) }
                )
            }
        }

        // Section: Auto-Rank Button
        if (uiState.responses.isNotEmpty() && !uiState.isComparing && uiState.rankings == null) {
            OutlinedButton(
                onClick = onAutoRank,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.responses.size >= 2
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Auto-Rank Responses")
            }
        }

        // Section: Rankings Display
        if (uiState.rankings != null) {
            RankingsContent(
                rankings = uiState.rankings,
                getModelDisplayName = getModelDisplayName
            )
        }

        // Section: Vote
        if (uiState.responses.isNotEmpty() && !uiState.isComparing && uiState.selectedModels.size >= 2) {
            VoteContent(
                selectedModels = uiState.selectedModels.toList(),
                getModelDisplayName = getModelDisplayName,
                onVote = onVote
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ModelSelectorContent(
    availableModels: List<ModelInfo>,
    selectedModels: Set<String>,
    onToggle: (String) -> Unit
) {
    val groupedModels = availableModels.groupBy { it.providerName }
        .entries
        .sortedBy { it.key }

    groupedModels.forEach { (providerName, models) ->
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                providerName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                models.forEach { model ->
                    FilterChip(
                        selected = model.compositeKey in selectedModels,
                        onClick = { onToggle(model.compositeKey) },
                        label = { Text(model.displayName, style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = {
                            if (model.compositeKey in selectedModels) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonProgressContent(
    selectedModels: Set<String>,
    modelStatuses: Map<String, ArenaModelStatus>,
    getModelDisplayName: (String) -> String,
    getModelProviderName: (String) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        selectedModels.forEach { compositeKey ->
            val status = modelStatuses[compositeKey] ?: ArenaModelStatus.IDLE
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (status) {
                        ArenaModelStatus.LOADING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        ArenaModelStatus.COMPLETE -> {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        ArenaModelStatus.ERROR -> {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        ArenaModelStatus.IDLE -> {
                            Icon(
                                Icons.Filled.HourglassEmpty,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            getModelDisplayName(compositeKey),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            getModelProviderName(compositeKey),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (status == ArenaModelStatus.LOADING) {
                        LinearProgressIndicator(
                            modifier = Modifier.width(60.dp).height(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    modelName: String,
    providerName: String,
    responseText: String,
    status: ArenaModelStatus,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        modelName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        providerName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status = status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (responseText.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = responseText,
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else if (status == ArenaModelStatus.LOADING) {
                Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (status == ArenaModelStatus.ERROR) {
                Text(
                    "Failed to get response",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    "Waiting...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (responseText.isNotEmpty() && status != ArenaModelStatus.LOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Copy response",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ArenaModelStatus) {
    val (text, color) = when (status) {
        ArenaModelStatus.IDLE -> "Idle" to MaterialTheme.colorScheme.outline
        ArenaModelStatus.LOADING -> "Loading..." to MaterialTheme.colorScheme.tertiary
        ArenaModelStatus.COMPLETE -> "Done" to MaterialTheme.colorScheme.primary
        ArenaModelStatus.ERROR -> "Error" to MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RankingsContent(
    rankings: List<RankedResponse>,
    getModelDisplayName: (String) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Auto Rankings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            rankings.forEach { ranked ->
                val medalTint = when (ranked.rank) {
                    1 -> Color(0xFFFFD700) // Gold
                    2 -> Color(0xFFC0C0C0) // Silver
                    3 -> Color(0xFFCD7F32) // Bronze
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val compositeKey = "${ranked.providerId}:${ranked.modelId}"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Rank badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (ranked.rank <= 3) medalTint.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "#${ranked.rank}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (ranked.rank <= 3) medalTint
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                getModelDisplayName(compositeKey),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${ranked.score.toInt()}/100",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (ranked.reasoning.isNotBlank()) {
                            Text(
                                ranked.reasoning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoteContent(
    selectedModels: List<String>,
    getModelDisplayName: (String) -> String,
    onVote: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ThumbUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Vote for the best response",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            selectedModels.forEach { compositeKey ->
                OutlinedButton(
                    onClick = { onVote(compositeKey) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.HowToVote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${getModelDisplayName(compositeKey)} wins")
                }
            }

            if (selectedModels.size == 2) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onVote("tie") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Balance, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tie")
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(
    matchups: List<ArenaMatchup>,
    onSelectMatchup: (ArenaMatchup) -> Unit,
    onDeleteMatchup: (String) -> Unit,
    getModelDisplayName: (String) -> String
) {
    if (matchups.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No comparison history yet",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Run a comparison to see it here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Comparison History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(matchups, key = { it.id }) { matchup ->
                HistoryMatchupCard(
                    matchup = matchup,
                    onClick = { onSelectMatchup(matchup) },
                    getModelDisplayName = getModelDisplayName
                )
            }
        }
    }
}

@Composable
private fun HistoryMatchupCard(
    matchup: ArenaMatchup,
    onClick: () -> Unit,
    getModelDisplayName: (String) -> String
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                matchup.question,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                matchup.models.forEachIndexed { index, (providerId, modelId) ->
                    if (index > 0) {
                        Text(
                            " vs ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        getModelDisplayName("$providerId:$modelId"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatRelativeTime(matchup.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                if (matchup.rankings != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "Ranked",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                if (matchup.vote != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "Voted",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard != null) {
        val clip = ClipData.newPlainText("Model Response", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${diff / TimeUnit.MINUTES.toMillis(1)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${diff / TimeUnit.HOURS.toMillis(1)}h ago"
        diff < TimeUnit.DAYS.toMillis(7) -> "${diff / TimeUnit.DAYS.toMillis(1)}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
