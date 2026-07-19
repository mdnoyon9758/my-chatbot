package com.pocketai.studio.ai.provider

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val topP: Float = 0.9f,
    val stream: Boolean = true,
    val systemPrompt: String? = null
)

data class ChatMessage(
    val role: String,
    val content: String
)
