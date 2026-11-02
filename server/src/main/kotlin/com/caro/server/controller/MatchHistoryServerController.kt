package com.caro.server.controller

import com.caro.server.dao.MatchDAO
import com.caro.server.dao.MoveDAO
import com.caro.server.dao.DAO
import com.caro.server.entity.Match
import com.caro.server.entity.Move

class MatchHistoryServerController {
    private val matchDAO = MatchDAO()
    private val moveDAO = MoveDAO()

    fun handleGetMatchHistory(playerId: Int): ArrayList<Match> {
        if (playerId <= 0) return ArrayList()
        return matchDAO.getMatchHistory(playerId)
    }

    fun handleGetMatchDetail(matchId: Int, playerId: Int): Match? {
        if (matchId <= 0 || playerId <= 0) return null
        val match = DAO.getMatchById(matchId) ?: return null
        val belongsToPlayer = match.matchParticipant.any { it.player?.id == playerId }
        return if (belongsToPlayer) match else null
    }

    fun handleGetMoveHistory(playerId: Int, matchId: Int): ArrayList<Move> {
        if (playerId <= 0 || matchId <= 0) return ArrayList()
        val belongsToPlayer = handleGetMatchDetail(matchId, playerId) != null
        return if (belongsToPlayer) moveDAO.getMoveHistory(matchId) else ArrayList()
    }
}
