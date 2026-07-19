package com.pocketai.studio.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketai.studio.domain.model.ChatMessage
import com.pocketai.studio.domain.model.MessageRole
import com.pocketai.studio.domain.model.ModelOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatId: String?,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.attachImage(it) } }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.attachPdf(it) } }

    LaunchedEffect(chatId) { viewModel.loadChat(chatId) }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            uiState.session?.title ?: "New Chat",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (uiState.isGenerating) {
                            Text(
                                "Thinking...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (chatId == null && uiState.modelOptions.isNotEmpty()) {
                        ModelPicker(
                            modelOptions = uiState.modelOptions,
                            selectedKey = uiState.selectedModelKey,
                            isRefreshing = uiState.isRefreshingModels,
                            onSelect = { viewModel.selectModel(it) },
                            onRefresh = { viewModel.refreshModelOptions() }
                        )
                    } else if (chatId == null && !uiState.isRefreshingModels) {
                        TextButton(onClick = { viewModel.refreshModelOptions() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Models", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    uiState.pendingAttachment?.let { attachment ->
                        Row(
                            modifier = Modifier.padding(bottom = 8.dp).background(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.shapes.small
                            ).padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (attachment.type == com.pocketai.studio.domain.model.AttachmentType.IMAGE) Icons.Filled.Image else Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                attachment.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(onClick = { viewModel.removeAttachment() }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Box {
                            IconButton(onClick = { showAttachmentMenu = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.AttachFile, contentDescription = "Attach", modifier = Modifier.size(22.dp))
                            }
                            DropdownMenu(expanded = showAttachmentMenu, onDismissRequest = { showAttachmentMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Image (OCR)") },
                                    onClick = { showAttachmentMenu = false; imagePicker.launch("image/*") },
                                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("PDF Document") },
                                    onClick = { showAttachmentMenu = false; pdfPicker.launch("application/pdf") },
                                    leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Message...") },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.extraLarge,
                            enabled = !uiState.isGenerating,
                            minLines = 1,
                            maxLines = 5
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        AnimatedVisibility(visible = uiState.isGenerating, enter = fadeIn(), exit = fadeOut()) {
                            FilledIconButton(
                                onClick = { viewModel.stopGeneration() },
                                modifier = Modifier.size(40.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(20.dp))
                            }
                        }
                        AnimatedVisibility(visible = !uiState.isGenerating, enter = fadeIn(), exit = fadeOut()) {
                            FilledIconButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        viewModel.sendMessage(inputText, chatId)
                                        inputText = ""
                                    }
                                },
                                modifier = Modifier.size(40.dp),
                                enabled = inputText.isNotBlank() || uiState.pendingAttachment != null
                            ) {
                                Icon(Icons.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                WelcomeScreen(onSuggestionClick = { inputText = it })
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.messages) { message -> MessageBubble(message = message) }
                    if (uiState.isGenerating && uiState.currentResponse.isNotBlank()) {
                        item {
                            MessageBubble(
                                message = ChatMessage(
                                    id = "streaming",
                                    chatId = "",
                                    role = MessageRole.ASSISTANT,
                                    content = uiState.currentResponse,
                                    timestamp = System.currentTimeMillis(),
                                    isStreaming = true
                                )
                            )
                        }
                    }
                    if (uiState.isGenerating && uiState.currentResponse.isBlank()) {
                        item { TypingIndicator() }
                    }
                }
            }
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") } }
                ) { Text(error) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Unified model picker with group headers
// ─────────────────────────────────────────────────────────────

@Composable
private fun ModelPicker(
    modelOptions: List<ModelOption>,
    selectedKey: String,
    isRefreshing: Boolean,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selected = modelOptions.firstOrNull { it.key == selectedKey }
    val pickerLabel = selected?.let {
        if (it is ModelOption.Cloud) it.modelName.take(18)
        else it.displayName.take(18)
    } ?: "Pick model"

    Box {
        Row {
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            TextButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.ModelTraining, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(pickerLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 260.dp, max = 320.dp)
        ) {
            // Refresh button at top
            DropdownMenuItem(
                text = { Text("Refresh models") },
                onClick = { onRefresh(); expanded = false },
                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            HorizontalDivider()

            // Group: cloud providers
            val cloudOptions = modelOptions.filterIsInstance<ModelOption.Cloud>()
            val localOptions = modelOptions.filterIsInstance<ModelOption.Local>()

            if (cloudOptions.isNotEmpty()) {
                Text(
                    "☁️ Cloud Providers",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                cloudOptions.forEach { opt ->
                    ModelDropdownItem(
                        option = opt,
                        isSelected = opt.key == selectedKey,
                        onClick = { onSelect(opt.key); expanded = false }
                    )
                }
            }

            if (cloudOptions.isNotEmpty() && localOptions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            if (localOptions.isNotEmpty()) {
                Text(
                    "📱 On-Device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                localOptions.forEach { opt ->
                    ModelDropdownItem(
                        option = opt,
                        isSelected = opt.key == selectedKey,
                        onClick = { onSelect(opt.key); expanded = false }
                    )
                }
            }

            if (modelOptions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No models available", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { expanded = false },
                    enabled = false
                )
            }
        }
    }
}

@Composable
private fun ModelDropdownItem(
    option: ModelOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (icon, subtitle) = when (option) {
        is ModelOption.Cloud -> "☁️" to option.subtitle
        is ModelOption.Local -> "📱" to option.subtitle
    }

    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, modifier = Modifier.padding(end = 8.dp))
                Column {
                    Text(
                        option.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSelected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        onClick = onClick
    )
}

// ─────────────────────────────────────────────────────────────
// Existing composables kept as-is
// ─────────────────────────────────────────────────────────────

@Composable
private fun WelcomeScreen(onSuggestionClick: (String) -> Unit) {
    val suggestions = listOf("/summarize", "/rewrite", "/grammar", "/translate", "/expand", "/shorten", "/bullets")
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(20.dp))
        Text("How can I help you?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Type a message, attach files, or try a command", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Try a command", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { s -> SuggestionChip(onClick = { onSuggestionClick("$s ") }, label = { Text(s) }) }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Text(
            text = if (isUser) "You" else "Pocket AI",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        if (message.attachments.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                message.attachments.forEach { att ->
                    AssistChip(
                        onClick = {},
                        label = { Text(att.name, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                if (att.type == com.pocketai.studio.domain.model.AttachmentType.IMAGE) Icons.Filled.Image else Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }
        Card(
            shape = MaterialTheme.shapes.large.copy(
                topStart = if (isUser) MaterialTheme.shapes.large.topStart else MaterialTheme.shapes.small.topStart,
                topEnd = if (isUser) MaterialTheme.shapes.small.topEnd else MaterialTheme.shapes.large.topEnd,
                bottomStart = MaterialTheme.shapes.large.bottomStart,
                bottomEnd = MaterialTheme.shapes.large.bottomEnd
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                message.toolUsed?.let { tool ->
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "/$tool",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isUser && message.content.isNotBlank() && !message.isStreaming) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = { clipboard.setText(AnnotatedString(message.content)) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        if (message.tokenCount > 0) {
            Text(
                "${message.tokenCount} tokens",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = "Pocket AI",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Card(
            shape = MaterialTheme.shapes.large.copy(
                topStart = MaterialTheme.shapes.small.topStart,
                bottomEnd = MaterialTheme.shapes.large.bottomEnd
            ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Thinking...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
