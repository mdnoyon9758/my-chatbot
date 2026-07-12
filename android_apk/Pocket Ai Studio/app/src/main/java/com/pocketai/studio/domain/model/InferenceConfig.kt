package com.pocketai.studio.domain.model

data class InferenceConfig(
    val contextSize: Int = 4096,
    val threads: Int = 4,
    val useGpu: Boolean = false,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 2048,
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED
)

enum class PerformanceMode(val displayName: String, val description: String) {
    FAST("Fast", "Quick responses, lower quality"),
    BALANCED("Balanced", "Good quality and speed"),
    HIGH_QUALITY("High Quality", "Best quality, slower")
}