package com.pocketai.studio.domain.model

data class AiModel(
    val id: String,
    val name: String,
    val developer: String,
    val description: String,
    val quantization: String = "Q4_K_M",
    val fileSizeBytes: Long = 0,
    val fileSizeFormatted: String = "",
    val requiredRam: String = "",
    val recommendedDevice: String = "",
    val downloadUrl: String = "",
    val filename: String = "",
    val isDownloaded: Boolean = false,
    val isLoaded: Boolean = false,
    val filePath: String? = null,
    val downloadProgress: Float = 0f,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
    // Cloud model fields
    val isCloudModel: Boolean = false,
    val providerId: String? = null,
    val modelId: String? = null,
    val contextWindow: Int = 4096,
    val pricingPer1kInput: Double = 0.0,
    val pricingPer1kOutput: Double = 0.0,
    val capabilities: List<String> = listOf("chat"),
    val isVision: Boolean = false
)

enum class DownloadStatus {
    NONE, DOWNLOADING, PAUSED, COMPLETED, FAILED, VERIFYING
}