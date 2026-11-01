package com.caro.server.controller

import com.caro.server.dao.DAO
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
        val matchId = move.matchId
        if (matchId <= 0) return false
        synchronized(DAO.lockForMatch(matchId)) {
            val match = DAO.checkAndEnforceTurnTimeout(matchId, graceSeconds = 1.0) ?: return false
            if (match.status != "PLAYING") return false

            val participant = match.matchParticipant.firstOrNull {
                it.id == move.matchParticipant?.id ||
                    (move.matchParticipant?.player?.id != null && it.player?.id == move.matchParticipant?.player?.id)
            } ?: return false

            if (!participant.side.equals(match.currentTurn, ignoreCase = true)) {
                return false
            }

            val rIdx = GameBoard.rowToIndex(move.row)
            val cIdx = move.column - 1
            if (rIdx !in 0 until GameBoard.BOARD_DIMENSION || cIdx !in 0 until GameBoard.BOARD_DIMENSION) {
                return false
            }
            if (match.gameBoard.getCell(rIdx, cIdx) != '.') {
                return false
            }

            move.matchParticipant = participant
            if (!moveDAO.addMove(move)) return false

            match.currentTurn = if (participant.side.uppercase() == "X") "O" else "X"
            matchDAO.updateBoardAndTurn(match)

            val history = moveDAO.getMoveHistory(matchId)
            match.move = history
            match.gameBoard.rebuildFromMoves(history)

            val outcome = checkOutcome(match.gameBoard, move)
            if (outcome == "WIN") {
                matchDAO.endMatchAndDetermineOutcome(matchId, participant.id, isDraw = false)
            } else if (outcome == "DRAW") {
                matchDAO.endMatchAndDetermineOutcome(matchId, winnerPartId = 0, isDraw = true)
            }
            return true
        }
    }

    fun checkOutcome(board: GameBoard, lastMove: Move): String {
        val r = GameBoard.rowToIndex(lastMove.row)
        val c = lastMove.column - 1
        if (r !in 0 until GameBoard.BOARD_DIMENSION || c !in 0 until GameBoard.BOARD_DIMENSION) {
            return "ONGOING"
        }

        val symbol = board.getCell(r, c)
        if (symbol != 'X' && symbol != 'O') return "ONGOING"

        fun countDir(dR: Int, dC: Int): Int {
            var count = 0
            var cr = r + dR
            var cc = c + dC
            while (cr in 0 until GameBoard.BOARD_DIMENSION && cc in 0 until GameBoard.BOARD_DIMENSION) {
                if (board.getCell(cr, cc) == symbol) {
                    count++
                    cr += dR
                    cc += dC
                } else {
                    break
                }
            }
            return count
        }

        val directions = arrayOf(
            intArrayOf(0, 1),
            intArrayOf(1, 0),
            intArrayOf(1, 1),
            intArrayOf(1, -1)
        )

        for (dir in directions) {
            val dR = dir[0]
            val dC = dir[1]
            val forward = countDir(dR, dC)
            val backward = countDir(-dR, -dC)
            val total = 1 + forward + backward

            if (total >= 5) return "WIN"

            if (total == 4) {
                val afterR = r + (forward + 1) * dR
                val afterC = c + (forward + 1) * dC
                val beforeR = r - (backward + 1) * dR
                val beforeC = c - (backward + 1) * dC

                val afterOpen = afterR in 0 until GameBoard.BOARD_DIMENSION &&
                    afterC in 0 until GameBoard.BOARD_DIMENSION &&
                    board.getCell(afterR, afterC) == '.'
                val beforeOpen = beforeR in 0 until GameBoard.BOARD_DIMENSION &&
                    beforeC in 0 until GameBoard.BOARD_DIMENSION &&
                    board.getCell(beforeR, beforeC) == '.'

                if (afterOpen && beforeOpen) return "WIN"
            }
        }

        if (!board.boardState.contains('.')) return "DRAW"
        return "ONGOING"
    }

    fun handleGetMoveHistory(matchId: Int): ArrayList<Move> {
        return moveDAO.getMoveHistory(matchId)
    }
}
