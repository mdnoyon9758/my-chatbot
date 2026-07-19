package com.pocketai.studio.ai.rag

import javax.inject.Inject
import javax.inject.Singleton

data class Chunk(val id: String, val text: String, val embedding: FloatArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Chunk) return false
        return id == other.id
    }
    override fun hashCode() = id.hashCode()
}

data class ScoredChunk(val chunk: Chunk, val score: Double)

@Singleton
class VectorStore @Inject constructor() {
    private val chunks = mutableListOf<Chunk>()
    private val documentChunks = mutableMapOf<String, MutableList<String>>()

    fun addChunk(id: String, text: String, embedding: FloatArray) {
        chunks.add(Chunk(id, text, embedding))
    }

    fun addChunk(documentId: String, id: String, text: String, embedding: FloatArray) {
        chunks.add(Chunk(id, text, embedding))
        documentChunks.getOrPut(documentId) { mutableListOf() }.add(id)
    }

    fun search(queryEmbedding: FloatArray, topK: Int = 5): List<ScoredChunk> {
        return chunks.map { chunk ->
            val score = cosineSimilarity(queryEmbedding, chunk.embedding)
            ScoredChunk(chunk, score)
        }.sortedByDescending { it.score }.take(topK)
    }

    fun removeDocument(documentId: String) {
        val ids = documentChunks.remove(documentId) ?: return
        chunks.removeAll { it.id in ids }
    }

    fun clear() {
        chunks.clear()
        documentChunks.clear()
    }

    fun size(): Int = chunks.size

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size) return 0.0
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dotProduct += a[i].toDouble() * b[i].toDouble()
            normA += a[i].toDouble() * a[i].toDouble()
            normB += b[i].toDouble() * b[i].toDouble()
        }
        val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denom == 0.0) 0.0 else dotProduct / denom
    }
}
