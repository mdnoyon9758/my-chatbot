package com.pocketai.studio.ai.rag

import android.content.Context
import android.net.Uri
import com.pocketai.studio.ai.provider.ChatMessage
import com.pocketai.studio.ai.provider.ChatRequest
import com.pocketai.studio.data.repository.ProviderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class RagResult(
    val question: String,
    val answer: String,
    val sources: List<String>
)

@Singleton
class RagQueryEngine @Inject constructor(
    private val documentProcessor: DocumentProcessor,
    private val textChunker: TextChunker,
    private val vectorStore: VectorStore,
    private val embeddingService: EmbeddingService,
    private val providerRepository: ProviderRepository
) {
    private val documents = mutableMapOf<String, String>()

    suspend fun indexDocument(uri: Uri, fileName: String): String {
        val docId = UUID.randomUUID().toString()
        val text = documentProcessor.processDocument(uri, fileName)
        documents[docId] = fileName

        val chunks = textChunker.chunk(text)
        chunks.forEachIndexed { i, chunkText ->
            val chunkId = "$docId:$i"
            val embedding = embeddingService.embed(chunkText)
            vectorStore.addChunk(docId, chunkId, chunkText, embedding)
        }
        return docId
    }

    fun getDocumentCount(): Int = documents.size
    fun getChunkCount(): Int = vectorStore.size()
    fun getDocumentNames(): List<String> = documents.values.toList()

    suspend fun query(
        question: String,
        providerId: String = "openai",
        modelId: String = "gpt-4o-mini",
        topK: Int = 5
    ): RagResult {
        val queryEmbedding = embeddingService.embed(question)
        val results = vectorStore.search(queryEmbedding, topK)
        val sources = results.map { it.chunk.text.take(100) + "..." }

        val context = results.joinToString("\n\n") { "---\n${it.chunk.text}\n---" }
        val systemPrompt = "You are a document analysis assistant. Answer the user's question based ONLY on the provided context. If the context doesn't contain enough information, say so."
        val userPrompt = "Context:\n$context\n\nQuestion: $question"

        val provider = providerRepository.getProvider(providerId)
        if (provider == null) {
            return RagResult(question, "Provider $providerId not available. Please configure it in Settings.", sources)
        }
        return try {
            val request = ChatRequest(
                model = modelId,
                messages = listOf(ChatMessage("user", userPrompt)),
                systemPrompt = systemPrompt,
                maxTokens = 2048,
                temperature = 0.3f
            )
            val response = provider.chat(request)
            RagResult(question, response.content, sources)
        } catch (e: Exception) {
            RagResult(question, "Error: ${e.message}", sources)
        }
    }

    fun clearDocuments() {
        vectorStore.clear()
        documents.clear()
    }

    fun removeDocument(docId: String) {
        documents.remove(docId)
        vectorStore.removeDocument(docId)
    }
}
