package com.pocketai.studio.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketai.studio.domain.model.PerformanceMode
import com.pocketai.studio.domain.repository.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ── Appearance Section ──
            item {
                SectionLabel("Appearance")
            }
            item {
                SettingsCard {
                    SettingsRow(label = "Theme") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = uiState.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    leadingIcon = {
                                        Icon(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> Icons.Filled.SettingsBrightness
                                                ThemeMode.LIGHT -> Icons.Filled.LightMode
                                                ThemeMode.DARK -> Icons.Filled.DarkMode
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
            item {
                SettingsCard {
                    SettingsRow(label = "Font Size") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.setFontSize((uiState.fontSize - 2).coerceAtLeast(12)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    "${uiState.fontSize} sp",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setFontSize((uiState.fontSize + 2).coerceAtMost(24)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // ── AI Performance Section ──
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionLabel("AI Performance")
            }
            item {
                SettingsCard {
                    SettingsRow(label = "Performance Mode") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PerformanceMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = uiState.inferenceConfig.performanceMode == mode,
                                    onClick = { viewModel.setPerformanceMode(mode) },
                                    label = { Text(mode.displayName) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            modeDescription(uiState.inferenceConfig.performanceMode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                SettingsCard {
                    SettingsRow(label = "Context Size") {
                        Slider(
                            value = uiState.inferenceConfig.contextSize.toFloat(),
                            onValueChange = { viewModel.setContextSize(it.toInt()) },
                            valueRange = 512f..16384f,
                            steps = 30,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("512", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${uiState.inferenceConfig.contextSize} tokens",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("16K", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Larger context = more memory, better long conversations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            item {
                SettingsCard {
                    SettingsRow(label = "Inference Threads") {
                        Slider(
                            value = uiState.inferenceConfig.threads.toFloat(),
                            onValueChange = { viewModel.setThreads(it.toInt()) },
                            valueRange = 1f..16f,
                            steps = 15,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${uiState.inferenceConfig.threads} threads",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("16", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "More threads = faster on multi-core CPUs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GPU Acceleration", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Use GPU for faster inference (if supported)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.inferenceConfig.useGpu,
                            onCheckedChange = { viewModel.setUseGpu(it) }
                        )
                    }
                }
            }

            // ── Storage Section ──
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionLabel("Storage")
            }
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Models Storage", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${uiState.modelsCount} models \u00B7 ${uiState.storageUsed} used",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── About Section ──
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionLabel("About")
            }
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Pocket AI Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Version 1.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                "100% offline \u00B7 No tracking \u00B7 No telemetry",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun modeDescription(mode: PerformanceMode): String = when (mode) {
    PerformanceMode.FAST -> "Quick responses, lower quality"
    PerformanceMode.BALANCED -> "Good quality and speed"
    PerformanceMode.HIGH_QUALITY -> "Best quality, slower"
}
