package com.pocketai.studio.ui.providers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsScreen(
    viewModel: ProviderSettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar when a key is saved/removed
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            snackbarHostState.showSnackbar("Settings saved successfully")
            viewModel.clearSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API Providers") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val filteredProviders = uiState.providerStates.values.filter { provider ->
                uiState.searchQuery.isBlank() ||
                        provider.displayName.contains(uiState.searchQuery, ignoreCase = true) ||
                        provider.providerId.contains(uiState.searchQuery, ignoreCase = true)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
            ) {
                // Search bar
                item(key = "search") {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search providers...") },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.setSearchQuery(uiState.searchQuery) })
                    )
                }

                // Provider filter info
                item(key = "count") {
                    Text(
                        text = if (uiState.searchQuery.isNotBlank()) {
                            "${filteredProviders.size} of ${uiState.providerStates.size} providers"
                        } else {
                            "${uiState.providerStates.size} providers"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Provider cards
                items(filteredProviders, key = { it.providerId }) { provider ->
                    ProviderCard(
                        provider = provider,
                        onToggle = { viewModel.toggleProvider(provider.providerId) },
                        onApiKeyChange = { key -> viewModel.setApiKey(provider.providerId, key) },
                        onSave = { viewModel.saveApiKey(provider.providerId) },
                        onRemove = { viewModel.removeApiKey(provider.providerId) },
                        onToggleEnabled = { viewModel.removeApiKey(provider.providerId) }
                    )
                }

                // Bottom info
                item(key = "info") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "API keys are stored locally on your device and are only sent to the respective provider's API.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderUiState,
    onToggle: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: emoji icon, name, status chip, expand/collapse
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Provider emoji icon
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = providerIcon(provider.providerId),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name and website
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = provider.website,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status indicator
                StatusChip(hasApiKey = provider.hasApiKey, isEnabled = provider.isEnabled)

                Spacer(modifier = Modifier.width(4.dp))

                // Expand/collapse button
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (provider.showField) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (provider.showField) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expanded content: API key field + buttons + enable/disable
            AnimatedVisibility(
                visible = provider.showField,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Password field with visibility toggle
                    var passwordVisible by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = provider.apiKey,
                        onValueChange = onApiKeyChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key") },
                        placeholder = {
                            Text(
                                if (provider.hasApiKey) "Enter new key to replace existing" else "Enter your API key"
                            )
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { if (provider.apiKey.isNotBlank()) onSave() }),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Hide key" else "Show key"
                                )
                            }
                        },
                        supportingText = {
                            Text(
                                text = "Your key is stored locally and never shared",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Save button
                        Button(
                            onClick = onSave,
                            enabled = provider.apiKey.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save")
                        }

                        // Remove button (only shown if has key)
                        if (provider.hasApiKey) {
                            OutlinedButton(
                                onClick = onRemove,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Remove")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Enable/disable toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (provider.isEnabled) Icons.Filled.ToggleOn else Icons.Filled.ToggleOff,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (provider.isEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (provider.isEnabled) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = provider.isEnabled,
                            onCheckedChange = { onToggleEnabled() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(hasApiKey: Boolean, isEnabled: Boolean) {
    val statusLabel: String
    val statusIcon: androidx.compose.ui.graphics.vector.ImageVector
    val containerColor: androidx.compose.ui.graphics.Color
    val contentColor: androidx.compose.ui.graphics.Color

    when {
        hasApiKey && isEnabled -> {
            statusLabel = "Connected"
            statusIcon = Icons.Filled.CheckCircle
            containerColor = MaterialTheme.colorScheme.primaryContainer
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        }
        hasApiKey && !isEnabled -> {
            statusLabel = "Disabled"
            statusIcon = Icons.Filled.RemoveCircleOutline
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
        else -> {
            statusLabel = "Not Connected"
            statusIcon = Icons.Filled.Warning
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        }
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

/**
 * Returns an appropriate emoji icon for the given provider ID.
 */
private fun providerIcon(providerId: String): String = when (providerId) {
    "openai" -> "🔵"
    "anthropic" -> "🟣"
    "google" -> "🟢"
    "mistral" -> "🔶"
    "groq" -> "🟩"
    "openrouter" -> "🔴"
    "deepseek" -> "🐋"
    "cohere" -> "🟤"
    "together" -> "🟣"
    "perplexity" -> "🔵"
    else -> "🤖"
}
