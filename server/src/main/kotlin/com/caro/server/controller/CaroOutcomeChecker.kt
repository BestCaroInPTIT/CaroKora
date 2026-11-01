package com.caro.server.controller

import com.caro.server.entity.GameBoard
import com.caro.server.entity.Move

object CaroOutcomeChecker {

    /**
     * Kiem tra ket qua tran dau Caro 17x17 sau nuoc di moi nhat:
     * - "WIN": dat duoc >= 5 quan lien tiep theo ngang, doc, cheo chinh hoac cheo phu
     * - "DRAW": ban co da kin 289 o ma chua ai thang
     * - "ONGOING": tran dau tiep tuc
     */
    fun checkOutcome(board: GameBoard, lastMove: Move): String {
        val r = GameBoard.rowToIndex(lastMove.row)
        val c = lastMove.column - 1
        if (r !in 0 until GameBoard.BOARD_DIMENSION || c !in 0 until GameBoard.BOARD_DIMENSION) {
            return "ONGOING"
        }

        val symbol = board.getCell(r, c)
        if (symbol != 'X' && symbol != 'O') return "ONGOING"

        val directions = arrayOf(
            intArrayOf(0, 1),   // Ngang
            intArrayOf(1, 0),   // Doc
            intArrayOf(1, 1),   // Cheo chinh
            intArrayOf(1, -1)   // Cheo phu
        )

        for (dir in directions) {
            var count = 1
            count += countDirection(board, r, c, dir[0], dir[1], symbol)
            count += countDirection(board, r, c, -dir[0], -dir[1], symbol)
            if (count >= 5) {
                return "WIN"
            }
        }

        if (!board.boardState.contains('.')) {
            return "DRAW"
        }

        return "ONGOING"
    }

    private fun countDirection(
        board: GameBoard,
        startR: Int,
        startC: Int,
        dR: Int,
        dC: Int,
        symbol: Char
    ): Int {
        var count = 0
        var r = startR + dR
        var c = startC + dC
        while (r in 0 until GameBoard.BOARD_DIMENSION && c in 0 until GameBoard.BOARD_DIMENSION) {
            if (board.getCell(r, c) == symbol) {
                count++
                r += dR
                c += dC
            } else {
                break
            }
        }
        return count
    }
}
