package com.caro.client.entity

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

    companion object {
        const val BOARD_DIMENSION = 17

        fun rowToIndex(row: String): Int {
            val trimmed = row.trim()
            if (trimmed.isEmpty()) return -1
            val asInt = trimmed.toIntOrNull()
            if (asInt != null) {
                return if (asInt in 1..BOARD_DIMENSION) asInt - 1 else asInt
            }
            return trimmed[0].uppercaseChar() - 'A'
        }

        fun indexToRow(index: Int): String = ('A' + index).toString()
    }
}
