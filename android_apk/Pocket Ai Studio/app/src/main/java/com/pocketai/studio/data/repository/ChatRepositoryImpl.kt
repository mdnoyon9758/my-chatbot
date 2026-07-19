package com.pocketai.studio.data.repository

import com.google.gson.Gson
import com.pocketai.studio.data.local.dao.ChatSessionDao
import com.pocketai.studio.data.local.dao.MessageDao
import com.pocketai.studio.data.local.entity.ChatSessionEntity
import com.pocketai.studio.data.local.entity.MessageEntity
import com.pocketai.studio.domain.model.Attachment
import com.pocketai.studio.domain.model.ChatMessage
import com.pocketai.studio.domain.model.ChatSession
import com.pocketai.studio.domain.model.MessageRole
import com.pocketai.studio.domain.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatSessionDao: ChatSessionDao,
    private val messageDao: MessageDao
) : ChatRepository {

    private val gson = Gson()

    override fun getAllSessions(): Flow<List<ChatSession>> =
        chatSessionDao.getAllSessions().map { list -> list.map { it.toDomain() } }

    override fun getSessionById(id: String): Flow<ChatSession?> =
        flow {
            val session = withContext(Dispatchers.IO) {
                chatSessionDao.getSessionById(id)
            }
            emit(session?.toDomain())
        }

    override fun searchSessions(query: String): Flow<List<ChatSession>> =
        chatSessionDao.searchSessions(query).map { list -> list.map { it.toDomain() } }

    override suspend fun createSession(title: String, modelId: String, modelName: String): ChatSession {
        val now = System.currentTimeMillis()
        val entity = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "New Chat" },
            modelId = modelId, modelName = modelName,
            createdAt = now, updatedAt = now
        )
        chatSessionDao.insertSession(entity)
        return entity.toDomain()
    }

    override suspend fun updateSession(session: ChatSession) {
        chatSessionDao.updateSession(session.toEntity())
    }

    override suspend fun deleteSession(id: String) = chatSessionDao.deleteSessionById(id)

    override suspend fun renameSession(id: String, title: String) = chatSessionDao.updateTitle(id, title)

    override suspend fun deleteAllSessions() = chatSessionDao.deleteAllSessions()

    override suspend fun getSessionCount(): Int = chatSessionDao.getCount()

    override fun getMessagesByChatId(chatId: String): Flow<List<ChatMessage>> =
        messageDao.getMessagesByChatId(chatId).map { list -> list.map { it.toDomain() } }

    override suspend fun getMessagesSync(chatId: String): List<ChatMessage> =
        messageDao.getMessagesByChatIdSync(chatId).map { it.toDomain() }

    override suspend fun insertMessage(
        chatId: String,
        role: String,
        content: String,
        tokenCount: Int,
        attachments: List<Attachment>,
        toolUsed: String?
    ): ChatMessage {
        val attachmentsJson = gson.toJson(attachments)
        val msg = MessageEntity(
            id = UUID.randomUUID().toString(), chatId = chatId,
            role = role, content = content,
            timestamp = System.currentTimeMillis(), tokenCount = tokenCount,
            attachments = attachmentsJson, toolUsed = toolUsed
        )
        messageDao.insertMessage(msg)
        val count = messageDao.getMessageCount(chatId)
        val preview = if (content.length > 100) content.take(100) + "..." else content
        chatSessionDao.updateMetadata(chatId, count, preview, System.currentTimeMillis())
        return msg.toDomain()
    }

    override suspend fun deleteMessage(id: String) {
        messageDao.deleteMessageById(id)
    }

    override suspend fun deleteMessagesByChatId(chatId: String) = messageDao.deleteMessagesByChatId(chatId)

    override suspend fun getTotalMessageCount(): Int = messageDao.getTotalCount()

    private fun ChatSessionEntity.toDomain() = ChatSession(
        id, title, modelId, modelName, createdAt, updatedAt, messageCount, lastPreview
    )

    private fun ChatSession.toEntity() = ChatSessionEntity(
        id, title, modelId, modelName, createdAt, updatedAt, messageCount, lastPreview
    )

    private fun MessageEntity.toDomain(): ChatMessage {
        val attachmentsList = try {
            gson.fromJson(attachments, Array<Attachment>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return ChatMessage(
            id, chatId,
            when (role) { "USER" -> MessageRole.USER; "ASSISTANT" -> MessageRole.ASSISTANT; else -> MessageRole.SYSTEM },
            content, timestamp, tokenCount, attachments = attachmentsList, toolUsed = toolUsed
        )
    }
}
