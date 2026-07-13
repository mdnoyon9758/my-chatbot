package com.pocketai.studio.domain.model

data class ChatMessage(
    val id: String,
    val chatId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val tokenCount: Int = 0,
    val isStreaming: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val toolUsed: String? = null
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }

enum class AttachmentType { IMAGE, PDF }

data class Attachment(
    val uri: String,
    val type: AttachmentType,
    val name: String
)
