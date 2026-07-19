package com.pocketai.studio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_keys")
data class ApiKeyEntity(
    @PrimaryKey val providerId: String,
    val apiKey: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
