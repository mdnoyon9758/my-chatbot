package com.pocketai.studio.ui.texttools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TextToolsScreen(
    viewModel: TextToolsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text Tools") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Tool selector — wraps instead of horizontal scroll
            item {
                Text(
                    "Select a tool",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            // Tool description
            item {
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            toolDescription(uiState.selectedTool),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Input
            item {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = { viewModel.setInputText(it) },
                    placeholder = { Text("Enter your text here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    shape = MaterialTheme.shapes.medium,
                    maxLines = 12,
                    supportingText = {
                        val wordCount = uiState.inputText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                        val charCount = uiState.inputText.length
                        Text("$wordCount words \u00B7 $charCount characters")
                    }
                )
            }

            // Process button
            item {
                Button(
                    onClick = { viewModel.process() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    enabled = uiState.inputText.isNotBlank() && !uiState.isProcessing
                ) {
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (uiState.isProcessing) "Processing..." else "Process")
                }
            }

            // Output
            if (uiState.outputText.isNotBlank()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Result",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                IconButton(
                                    onClick = { clipboard.setText(AnnotatedString(uiState.outputText)) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                uiState.outputText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun toolDescription(tool: TextTool): String = when (tool) {
    TextTool.SUMMARIZE -> "Condense text into a shorter summary while keeping key points"
    TextTool.REWRITE -> "Rewrite text for clarity, tone, or style improvements"
    TextTool.GRAMMAR -> "Fix grammar, spelling, and punctuation errors"
    TextTool.TRANSLATE -> "Translate text into another language"
    TextTool.EXPAND -> "Add more detail and context to make text longer"
    TextTool.SHORTEN -> "Remove unnecessary words while keeping meaning"
    TextTool.BULLET_POINTS -> "Convert paragraphs into concise bullet points"
}
