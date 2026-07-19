package com.pocketai.studio.data.local.dao

import androidx.room.*
import com.pocketai.studio.data.local.entity.ApiKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys")
    fun getAllKeys(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys WHERE providerId = :providerId")
    suspend fun getKey(providerId: String): ApiKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: ApiKeyEntity)

    @Query("DELETE FROM api_keys WHERE providerId = :providerId")
    suspend fun deleteKey(providerId: String)

    @Query("DELETE FROM api_keys")
    suspend fun deleteAllKeys()

    @Query("SELECT apiKey FROM api_keys WHERE providerId = :providerId AND isEnabled = 1")
    suspend fun getActiveApiKey(providerId: String): String?
}
