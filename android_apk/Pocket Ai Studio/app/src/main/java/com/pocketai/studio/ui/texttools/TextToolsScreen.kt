package com.pocketai.studio.ui.texttools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToolsScreen(
    viewModel: TextToolsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text Tools") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextTool.values().forEach { tool ->
                        FilterChip(
                            selected = uiState.selectedTool == tool,
                            onClick = { viewModel.selectTool(tool) },
                            label = { Text(tool.displayName) },
                            leadingIcon = {
                                Icon(
                                    when (tool) {
                                        TextTool.SUMMARIZE -> Icons.Filled.Summarize
                                        TextTool.REWRITE -> Icons.Filled.Edit
                                        TextTool.GRAMMAR -> Icons.Filled.Spellcheck
                                        TextTool.TRANSLATE -> Icons.Filled.Translate
                                        TextTool.EXPAND -> Icons.Filled.OpenInFull
                                        TextTool.SHORTEN -> Icons.Filled.Compress
                                        TextTool.BULLET_POINTS -> Icons.Filled.FormatListBulleted
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }

            item {
                OutlinedTextField(value = uiState.inputText, onValueChange = { viewModel.setInputText(it) }, placeholder = { Text("Enter your text here...") }, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), shape = RoundedCornerShape(16.dp), maxLines = 10)
            }

            item {
                Button(onClick = { viewModel.process() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), enabled = uiState.inputText.isNotBlank() && !uiState.isProcessing) {
                    if (uiState.isProcessing) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(modifier = Modifier.width(8.dp)) }
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Process")
                }
            }

            if (uiState.outputText.isNotBlank()) {
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Result", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(uiState.outputText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
}