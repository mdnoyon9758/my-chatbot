package com.pocketai.studio.ai.rag

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextChunker @Inject constructor() {
    fun chunk(text: String, chunkSize: Int = 1000, overlap: Int = 200): List<String> {
        if (text.length <= chunkSize) return listOf(text)
        val chunks = mutableListOf<String>()
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
        var current = StringBuilder()
        for (para in paragraphs) {
            if (current.length + para.length > chunkSize && current.isNotEmpty()) {
                chunks.add(current.toString().trim())
                val overlapText = getOverlapText(current.toString(), overlap)
                current = StringBuilder(overlapText)
            }
            if (para.length > chunkSize) {
                val sentences = para.split(Regex("(?<=[.!?])\\s+"))
                for (sentence in sentences) {
                    if (current.length + sentence.length > chunkSize && current.isNotEmpty()) {
                        chunks.add(current.toString().trim())
                        val overlapText = getOverlapText(current.toString(), overlap)
                        current = StringBuilder(overlapText)
                    }
                    current.append(sentence).append(' ')
                }
            } else {
                current.append(para).append("\n\n")
            }
        }
        if (current.isNotBlank()) chunks.add(current.toString().trim())
        return chunks
    }

    private fun getOverlapText(text: String, overlapChars: Int): String {
        if (text.length <= overlapChars) return text
        val start = text.length - overlapChars
        val sentenceStart = text.indexOf('.', start)
        return if (sentenceStart > 0 && sentenceStart < text.length - 1) {
            text.substring(sentenceStart + 1).trim() + "\n"
        } else {
            text.substring(start).trim() + "\n"
        }
    }

    fun estimateTokens(text: String): Int = text.split(Regex("\\s+")).size
}
