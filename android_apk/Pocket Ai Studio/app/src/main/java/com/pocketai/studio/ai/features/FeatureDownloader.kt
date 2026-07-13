package com.pocketai.studio.ai.features

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class Feature(val displayName: String, val description: String) {
    OCR("Text Scanner", "Extract text from images"),
    PDF("PDF Assistant", "Read and analyze PDF documents"),
    TEXT_TOOLS("Text Tools", "Summarize, rewrite, translate text")
}

enum class FeatureStatus {
    AVAILABLE,
    DOWNLOADING,
    DOWNLOAD_FAILED
}

@Singleton
class FeatureDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val featuresDir: File
        get() = File(context.filesDir, "features").also { it.mkdirs() }

    private val _featureStatus = MutableStateFlow<Map<Feature, FeatureStatus>>(emptyMap())
    val featureStatus: StateFlow<Map<Feature, FeatureStatus>> = _featureStatus.asStateFlow()

    fun isFeatureAvailable(feature: Feature): Boolean {
        // All features are bundled in the APK
        // This is a placeholder for future on-demand module delivery
        return true
    }

    fun getFeatureDir(feature: Feature): File {
        return File(featuresDir, feature.name.lowercase()).also { it.mkdirs() }
    }
}
