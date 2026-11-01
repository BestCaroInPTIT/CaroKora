package com.caro.server.controller

import com.caro.server.dao.DAO
import com.caro.server.dao.MatchDAO
import com.caro.server.dao.MoveDAO
import com.caro.server.dao.PlayerStatisticDAO
import com.caro.server.entity.GameBoard
import com.caro.server.entity.Match
import com.caro.server.entity.Move
import java.util.concurrent.ConcurrentHashMap

class HumanMatchServerController {
    private val matchDAO = MatchDAO()
    private val moveDAO = MoveDAO()
    private val playerStatisticDAO = PlayerStatisticDAO()

    private val queueLock = Any()
    private val waitingQueue = LinkedHashMap<Int, Long>()
    private val matchedResults = ConcurrentHashMap<Int, Int>()

    fun handleFindAndCreateMatch(playerId: Int): Match? {
        if (playerId < 0) {
            val targetId = -playerId
            synchronized(queueLock) {
                waitingQueue.remove(targetId)
                matchedResults.remove(targetId)
            }
            return null
        }
        if (playerId == 0) return null

        synchronized(queueLock) {
            val readyMatchId = matchedResults.remove(playerId)
            if (readyMatchId != null) {
                return DAO.getMatchById(readyMatchId)
            }

            val now = System.currentTimeMillis()
            val expiredIter = waitingQueue.entries.iterator()
            while (expiredIter.hasNext()) {
                val entry = expiredIter.next()
                if (now - entry.value > 8_000L && entry.key != playerId) {
                    expiredIter.remove()
                }
            }

            val opponentId = waitingQueue.keys.firstOrNull { it != playerId }
            if (opponentId != null) {
                waitingQueue.remove(opponentId)
                waitingQueue.remove(playerId)
                val createdMatch = matchDAO.createHumanMatch(opponentId, playerId)
                if (createdMatch != null) {
                    matchedResults[opponentId] = createdMatch.id
                }
                return createdMatch
            }

            waitingQueue[playerId] = now
            return null
        }
    }

    fun handleMakeMove(move: Move): Match? {
        val matchId = move.matchId
        if (matchId <= 0) return null

        synchronized(DAO.lockForMatch(matchId)) {
            val match = DAO.checkAndEnforceTurnTimeout(matchId, graceSeconds = 1.0) ?: return null
            if (match.status != "PLAYING") {
                for (mp in match.matchParticipant) {
                    val pId = mp.player?.id ?: continue
                    playerStatisticDAO.getPlayerStatistics(pId)?.let { mp.player = it }
                }
                return match
            }

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
            if (!moveDAO.addMove(move)) return null

            match.currentTurn = if (participant.side.uppercase() == "X") "O" else "X"
            matchDAO.updateBoardAndTurn(match)

            val history = moveDAO.getMoveHistory(matchId)
            match.move = history
            match.gameBoard.rebuildFromMoves(history)

            val outcome = checkOutcome(match.gameBoard, move)
            if (outcome == "WIN" || outcome == "DRAW") {
                val endedMatch = if (outcome == "WIN") {
                    matchDAO.endMatchAndDetermineOutcome(matchId, participant.id, isDraw = false)
                } else {
                    matchDAO.endMatchAndDetermineOutcome(matchId, winnerPartId = 0, isDraw = true)
                }
                for (mp in endedMatch?.matchParticipant ?: emptyList()) {
                    val pId = mp.player?.id ?: continue
                    playerStatisticDAO.getPlayerStatistics(pId)?.let { mp.player = it }
                }
                return endedMatch
            }

            return match
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
}
