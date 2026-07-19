package com.pocketai.studio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocketai.studio.data.local.entity.ArenaMatchupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArenaDao {
    @Query("SELECT * FROM arena_matchups ORDER BY createdAt DESC")
    fun getAllMatchups(): Flow<List<ArenaMatchupEntity>>

    @Query("SELECT * FROM arena_matchups WHERE id = :id")
    suspend fun getMatchupById(id: String): ArenaMatchupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatchup(matchup: ArenaMatchupEntity)

    @Query("DELETE FROM arena_matchups WHERE id = :id")
    suspend fun deleteMatchup(id: String)

    @Query("SELECT * FROM arena_matchups ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMatchups(limit: Int): List<ArenaMatchupEntity>

    @Query("UPDATE arena_matchups SET rankingsJson = :rankingsJson WHERE id = :id")
    suspend fun updateRankings(id: String, rankingsJson: String)

    @Query("UPDATE arena_matchups SET userVote = :modelId WHERE id = :matchupId")
    suspend fun recordVote(matchupId: String, modelId: String)
}
