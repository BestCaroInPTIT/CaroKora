package com.caro.client.controller

import com.caro.client.entity.Player
import com.caro.client.entity.PlayerStatistic

class PlayerClientController : ApiClient() {

    private data class BooleanResp(val success: Boolean = false)

    fun checkLogin(username: String, password: String): Player? {
        val payload = mapOf("username" to username, "password" to password)
        return postJson("/api/player/login", payload, Player::class.java)
    }

    fun getPlayerProfile(playerId: Int): PlayerStatistic? {
        return getJson("/api/player/$playerId/profile", PlayerStatistic::class.java)
    }

    fun changePassword(playerId: Int, oldPass: String, newPass: String): Boolean {
        val payload = mapOf(
            "playerId" to playerId,
            "oldPass" to oldPass,
            "newPass" to newPass
        )
        return postJson<BooleanResp>("/api/player/change-password", payload, BooleanResp::class.java)?.success == true
    }
}
