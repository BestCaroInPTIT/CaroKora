package com.caro.server.controller

import com.caro.server.dao.MatchDAO
import com.caro.server.dao.MoveDAO
import com.caro.server.dao.PlayerStatisticDAO
import com.caro.server.entity.GameBoard
import com.caro.server.entity.Match
import com.caro.server.entity.Move
import com.caro.server.entity.PlayerStatistic
import java.util.concurrent.ConcurrentHashMap

class HumanMatchServerController {
    private val matchDAO = MatchDAO()
    private val moveDAO = MoveDAO()
    private val playerStatisticDAO = PlayerStatisticDAO()

    private val queueLock = Any()
    // playerId -> thoi diem poll gan nhat (ms)
    private val waitingQueue = LinkedHashMap<Int, Long>()
    // playerId -> matchId vua duoc ghep cap
    private val matchedResults = ConcurrentHashMap<Int, Int>()

    private val matchLocks = ConcurrentHashMap<Int, Any>()

    data class RematchSession(
        val acceptedPlayerIds: MutableSet<Int> = mutableSetOf(),
        var refusedByPlayerId: Int? = null,
        var newMatchId: Int? = null
    )

    private val rematchSessions = ConcurrentHashMap<Int, RematchSession>()

    /**
     * Ghep cap 2 nguoi choi dang tim tran (timeout poll = 8 giay de tranh ghep voi nguoi da thoat)
     */
    fun handleFindAndCreateMatch(playerId: Int): Match? {
        if (playerId <= 0) return null

        synchronized(queueLock) {
            // Neu nguoi choi nay vua duoc doi thu ghep cap thanh cong -> tra ve Match ngay
            val readyMatchId = matchedResults.remove(playerId)
            if (readyMatchId != null) {
                return matchDAO.getMatchById(readyMatchId)
            }

            val now = System.currentTimeMillis()
            // Don dep nhung nguoi choi khong con poll trong 8 giay qua
            val expiredIter = waitingQueue.entries.iterator()
            while (expiredIter.hasNext()) {
                val entry = expiredIter.next()
                if (now - entry.value > 8_000L && entry.key != playerId) {
                    expiredIter.remove()
                }
            }

            // Tim doi thu khac dang doi trong hang doi
            val opponentId = waitingQueue.keys.firstOrNull { it != playerId }
            if (opponentId != null) {
                waitingQueue.remove(opponentId)
                waitingQueue.remove(playerId)
                // Nguoi vao truoc (opponentId) cam quan X, nguoi vao sau (playerId) cam quan O
                val createdMatch = matchDAO.createHumanMatch(opponentId, playerId)
                if (createdMatch != null) {
                    matchedResults[opponentId] = createdMatch.id
                }
                return createdMatch
            }

            // Chua co doi thu -> dua vao hang doi hoac cap nhat heartbeat
            waitingQueue[playerId] = now
            return null
        }
    }

    fun handleCancelFindMatch(playerId: Int) {
        synchronized(queueLock) {
            waitingQueue.remove(playerId)
            matchedResults.remove(playerId)
        }
    }

    /**
     * Xu ly nuoc di theo dung bieu do tuan tu:
     * addMove() -> updateBoardAndTurn() -> checkOutcome() -> getMoveHistory()
     * -> neu ket thuc: endMatchAndDetermineOutcome() -> getPlayerStatistic()
     */
    fun handleMakeMove(move: Move): Match? {
        val matchId = move.matchId
        if (matchId <= 0) return null
        val lock = matchLocks.computeIfAbsent(matchId) { Any() }

        synchronized(lock) {
            val match = matchDAO.getMatchById(matchId) ?: return null
            if (match.status != "PLAYING") return match

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
                    matchDAO.endMatchAndDeterMineOutcome(matchId, participant.id, isDraw = false)
                } else {
                    matchDAO.endMatchAndDeterMineOutcome(matchId, winnerPartId = 0, isDraw = true)
                }
                // Goi PlayerStatisticDAO.getPlayerStatistic() theo buoc 87-88
                for (mp in endedMatch?.matchParticipant ?: emptyList()) {
                    val pId = mp.player?.id ?: continue
                    val stat: PlayerStatistic? = playerStatisticDAO.getPlayerStatistic(pId)
                    if (stat != null) {
                        mp.player = stat
                    }
                }
                return endedMatch
            }

            return match
        }
    }

    fun checkOutcome(board: GameBoard, lastMove: Move): String {
        return CaroOutcomeChecker.checkOutcome(board, lastMove)
    }

    fun handleStopPlaying(matchId: Int, playerId: Int): Match? {
        val endedMatch = matchDAO.stopPlaying(matchId, playerId) ?: return null
        for (mp in endedMatch.matchParticipant) {
            val pId = mp.player?.id ?: continue
            val stat = playerStatisticDAO.getPlayerStatistic(pId)
            if (stat != null) mp.player = stat
        }
        return endedMatch
    }

    fun handleGetMatchState(matchId: Int): Match? {
        val match = matchDAO.getMatchById(matchId) ?: return null
        if (match.status == "FINISHED" || match.status == "STOPPED") {
            for (mp in match.matchParticipant) {
                val pId = mp.player?.id ?: continue
                val stat = playerStatisticDAO.getPlayerStatistic(pId)
                if (stat != null) mp.player = stat
            }
        }
        return match
    }

    data class RematchResponse(
        val status: String, // WAITING | CREATED | REFUSED
        val match: Match? = null
    )

    fun handleQuickRematch(oldMatchId: Int, playerId: Int, accept: Boolean): RematchResponse {
        val oldMatch = matchDAO.getMatchById(oldMatchId) ?: return RematchResponse("REFUSED", null)
        val playerIds = oldMatch.matchParticipant.mapNotNull { it.player?.id }
        if (playerIds.size < 2 || playerId !in playerIds) {
            return RematchResponse("REFUSED", null)
        }

        val session = rematchSessions.computeIfAbsent(oldMatchId) { RematchSession() }
        synchronized(session) {
            if (!accept) {
                session.refusedByPlayerId = playerId
                return RematchResponse("REFUSED", null)
            }
            if (session.refusedByPlayerId != null) {
                return RematchResponse("REFUSED", null)
            }
            if (session.newMatchId != null) {
                return RematchResponse("CREATED", matchDAO.getMatchById(session.newMatchId!!))
            }

            session.acceptedPlayerIds.add(playerId)
            if (session.acceptedPlayerIds.containsAll(playerIds)) {
                // Doi ben X/O cho van tai dau cong bang
                val newP1 = playerIds[1]
                val newP2 = playerIds[0]
                val newMatch = matchDAO.createHumanMatch(newP1, newP2)
                return if (newMatch != null) {
                    session.newMatchId = newMatch.id
                    RematchResponse("CREATED", newMatch)
                } else {
                    RematchResponse("REFUSED", null)
                }
            }
            return RematchResponse("WAITING", null)
        }
    }
}
