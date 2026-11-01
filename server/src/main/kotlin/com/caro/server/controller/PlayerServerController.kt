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
}
