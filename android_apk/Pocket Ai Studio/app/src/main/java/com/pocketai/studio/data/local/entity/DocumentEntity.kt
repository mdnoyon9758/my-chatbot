package com.pocketai.studio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long = 0,
    val chunkCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
