package com.caro.server.controller

import com.caro.server.dao.PlayerStatisticDAO
import com.caro.server.entity.PlayerStatistic

class RankingServerController {
    private val playerStatisticDAO = PlayerStatisticDAO()

    fun handleGetRanking(): ArrayList<PlayerStatistic> {
        return playerStatisticDAO.getRanking()
    }

    fun handleGetPlayerStatistics(playerId: Int): PlayerStatistic? {
        if (playerId <= 0) return null
        return playerStatisticDAO.getPlayerStatistic(playerId)
    }
}
