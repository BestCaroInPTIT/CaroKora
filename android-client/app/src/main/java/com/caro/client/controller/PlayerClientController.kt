package com.caro.client.controller

import com.caro.client.entity.Match
import com.caro.client.entity.Player
import com.caro.client.entity.PlayerStatistic

class PlayerClientController : ApiClient() {

    private data class BooleanResp(val success: Boolean = false)
    data class HeartbeatResp(val alive: Boolean = false, val incomingChallenge: Match? = null)

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

    fun registerPlayer(gameID: String, password: String, fullName: String, email: String): Player? {
        val payload = mapOf(
            "gameID" to gameID,
            "password" to password,
            "fullName" to fullName,
            "email" to email
        )
        return postJson("/api/player/register", payload, Player::class.java)
    }

    fun logout(playerId: Int): Boolean {
        val payload = mapOf("playerId" to playerId, "onlineStatus" to "OFFLINE")
        return postJson<BooleanResp>("/api/player/logout", payload, BooleanResp::class.java)?.success == true
    }

    fun updateProfile(playerId: Int, bio: String?, avatar: String? = null): Boolean {
        val payload = mapOf("playerId" to playerId, "bio" to bio, "avatar" to avatar)
        return postJson<BooleanResp>("/api/player/update-profile", payload, BooleanResp::class.java)?.success == true
    }

    fun sendHeartbeat(playerId: Int): HeartbeatResp? {
        val payload = mapOf("playerId" to playerId)
        return postJson("/api/player/heartbeat", payload, HeartbeatResp::class.java)
    }
}
