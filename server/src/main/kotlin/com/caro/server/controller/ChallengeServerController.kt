package com.caro.server.controller

import com.caro.server.dao.MatchDAO
import com.caro.server.dao.MoveDAO
import com.caro.server.dao.PlayerStatisticDAO
import com.caro.server.entity.GameBoard
import com.caro.server.entity.Match
import com.caro.server.entity.Move
import com.caro.server.entity.PlayerStatistic

class ChallengeServerController {
    private val playerStatisticDAO = PlayerStatisticDAO()
    private val matchDAO = MatchDAO()
    private val moveDAO = MoveDAO()

    private val matchLocks = java.util.concurrent.ConcurrentHashMap<Int, Any>()

    fun handleGetOnlinePlayers(): ArrayList<PlayerStatistic> {
        return playerStatisticDAO.getOnlinePlayers()
    }

    fun handleSendChallenge(p1Id: Int, p2Id: Int): Match? {
        if (p1Id <= 0 || p2Id <= 0 || p1Id == p2Id) return null
        return matchDAO.createChallengeMatch(p1Id, p2Id)
    }

    fun handleRespondChallenge(matchId: Int, accept: Boolean): Match? {
        if (matchId <= 0) return null
        return matchDAO.updateChallengeStatus(matchId, accept)
    }

    fun handleMakeMove(move: Move): Boolean {
        return processChallengeMove(move) != null
    }

    fun processChallengeMove(move: Move): Match? {
        val matchId = move.matchId
        if (matchId <= 0) return null
        val lock = matchLocks.computeIfAbsent(matchId) { Any() }

        synchronized(lock) {
            val match = matchDAO.getMatchById(matchId) ?: return null
            if (match.status != "PLAYING") return null

            val participant = match.matchParticipant.firstOrNull {
                it.id == move.matchParticipant?.id ||
                    (move.matchParticipant?.player?.id != null && it.player?.id == move.matchParticipant?.player?.id)
            } ?: return null

            if (!participant.side.equals(match.currentTurn, ignoreCase = true)) {
                return null
            }

            val rIdx = GameBoard.rowToIndex(move.row)
            val cIdx = move.column - 1
            if (rIdx !in 0 until GameBoard.BOARD_DIMENSION || cIdx !in 0 until GameBoard.BOARD_DIMENSION) {
                return null
            }
            if (match.gameBoard.getCell(rIdx, cIdx) != '.') {
                return null
            }

            move.matchParticipant = participant
            val added = moveDAO.addMove(move)
            if (!added) return null

            match.currentTurn = if (participant.side.uppercase() == "X") "O" else "X"
            matchDAO.updateBoardAndTurn(match)

            val history = moveDAO.getMoveHistory(matchId)
            match.move = history
            match.gameBoard.rebuildFromMoves(history)

            val outcome = checkOutcome(match.gameBoard, move)
            return when (outcome) {
                "WIN" -> matchDAO.endMatchAndDetermineOutcome(matchId, participant.id, isDraw = false)
                "DRAW" -> matchDAO.endMatchAndDetermineOutcome(matchId, winnerPartId = 0, isDraw = true)
                else -> match
            }
        }
    }

    fun checkOutcome(board: GameBoard, lastMove: Move): String {
        return CaroOutcomeChecker.checkOutcome(board, lastMove)
    }

    fun handleGetMoveHistory(matchId: Int): ArrayList<Move> {
        return moveDAO.getMoveHistory(matchId)
    }

    fun handleGetIncomingChallenge(playerId: Int): Match? {
        return matchDAO.getIncomingPendingChallenge(playerId)
    }

    fun handleCancelChallenge(matchId: Int): Boolean {
        return matchDAO.cancelPendingChallenge(matchId)
    }
}
