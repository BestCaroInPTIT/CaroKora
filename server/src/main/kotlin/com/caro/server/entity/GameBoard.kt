package com.caro.server.entity

import java.io.Serializable

class GameBoard(
    var id: Int = 0,
    var boardSize: String = "17x17",
    var boardState: String = ".".repeat(BOARD_DIMENSION * BOARD_DIMENSION)
) : Serializable {

    fun getCell(rowIdx: Int, colIdx: Int): Char {
        if (rowIdx !in 0 until BOARD_DIMENSION || colIdx !in 0 until BOARD_DIMENSION) return '.'
        val idx = rowIdx * BOARD_DIMENSION + colIdx
        return if (idx in boardState.indices) boardState[idx] else '.'
    }

    fun setCell(rowIdx: Int, colIdx: Int, symbol: Char) {
        if (rowIdx !in 0 until BOARD_DIMENSION || colIdx !in 0 until BOARD_DIMENSION) return
        val chars = if (boardState.length == BOARD_DIMENSION * BOARD_DIMENSION) {
            boardState.toCharArray()
        } else {
            CharArray(BOARD_DIMENSION * BOARD_DIMENSION) { '.' }
        }
        chars[rowIdx * BOARD_DIMENSION + colIdx] = symbol
        boardState = String(chars)
    }

    fun rebuildFromMoves(moves: List<Move>) {
        val chars = CharArray(BOARD_DIMENSION * BOARD_DIMENSION) { '.' }
        for (mv in moves) {
            val r = rowToIndex(mv.row)
            val c = mv.column - 1
            val sideChar = mv.matchParticipant?.side?.firstOrNull()?.uppercaseChar()
                ?: if (mv.moveOrder % 2 == 1) 'X' else 'O'
            if (r in 0 until BOARD_DIMENSION && c in 0 until BOARD_DIMENSION) {
                chars[r * BOARD_DIMENSION + c] = sideChar
            }
        }
        boardState = String(chars)
    }

    companion object {
        const val BOARD_DIMENSION = 17

        fun rowToIndex(row: String): Int {
            val trimmed = row.trim()
            if (trimmed.isEmpty()) return -1
            val asInt = trimmed.toIntOrNull()
            if (asInt != null) {
                return if (asInt in 1..BOARD_DIMENSION) asInt - 1 else asInt
            }
            val ch = trimmed[0].uppercaseChar()
            return ch - 'A'
        }

        fun indexToRow(index: Int): String {
            return ('A' + index).toString()
        }
    }
}
