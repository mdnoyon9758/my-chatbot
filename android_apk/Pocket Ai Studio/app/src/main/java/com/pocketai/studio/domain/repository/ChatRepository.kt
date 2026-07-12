package com.pocketai.studio.domain.repository

import com.pocketai.studio.domain.model.ChatMessage
import com.pocketai.studio.domain.model.ChatSession
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getAllSessions(): Flow<List<ChatSession>>
    fun getSessionById(id: String): Flow<ChatSession?>
    fun searchSessions(query: String): Flow<List<ChatSession>>
    suspend fun createSession(title: String, modelId: String, modelName: String): ChatSession
    suspend fun updateSession(session: ChatSession)
    suspend fun deleteSession(id: String)
    suspend fun renameSession(id: String, title: String)
    suspend fun deleteAllSessions()
    suspend fun getSessionCount(): Int

    fun getMessagesByChatId(chatId: String): Flow<List<ChatMessage>>
    suspend fun getMessagesSync(chatId: String): List<ChatMessage>
    suspend fun insertMessage(chatId: String, role: String, content: String, tokenCount: Int = 0): ChatMessage
    suspend fun deleteMessage(id: String)
    suspend fun deleteMessagesByChatId(chatId: String)
    suspend fun getTotalMessageCount(): Int
}