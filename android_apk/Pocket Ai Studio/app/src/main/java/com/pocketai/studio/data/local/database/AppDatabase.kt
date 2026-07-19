package com.pocketai.studio.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pocketai.studio.data.local.dao.ApiKeyDao
import com.pocketai.studio.data.local.dao.ArenaDao
import com.pocketai.studio.data.local.dao.ChatSessionDao
import com.pocketai.studio.data.local.dao.DocumentDao
import com.pocketai.studio.data.local.dao.MessageDao
import com.pocketai.studio.data.local.entity.ApiKeyEntity
import com.pocketai.studio.data.local.entity.ArenaMatchupEntity
import com.pocketai.studio.data.local.entity.ChatSessionEntity
import com.pocketai.studio.data.local.entity.DocumentEntity
import com.pocketai.studio.data.local.entity.MessageEntity

@Database(
    entities = [
        ChatSessionEntity::class,
        MessageEntity::class,
        ApiKeyEntity::class,
        ArenaMatchupEntity::class,
        DocumentEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun messageDao(): MessageDao
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun arenaDao(): ArenaDao
    abstract fun documentDao(): DocumentDao
}
