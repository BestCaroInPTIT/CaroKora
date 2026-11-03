package com.caro.server.controller

import com.caro.server.dao.DAO
import com.caro.server.dao.MatchDAO
import com.caro.server.entity.GameBoard
import com.caro.server.entity.Match
import com.caro.server.entity.Move
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class AIMatchServerController {
    private val matchDAO = MatchDAO()
    private val humanMatchController = HumanMatchServerController()

    fun handleCreateMatch(playerId: Int, difficulty: String): Match? {
        return matchDAO.createAIMatch(playerId, difficulty)
    }

    fun handleMakeMove(move: Move): Match? {
        val matchId = move.matchId
        if (matchId <= 0) return null

        return synchronized(DAO.lockForMatch(matchId)) {
            val match = DAO.getMatchById(matchId) ?: return@synchronized null
            if (match.mode.uppercase() != "AI" || match.status != "PLAYING") {
                return@synchronized match
            }

            val playerParticipant = match.matchParticipant.firstOrNull {
                it.participantType.uppercase() == "HUMAN"
            } ?: return@synchronized null
            val aiParticipant = match.matchParticipant.firstOrNull {
                it.participantType.uppercase() == "AI"
            } ?: return@synchronized null

            if (move.matchParticipant?.player?.id != null &&
                playerParticipant.player?.id != move.matchParticipant?.player?.id
            ) return@synchronized null

            val afterPlayer = humanMatchController.handleMakeMove(
                Move(
                    row = move.row,
                    column = move.column,
                    moveOrder = move.moveOrder,
                    matchParticipant = playerParticipant,
                    matchId = matchId
                )
            ) ?: return@synchronized null
            if (afterPlayer.status != "PLAYING" || afterPlayer.currentTurn.uppercase() != "O") {
                return@synchronized afterPlayer
            }

            val latest = DAO.getMatchById(matchId) ?: return@synchronized null
            val difficulty = aiParticipant.aiDifficulty ?: "beginner"
            val aiCell = chooseMove(latest.gameBoard, difficulty)
                ?: return@synchronized latest

            humanMatchController.handleMakeMove(
                Move(
                    row = GameBoard.indexToRow(aiCell.first),
                    column = aiCell.second + 1,
                    moveOrder = latest.move.size + 2,
                    matchParticipant = aiParticipant,
                    matchId = matchId
                )
            )
        }
    }

    private fun chooseMove(board: GameBoard, difficulty: String): Pair<Int, Int>? {
        val candidates = candidateCells(board)
        if (candidates.isEmpty()) return null

        return when (difficulty.lowercase()) {
            "beginner" -> chooseBeginner(board, candidates)
            "intermediate" -> chooseIntermediate(board, candidates)
            "expert" -> chooseExpert(board, candidates)
            else -> chooseBeginner(board, candidates)
        }
    }

    private fun chooseBeginner(board: GameBoard, candidates: List<Pair<Int, Int>>): Pair<Int, Int>? {
        findWinningMove(board, candidates, 'O')?.let { return it }
        val nearCandidates = candidates.filter { hasNeighbor(board, it.first, it.second, 1) }
        val pool = if (nearCandidates.isNotEmpty()) nearCandidates else candidates
        return pool.random(Random.Default)
    }

    private fun chooseIntermediate(board: GameBoard, candidates: List<Pair<Int, Int>>): Pair<Int, Int>? {
        findWinningMove(board, candidates, 'O')?.let { return it }
        findWinningMove(board, candidates, 'X')?.let { return it }
        return candidates.maxByOrNull { (r, c) ->
            positionScore(board, r, c, 'O') + positionScore(board, r, c, 'X') / 2
        }
    }

    private fun chooseExpert(board: GameBoard, candidates: List<Pair<Int, Int>>): Pair<Int, Int>? {
        findWinningMove(board, candidates, 'O')?.let { return it }
        findWinningMove(board, candidates, 'X')?.let { return it }

        val startTime = System.currentTimeMillis()
        val timeoutMs = 1500L
        var bestMove = candidates.maxByOrNull { (r, c) ->
            positionScore(board, r, c, 'O') + positionScore(board, r, c, 'X')
        } ?: return null
        var bestScore = Int.MIN_VALUE

        for ((r, c) in candidates) {
            if (System.currentTimeMillis() - startTime >= timeoutMs) break
            val copy = board.boardState.toCharArray()
            copy[r * GameBoard.BOARD_DIMENSION + c] = 'O'
            val score = minimax(
                copy,
                3,
                false,
                Int.MIN_VALUE,
                Int.MAX_VALUE,
                startTime,
                timeoutMs
            )
            if (score > bestScore) {
                bestScore = score
                bestMove = r to c
            }
        }
        return bestMove
    }

    private fun minimax(
        state: CharArray,
        depth: Int,
        maximizing: Boolean,
        alphaInput: Int,
        betaInput: Int,
        startTime: Long,
        timeoutMs: Long
    ): Int {
        if (depth <= 0 || System.currentTimeMillis() - startTime >= timeoutMs) {
            return evaluateState(state, 'O') - evaluateState(state, 'X')
        }

        var alpha = alphaInput
        var beta = betaInput
        val tempBoard = GameBoard(boardState = String(state))
        val moves = candidateCells(tempBoard).take(18)
        if (moves.isEmpty()) return 0

        if (maximizing) {
            var value = Int.MIN_VALUE
            for ((r, c) in moves) {
                if (hasImmediateWin(state, r, c, 'O')) return 100000
                state[r * GameBoard.BOARD_DIMENSION + c] = 'O'
                val score = minimax(state, depth - 1, false, alpha, beta, startTime, timeoutMs)
                state[r * GameBoard.BOARD_DIMENSION + c] = '.'
                value = max(value, score)
                alpha = max(alpha, value)
                if (beta <= alpha) break
            }
            return value
        } else {
            var value = Int.MAX_VALUE
            for ((r, c) in moves) {
                if (hasImmediateWin(state, r, c, 'X')) return -100000
                state[r * GameBoard.BOARD_DIMENSION + c] = 'X'
                val score = minimax(state, depth - 1, true, alpha, beta, startTime, timeoutMs)
                state[r * GameBoard.BOARD_DIMENSION + c] = '.'
                value = min(value, score)
                beta = min(beta, value)
                if (beta <= alpha) break
            }
            return value
        }
    }

    private fun candidateCells(board: GameBoard): List<Pair<Int, Int>> {
        val candidates = ArrayList<Pair<Int, Int>>()
        var hasStone = false
        val occupied = ArrayList<Pair<Int, Int>>()
        for (r in 0 until GameBoard.BOARD_DIMENSION) {
            for (c in 0 until GameBoard.BOARD_DIMENSION) {
                if (board.getCell(r, c) != '.') occupied.add(r to c)
            }
        }
        hasStone = occupied.isNotEmpty()
        if (!hasStone) return listOf(8 to 8, 8 to 9, 9 to 8, 9 to 9)

        for (r in 0 until GameBoard.BOARD_DIMENSION) {
            for (c in 0 until GameBoard.BOARD_DIMENSION) {
                if (board.getCell(r, c) == '.' && hasNeighbor(board, r, c, 2)) {
                    candidates.add(r to c)
                }
            }
        }
        return candidates.sortedByDescending { (r, c) ->
            positionScore(board, r, c, 'O') + positionScore(board, r, c, 'X')
        }
    }

    private fun hasNeighbor(board: GameBoard, row: Int, col: Int, radius: Int): Boolean {
        for (r in row - radius..row + radius) {
            for (c in col - radius..col + radius) {
                if (r == row && c == col) continue
                if (r in 0 until GameBoard.BOARD_DIMENSION &&
                    c in 0 until GameBoard.BOARD_DIMENSION &&
                    board.getCell(r, c) != '.'
                ) return true
            }
        }
        return false
    }

    private fun findWinningMove(
        board: GameBoard,
        candidates: List<Pair<Int, Int>>,
        symbol: Char
    ): Pair<Int, Int>? {
        return candidates.firstOrNull { (r, c) ->
            val copy = board.boardState.toCharArray()
            copy[r * GameBoard.BOARD_DIMENSION + c] = symbol
            hasImmediateWin(copy, r, c, symbol)
        }
    }

    private fun hasImmediateWin(state: CharArray, row: Int, col: Int, symbol: Char): Boolean {
        state[row * GameBoard.BOARD_DIMENSION + col] = symbol
        val directions = arrayOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for ((dr, dc) in directions) {
            var total = 1
            var r = row + dr
            var c = col + dc
            while (r in 0 until GameBoard.BOARD_DIMENSION &&
                c in 0 until GameBoard.BOARD_DIMENSION &&
                state[r * GameBoard.BOARD_DIMENSION + c] == symbol
            ) {
                total++
                r += dr
                c += dc
            }
            r = row - dr
            c = col - dc
            while (r in 0 until GameBoard.BOARD_DIMENSION &&
                c in 0 until GameBoard.BOARD_DIMENSION &&
                state[r * GameBoard.BOARD_DIMENSION + c] == symbol
            ) {
                total++
                r -= dr
                c -= dc
            }
            if (total >= 5) return true
        }
        return false
    }

    private fun positionScore(board: GameBoard, row: Int, col: Int, symbol: Char): Int {
        var score = 0
        val directions = arrayOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for ((dr, dc) in directions) {
            var own = 0
            var blocked = 0
            for (step in -4..4) {
                val r = row + dr * step
                val c = col + dc * step
                if (r !in 0 until GameBoard.BOARD_DIMENSION ||
                    c !in 0 until GameBoard.BOARD_DIMENSION
                ) {
                    blocked++
                    continue
                }
                val cell = board.getCell(r, c)
                if (cell == symbol) own++ else if (cell != '.') blocked++
            }
            score += own * own * 20 - blocked * 3
        }
        val center = 8
        score += (GameBoard.BOARD_DIMENSION - kotlin.math.abs(row - center) -
            kotlin.math.abs(col - center)) * 2
        return score
    }

    private fun evaluateState(state: CharArray, symbol: Char): Int {
        var total = 0
        val board = GameBoard(boardState = String(state))
        for (r in 0 until GameBoard.BOARD_DIMENSION) {
            for (c in 0 until GameBoard.BOARD_DIMENSION) {
                if (board.getCell(r, c) == '.') {
                    total += positionScore(board, r, c, symbol)
                }
            }
        }
        return total
    }
}
