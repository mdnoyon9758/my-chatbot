package com.pocketai.studio.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.pocketai.studio.domain.model.InferenceConfig
import com.pocketai.studio.domain.model.PerformanceMode
import com.pocketai.studio.domain.repository.SettingsRepository
import com.pocketai.studio.domain.repository.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_mode")
        private val FONT_SIZE_KEY = intPreferencesKey("font_size")
        private val CONTEXT_SIZE_KEY = intPreferencesKey("context_size")
        private val THREADS_KEY = intPreferencesKey("threads")
        private val USE_GPU_KEY = booleanPreferencesKey("use_gpu")
        private val PERFORMANCE_MODE_KEY = stringPreferencesKey("performance_mode")
        private val TEMPERATURE_KEY = floatPreferencesKey("temperature")
    }

    override val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[THEME_KEY]) { "LIGHT" -> ThemeMode.LIGHT; "DARK" -> ThemeMode.DARK; else -> ThemeMode.SYSTEM }
    }

    override val fontSize: Flow<Int> = context.dataStore.data.map { prefs -> prefs[FONT_SIZE_KEY] ?: 16 }

    override val inferenceConfig: Flow<InferenceConfig> = context.dataStore.data.map { prefs ->
        InferenceConfig(
            contextSize = prefs[CONTEXT_SIZE_KEY] ?: 4096,
            threads = prefs[THREADS_KEY] ?: 4,
            useGpu = prefs[USE_GPU_KEY] ?: false,
            temperature = prefs[TEMPERATURE_KEY] ?: 0.7f,
            performanceMode = when (prefs[PERFORMANCE_MODE_KEY]) {
                "FAST" -> PerformanceMode.FAST
                "HIGH_QUALITY" -> PerformanceMode.HIGH_QUALITY
                else -> PerformanceMode.BALANCED
            }
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_KEY] = mode.name }
    }

    override suspend fun setFontSize(size: Int) {
        context.dataStore.edit { it[FONT_SIZE_KEY] = size }
    }

    override suspend fun setContextSize(size: Int) {
        context.dataStore.edit { it[CONTEXT_SIZE_KEY] = size }
    }

    override suspend fun setThreads(threads: Int) {
        context.dataStore.edit { it[THREADS_KEY] = threads }
    }

    override suspend fun setUseGpu(useGpu: Boolean) {
        context.dataStore.edit { it[USE_GPU_KEY] = useGpu }
    }

    override suspend fun setPerformanceMode(mode: PerformanceMode) {
        context.dataStore.edit { it[PERFORMANCE_MODE_KEY] = mode.name }
    }

    override suspend fun setTemperature(temp: Float) {
        context.dataStore.edit { it[TEMPERATURE_KEY] = temp }
    }
}