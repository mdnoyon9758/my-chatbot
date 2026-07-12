package com.pocketai.studio.domain.model

data class ChatSession(
    val id: String,
    val title: String,
    val modelId: String,
    val modelName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0,
    val lastPreview: String = ""
)