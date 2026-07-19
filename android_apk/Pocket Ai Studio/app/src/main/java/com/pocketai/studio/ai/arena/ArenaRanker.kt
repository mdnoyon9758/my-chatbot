package com.pocketai.studio.ai.arena

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pocketai.studio.ai.provider.ChatMessage
import com.pocketai.studio.ai.provider.ChatRequest
import com.pocketai.studio.data.repository.ProviderRepository
import javax.inject.Inject
import javax.inject.Singleton

data class RankedResponse(
    val modelId: String,
    val providerId: String,
    val score: Double,
    val reasoning: String,
    val rank: Int
)

private data class JudgeRankingEntry(
    val modelId: String,
    val score: Double,
    val reasoning: String
)

@Singleton
class ArenaRanker @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val gson: Gson
) {
    /**
     * Rank model responses using a judge model.
     * @param question The original question asked to all models
     * @param responses Map of "providerId:modelId" -> response text for each model
     * @param judgeModelProvider The provider to use as the judge (default: "openai")
     * @param judgeModelId The model ID to use as the judge (default: "gpt-4o")
     * @return List of [RankedResponse] sorted from best to worst
     */
    suspend fun rankResponses(
        question: String,
        responses: Map<String, String>,
        judgeModelProvider: String = "openai",
        judgeModelId: String = "gpt-4o"
    ): List<RankedResponse> {
        if (responses.isEmpty()) return emptyList()
        if (responses.size == 1) {
            val entry = responses.entries.first()
            val (providerId, modelId) = parseCompositeKey(entry.key)
            return listOf(RankedResponse(
                modelId = modelId,
                providerId = providerId,
                score = 100.0,
                reasoning = "Only one response to evaluate.",
                rank = 1
            ))
        }

        val provider = providerRepository.getProvider(judgeModelProvider)
            ?: throw IllegalStateException("Judge provider '$judgeModelProvider' not found")

        val prompt = buildRankingPrompt(question, responses)

        val request = ChatRequest(
            model = judgeModelId,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            temperature = 0.3f,
            maxTokens = 4096,
            topP = 0.9f,
            stream = false,
            systemPrompt = "You are an expert AI response evaluator. You must return ONLY valid JSON, no other text."
        )

        val chatResponse = provider.chat(request)
        val content = chatResponse.content.trim()

        return try {
            val cleaned = cleanJsonResponse(content)
            val listType = object : TypeToken<List<JudgeRankingEntry>>() {}.type
            val entries: List<JudgeRankingEntry> = gson.fromJson(cleaned, listType)

            // Sort by score descending and assign ranks
            entries
                .sortedByDescending { it.score }
                .mapIndexed { index, entry ->
                    val (providerId, modelId) = parseCompositeKey(entry.modelId)
                    RankedResponse(
                        modelId = modelId,
                        providerId = providerId,
                        score = entry.score.coerceIn(0.0, 100.0),
                        reasoning = entry.reasoning,
                        rank = index + 1
                    )
                }
        } catch (e: Exception) {
            // If parsing fails, assign equal scores
            responses.entries.mapIndexed { index, entry ->
                val (providerId, modelId) = parseCompositeKey(entry.key)
                RankedResponse(
                    modelId = modelId,
                    providerId = providerId,
                    score = 50.0,
                    reasoning = "Auto-ranking parsing failed: ${e.message}",
                    rank = index + 1
                )
            }
        }
    }

    private fun buildRankingPrompt(
        question: String,
        responses: Map<String, String>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("You are evaluating AI responses. Given the user's question and the responses from different AI models, rank them from best to worst.")
        sb.appendLine()
        sb.appendLine("Consider these criteria:")
        sb.appendLine("- Accuracy: Does the response correctly address the question?")
        sb.appendLine("- Helpfulness: How useful is the response to the user?")
        sb.appendLine("- Conciseness: Is the response appropriately detailed without being verbose?")
        sb.appendLine("- Completeness: Does the response cover all aspects of the question?")
        sb.appendLine()
        sb.appendLine("User Question: $question")
        sb.appendLine()

        responses.forEach { (key, response) ->
            sb.appendLine("--- Response from $key ---")
            sb.appendLine(response.take(2000)) // Limit response length for the judge
            sb.appendLine()
        }

        sb.appendLine("Return a JSON array of objects with these fields:")
        sb.appendLine("- modelId: string (use the exact key from \"--- Response from ... ---\" header)")
        sb.appendLine("- score: number (0-100)")
        sb.appendLine("- reasoning: string (brief explanation for the score)")
        sb.appendLine()
        sb.appendLine("Example:")
        sb.appendLine("""[{"modelId": "openai:gpt-4o", "score": 95, "reasoning": "..."}]""")
        sb.appendLine()
        sb.appendLine("Return ONLY valid JSON, no other text.")

        return sb.toString()
    }

    private fun cleanJsonResponse(raw: String): String {
        var cleaned = raw.trim()
        // Remove markdown code fences if present
        if (cleaned.startsWith("```")) {
            val firstNewline = cleaned.indexOf('\n')
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1)
            }
            val lastFence = cleaned.lastIndexOf("```")
            if (lastFence != -1) {
                cleaned = cleaned.substring(0, lastFence)
            }
        }
        return cleaned.trim()
    }

    private fun parseCompositeKey(key: String): Pair<String, String> {
        val parts = key.split(":", limit = 2)
        return if (parts.size == 2) {
            parts[0] to parts[1]
        } else {
            "unknown" to key
        }
    }
}
