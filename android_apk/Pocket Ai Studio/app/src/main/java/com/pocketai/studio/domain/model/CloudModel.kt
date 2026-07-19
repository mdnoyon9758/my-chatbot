package com.pocketai.studio.domain.model

data class CloudModel(
    val id: String,           // Provider-specific model ID like "gpt-4o"
    val name: String,         // Display name like "GPT-4o"
    val providerId: String,   // "openai", "anthropic", etc.
    val providerName: String, // "OpenAI", "Anthropic", etc.
    val contextWindow: Int = 8192,
    val maxOutputTokens: Int = 4096,
    val pricingPer1kInput: Double = 0.0,
    val pricingPer1kOutput: Double = 0.0,
    val isFree: Boolean = false,
    val capabilities: List<String> = listOf("chat"), // "chat", "vision", "tools", "streaming"
    val isVision: Boolean = false,
    val isStreaming: Boolean = true,
    val description: String = "",
    val developer: String = ""
)
