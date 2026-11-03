package com.caro.client.controller

import com.caro.client.entity.PlayerStatistic
import com.google.gson.reflect.TypeToken

class RankingClientController : ApiClient() {

    fun getRanking(): ArrayList<PlayerStatistic> {
        val type = object : TypeToken<ArrayList<PlayerStatistic>>() {}.type
        return getJson<ArrayList<PlayerStatistic>>("/api/ranking", type) ?: ArrayList()
    }

    fun getPlayerStatistics(playerId: Int): PlayerStatistic? {
        if (playerId <= 0) return null
        return getJson(
            "/api/ranking/$playerId/statistics",
            PlayerStatistic::class.java
        )
    }
}
