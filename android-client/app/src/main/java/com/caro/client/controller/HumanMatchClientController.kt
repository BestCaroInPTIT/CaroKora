package com.caro.client.controller

import com.caro.client.entity.Match
import com.caro.client.entity.Move

class HumanMatchClientController : ApiClient() {

    data class RematchResp(val status: String = "REFUSED", val match: Match? = null)

    fun findAndCreateMatch(playerId: Int): Match? {
        val payload = mapOf("playerId" to playerId)
        return postJson("/api/human-match/find", payload, Match::class.java)
    }

    fun makeMove(move: Move): Match? {
        return postJson("/api/human-match/move", move, Match::class.java)
    }

    fun cancelFindMatch(playerId: Int) {
        val payload = mapOf("playerId" to playerId)
        postJson<Map<String, Any>>("/api/human-match/cancel-find", payload, Map::class.java)
    }

    fun stopPlaying(matchId: Int, playerId: Int): Match? {
        val payload = mapOf("matchId" to matchId, "playerId" to playerId)
        return postJson("/api/human-match/stop", payload, Match::class.java)
    }

    fun quickRematch(oldMatchId: Int, playerId: Int, accept: Boolean): RematchResp? {
        val payload = mapOf(
            "oldMatchId" to oldMatchId,
            "playerId" to playerId,
            "accept" to accept
        )
        return postJson("/api/human-match/rematch", payload, RematchResp::class.java)
    }

    fun getMatchState(matchId: Int): Match? {
        return getJson("/api/match/$matchId", Match::class.java)
    }
}
