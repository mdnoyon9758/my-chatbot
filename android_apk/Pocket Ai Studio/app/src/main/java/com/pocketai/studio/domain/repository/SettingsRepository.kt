package com.pocketai.studio.domain.repository

import com.pocketai.studio.domain.model.InferenceConfig
import com.pocketai.studio.domain.model.PerformanceMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    val fontSize: Flow<Int>
    val inferenceConfig: Flow<InferenceConfig>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setFontSize(size: Int)
    suspend fun setContextSize(size: Int)
    suspend fun setThreads(threads: Int)
    suspend fun setUseGpu(useGpu: Boolean)
    suspend fun setPerformanceMode(mode: PerformanceMode)
    suspend fun setTemperature(temp: Float)
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }