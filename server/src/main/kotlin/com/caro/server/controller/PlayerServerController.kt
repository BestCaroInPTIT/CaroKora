package com.caro.server.controller

import com.caro.server.dao.PlayerDAO
import com.caro.server.dao.PlayerStatisticDAO
import com.caro.server.entity.Player
import com.caro.server.entity.PlayerStatistic

class PlayerServerController {
    private val playerDAO = PlayerDAO()
    private val playerStatisticDAO = PlayerStatisticDAO()

    fun handleCheckLogin(username: String, password: String): Player? {
        if (username.isBlank() || password.isBlank()) return null
        return playerDAO.checkLogin(username, password)
    }

    fun handleGetPlayerProfile(playerID: Int): PlayerStatistic? {
        if (playerID <= 0) return null
        return playerStatisticDAO.getPlayerStatistic(playerID)
    }

    fun handleChangePassword(playerID: Int, oldPass: String, newPass: String): Boolean {
        if (playerID <= 0 || newPass.length < 6 || oldPass == newPass) return false
        return playerDAO.changePassword(playerID, oldPass, newPass)
    }

    // Ho tro dang ky tai khoan, dang xuat va cap nhat tieu su/anh dai dien tren giao dien
    fun handleRegister(
        gameID: String,
        password: String,
        fullName: String,
        email: String,
        avatar: String? = null
    ): Player? {
        if (gameID.isBlank() || password.length < 6) return null
        return playerDAO.registerPlayer(gameID, password, fullName, email, avatar)
    }

    fun handleLogout(playerID: Int): Boolean {
        if (playerID <= 0) return false
        return playerDAO.updateOnlineStatus(playerID, "OFFLINE")
    }

    fun handleUpdateProfile(playerID: Int, bio: String?, avatar: String?): Boolean {
        if (playerID <= 0) return false
        return playerDAO.updateBioAndAvatar(playerID, bio, avatar)
    }
}
