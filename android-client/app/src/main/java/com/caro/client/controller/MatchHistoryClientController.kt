package com.caro.client.controller

import com.caro.client.entity.Match
import com.caro.client.entity.Move
import com.google.gson.reflect.TypeToken

class MatchHistoryClientController : ApiClient() {

    fun getMatchHistory(playerId: Int): ArrayList<Match> {
        val type = object : TypeToken<ArrayList<Match>>() {}.type
        return getJson<ArrayList<Match>>("/api/match-history/$playerId", type) ?: ArrayList()
    }

    fun getMatchDetail(playerId: Int, matchId: Int): Match? {
        return getJson("/api/match-history/$playerId/$matchId", Match::class.java)
    }

    fun getMoveHistory(playerId: Int, matchId: Int): ArrayList<Move> {
        val type = object : TypeToken<ArrayList<Move>>() {}.type
        return getJson<ArrayList<Move>>("/api/match-history/$playerId/$matchId/moves", type) ?: ArrayList()
    }
}
