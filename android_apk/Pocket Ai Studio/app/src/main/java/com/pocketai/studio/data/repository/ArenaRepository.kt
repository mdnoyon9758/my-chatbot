package com.pocketai.studio.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pocketai.studio.ai.arena.RankedResponse
import com.pocketai.studio.data.local.dao.ArenaDao
import com.pocketai.studio.data.local.entity.ArenaMatchupEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ArenaMatchup(
    val id: String,
    val question: String,
    val models: List<Pair<String, String>>, // List of (providerId, modelId)
    val answers: Map<String, String>, // "providerId:modelId" -> full response text
    val rankings: List<RankedResponse>?,
    val vote: String?, // "providerId:modelId" the user voted for
    val createdAt: Long
)

@Singleton
class ArenaRepository @Inject constructor(
    private val arenaDao: ArenaDao,
    private val gson: Gson
) {
    fun getAllMatchups(): Flow<List<ArenaMatchup>> {
        return arenaDao.getAllMatchups().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getMatchupById(id: String): ArenaMatchup? {
        return arenaDao.getMatchupById(id)?.toDomain()
    }

    suspend fun createMatchup(
        question: String,
        models: List<Pair<String, String>>,
        answers: Map<String, String>
    ): String {
        val id = UUID.randomUUID().toString()
        val modelsJson = gson.toJson(models.map { "${it.first}:${it.second}" })
        val answersJson = gson.toJson(answers)

        val entity = ArenaMatchupEntity(
            id = id,
            question = question,
            modelsUsed = modelsJson,
            answers = answersJson,
            createdAt = System.currentTimeMillis()
        )

        arenaDao.insertMatchup(entity)
        return id
    }

    suspend fun updateMatchupRankings(matchupId: String, rankings: List<RankedResponse>) {
        val rankingsJson = gson.toJson(rankings)
        arenaDao.updateRankings(matchupId, rankingsJson)
    }

    suspend fun recordVote(matchupId: String, modelId: String) {
        arenaDao.recordVote(matchupId, modelId)
        val matchup = arenaDao.getMatchupById(matchupId)
        if (matchup != null) {
            arenaDao.insertMatchup(matchup.copy(userVote = modelId))
        }
    }

    suspend fun deleteMatchup(id: String) {
        arenaDao.deleteMatchup(id)
    }

    private fun ArenaMatchupEntity.toDomain(): ArenaMatchup {
        val modelsListType = object : TypeToken<List<String>>() {}.type
        val modelsJsonList: List<String> = gson.fromJson(modelsUsed, modelsListType)
        val models = modelsJsonList.map { s ->
            val parts = s.split(":", limit = 2)
            parts[0] to parts[1]
        }

        val answersMapType = object : TypeToken<Map<String, String>>() {}.type
        val answersMap: Map<String, String> = gson.fromJson(answers, answersMapType)

        val rankings: List<RankedResponse>? = rankingsJson?.let {
            val rankingsType = object : TypeToken<List<RankedResponse>>() {}.type
            gson.fromJson(it, rankingsType)
        }

        return ArenaMatchup(
            id = id,
            question = question,
            models = models,
            answers = answersMap,
            rankings = rankings,
            vote = userVote,
            createdAt = createdAt
        )
    }
}
