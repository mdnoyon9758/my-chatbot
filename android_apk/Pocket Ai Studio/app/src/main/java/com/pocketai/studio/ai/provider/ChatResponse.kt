package com.pocketai.studio.ai.provider

data class ChatResponse(
    val content: String,
    val model: String,
    val providerId: String,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val finishReason: String = "stop"
)
