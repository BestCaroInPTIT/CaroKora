package com.caro.client.controller

import com.caro.client.entity.Match
import com.caro.client.entity.Move
import com.caro.client.entity.PlayerStatistic
import com.google.gson.reflect.TypeToken

class ChallengeClientController : ApiClient() {

    private data class ChallengeMoveResp(val success: Boolean = false, val match: Match? = null)

    fun getOnlinePlayers(): ArrayList<PlayerStatistic> {
        val listType = object : TypeToken<ArrayList<PlayerStatistic>>() {}.type
        return getJson<ArrayList<PlayerStatistic>>("/api/challenge/online-players", listType) ?: ArrayList()
    }

    fun sendChallenge(p1Id: Int, p2Id: Int): Match? {
        val payload = mapOf("p1Id" to p1Id, "p2Id" to p2Id)
        return postJson("/api/challenge/send", payload, Match::class.java)
    }

    fun respondChallenge(matchId: Int, accept: Boolean): Match? {
        val payload = mapOf("matchId" to matchId, "accept" to accept)
        return postJson("/api/challenge/respond", payload, Match::class.java)
    }

    fun makeMove(move: Move): Boolean {
        val resp = postJson<ChallengeMoveResp>("/api/challenge/move", move, ChallengeMoveResp::class.java)
        return resp?.success == true
    }

    fun getMoveHistory(matchId: Int): ArrayList<Move> {
        val listType = object : TypeToken<ArrayList<Move>>() {}.type
        return getJson<ArrayList<Move>>("/api/challenge/match/$matchId/moves", listType) ?: ArrayList()
    }
}
