package com.pocketai.studio.data.repository

import android.net.Uri
import com.pocketai.studio.ai.rag.RagResult
import com.pocketai.studio.ai.rag.RagQueryEngine
import com.pocketai.studio.data.local.dao.DocumentDao
import com.pocketai.studio.data.local.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val ragQueryEngine: RagQueryEngine
) {
    fun getAllDocuments(): Flow<List<DocumentEntity>> = documentDao.getAllDocuments()

    suspend fun importDocument(uri: Uri, fileName: String): Result<String> {
        return try {
            val docId = ragQueryEngine.indexDocument(uri, fileName)
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val entity = DocumentEntity(
                id = docId,
                fileName = fileName,
                fileType = ext,
                chunkCount = ragQueryEngine.getChunkCount(),
                createdAt = System.currentTimeMillis()
            )
            documentDao.insertDocument(entity)
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun queryDocument(question: String, providerId: String = "openai", modelId: String = "gpt-4o-mini"): RagResult {
        return ragQueryEngine.query(question, providerId, modelId)
    }

    suspend fun deleteDocument(id: String) {
        ragQueryEngine.removeDocument(id)
        documentDao.deleteDocumentById(id)
    }

    suspend fun deleteAll() {
        ragQueryEngine.clearDocuments()
        documentDao.deleteAllDocuments()
    }

    fun getDocumentCount(): Int = ragQueryEngine.getDocumentCount()
}
