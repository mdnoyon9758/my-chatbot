package com.pocketai.studio.ai.benchmark

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class BenchmarkResult(
    val totalRamMb: Int,
    val availableRamMb: Int,
    val recommendedModel: String,
    val canRunLargeModels: Boolean,
    val cpuCores: Int,
    val supportsArm64: Boolean
)

@Singleton
class DeviceBenchmark @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun benchmark(): BenchmarkResult {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt()
        val availableRamMb = (memInfo.availMem / (1024 * 1024)).toInt()
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val supportsArm64 = Build.SUPPORTED_ABIS.any { it.contains("arm64") }

        val recommendedModel = when {
            totalRamMb >= 8000 -> "Mistral 7B"
            totalRamMb >= 5000 -> "Phi-3 Mini"
            totalRamMb >= 3500 -> "Gemma 2 2B"
            else -> "Llama 3.2 1B"
        }

        return BenchmarkResult(
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            recommendedModel = recommendedModel,
            canRunLargeModels = totalRamMb >= 6000,
            cpuCores = cpuCores,
            supportsArm64 = supportsArm64
        )
    }

    fun getRamWarning(modelSizeMb: Long): String? {
        val result = benchmark()
        val requiredMb = (modelSizeMb * 2).toInt()
        return if (requiredMb > result.availableRamMb) {
            "Warning: This model may require ~${requiredMb}MB RAM. You have ${result.availableRamMb}MB available."
        } else null
    }
}
