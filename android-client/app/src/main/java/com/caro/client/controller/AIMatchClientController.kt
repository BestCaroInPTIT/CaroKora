package com.caro.client.controller

import com.caro.client.entity.Match
import com.caro.client.entity.Move

class AIMatchClientController : ApiClient() {
    fun createMatch(playerId: Int, difficulty: String): Match? {
        return postJson(
            "/api/ai-match/create",
            mapOf("playerId" to playerId, "difficulty" to difficulty),
            Match::class.java
        )
    }

    fun makeMove(move: Move): Match? {
        return postJson("/api/ai-match/move", move, Match::class.java)
    }
}
