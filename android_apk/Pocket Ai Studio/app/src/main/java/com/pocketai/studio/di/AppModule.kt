package com.pocketai.studio.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.pocketai.studio.ai.engine.AiEngine
import com.pocketai.studio.ai.modelmanager.ModelManager
import com.pocketai.studio.data.local.dao.ApiKeyDao
import com.pocketai.studio.data.local.dao.ArenaDao
import com.pocketai.studio.data.local.dao.ChatSessionDao
import com.pocketai.studio.data.local.dao.DocumentDao
import com.pocketai.studio.data.local.dao.MessageDao
import com.pocketai.studio.data.local.database.AppDatabase
import com.pocketai.studio.data.repository.ChatRepositoryImpl
import com.pocketai.studio.data.repository.SettingsRepositoryImpl
import com.pocketai.studio.domain.repository.ChatRepository
import com.pocketai.studio.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context, AppDatabase::class.java, "pocket_ai_studio.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideChatSessionDao(db: AppDatabase): ChatSessionDao = db.chatSessionDao()

    @Provides
    @Singleton
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    @Singleton
    fun provideApiKeyDao(db: AppDatabase): ApiKeyDao = db.apiKeyDao()

    @Provides
    @Singleton
    fun provideArenaDao(db: AppDatabase): ArenaDao = db.arenaDao()

    @Provides
    @Singleton
    fun provideDocumentDao(db: AppDatabase): DocumentDao = db.documentDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideChatRepository(impl: ChatRepositoryImpl): ChatRepository = impl

    @Provides
    @Singleton
    fun provideSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository = impl

}
