package com.pocketai.studio.ai.rag

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbeddingService @Inject constructor() {
    private val dims = 128

    fun embed(text: String): FloatArray {
        val cleaned = text.lowercase().trim()
        val result = FloatArray(dims) { 0f }
        val words = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return result

        // Simple n-gram frequency-based embedding
        for (i in 0 until dims) {
            var value = 0.0
            for (word in words) {
                val hash = abs(word.hashCode() * (i + 1) * 31L) % 10000
                val freq = words.count { it == word }.toDouble() / words.size
                value += (hash / 10000.0) * freq * (i + 1).toDouble() / dims
            }
            result[i] = (value / words.size).toFloat()
        }

        // Normalize
        var norm = 0f
        for (v in result) norm += v * v
        norm = sqrt(norm)
        if (norm > 0f) for (i in result.indices) result[i] /= norm

        return result
    }

    fun embedBatch(texts: List<String>): List<FloatArray> = texts.map { embed(it) }
}
