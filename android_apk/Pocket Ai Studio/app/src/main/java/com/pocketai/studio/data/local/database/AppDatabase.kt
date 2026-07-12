package com.pocketai.studio.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pocketai.studio.data.local.dao.ChatSessionDao
import com.pocketai.studio.data.local.dao.MessageDao
import com.pocketai.studio.data.local.entity.ChatSessionEntity
import com.pocketai.studio.data.local.entity.MessageEntity

@Database(
    entities = [ChatSessionEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun messageDao(): MessageDao
}