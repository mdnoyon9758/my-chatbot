package com.pocketai.studio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "arena_matchups")
data class ArenaMatchupEntity(
    @PrimaryKey val id: String,
    val question: String,
    val modelsUsed: String, // JSON array of "providerId:modelId" strings
    val answers: String, // JSON map of "providerId:modelId" -> full response text
    val rankingsJson: String? = null, // JSON array of ranked response objects
    val userVote: String? = null, // "providerId:modelId" the user voted for
    val createdAt: Long = System.currentTimeMillis()
)
