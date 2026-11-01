package com.caro.server.controller

import com.caro.server.entity.GameBoard
import com.caro.server.entity.Move

object CaroOutcomeChecker {

    /**
     * Kiem tra ket qua tran dau Caro 17x17 sau nuoc di moi nhat (theo dung muc 1.4 bao cao):
     * - "WIN":
     *    + Dat duoc >= 5 quan lien tiep theo ngang, doc, cheo chinh hoac cheo phu; HOAC
     *    + Dat duoc 4 quan lien tiep khong bi chan dau nao (.XXXX. - ca hai dau deu con o trong '.')
     * - "DRAW": ban co da kin het 289 o trong ma chua ben nao thang
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
            val dR = dir[0]
            val dC = dir[1]
            val forward = countDirection(board, r, c, dR, dC, symbol)
            val backward = countDirection(board, r, c, -dR, -dC, symbol)
            val total = 1 + forward + backward

            if (total >= 5) {
                return "WIN"
            }

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

                if (afterOpen && beforeOpen) {
                    return "WIN"
                }
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
