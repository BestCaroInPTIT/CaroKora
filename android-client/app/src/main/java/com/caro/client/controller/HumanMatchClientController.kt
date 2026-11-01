package com.caro.client.controller

import com.caro.client.entity.Match
import com.caro.client.entity.Move

class HumanMatchClientController : ApiClient() {

    fun findAndCreateMatch(playerId: Int): Match? {
        val payload = mapOf("playerId" to playerId)
        return postJson("/api/human-match/find", payload, Match::class.java)
    }

    fun makeMove(move: Move): Match? {
        return postJson("/api/human-match/move", move, Match::class.java)
    }
}
