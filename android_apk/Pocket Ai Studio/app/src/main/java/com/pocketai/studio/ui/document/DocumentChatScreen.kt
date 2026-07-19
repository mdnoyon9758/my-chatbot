package com.pocketai.studio.ui.document

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentChatScreen(
    viewModel: DocumentChatViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var question by remember { mutableStateOf("") }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.importDocument(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Chat") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { docPicker.launch("*/*") }) { Icon(Icons.Filled.UploadFile, contentDescription = "Import") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Import button
            item {
                Button(onClick = { docPicker.launch("*/*") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import PDF, Excel, CSV, or Text file")
                }
            }

            // Documents list
            if (state.documents.isNotEmpty()) {
                item { Text("Indexed Documents", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                items(state.documents) { doc ->
                    Card(shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doc.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${doc.chunkCount} chunks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { viewModel.deleteDocument(doc.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Question input
            if (state.documents.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = question, onValueChange = { question = it },
                        placeholder = { Text("Ask about your documents...") },
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                        trailingIcon = {
                            IconButton(onClick = { if (question.isNotBlank()) { viewModel.askQuestion(question); question = "" } }, enabled = question.isNotBlank()) {
                                Icon(Icons.Filled.Send, contentDescription = "Ask")
                            }
                        }
                    )
                }
            }

            // Answer
            if (state.answer.isNotBlank()) {
                item {
                    Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.QuestionAnswer, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Answer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(state.answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            if (state.sources.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Sources:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                state.sources.forEach { src ->
                                    Text(src, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            if (state.isLoading) item { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }

            if (state.documents.isEmpty() && !state.isLoading) {
                item {
                    Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No documents indexed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            Text("Import documents to ask questions about their content", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
