package com.caro.server

import com.caro.server.controller.CaroOutcomeChecker
import com.caro.server.controller.ChallengeServerController
import com.caro.server.controller.PlayerServerController
import com.caro.server.entity.GameBoard
import com.caro.server.entity.MatchParticipant
import com.caro.server.entity.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HaModulesSmokeTest {

    @Test
    fun testCaroFiveInARowAndOpenFourOutcomeChecker() {
        val pX = MatchParticipant(id = 1, side = "X")
        val pO = MatchParticipant(id = 2, side = "O")

        // 1. 4 quan lien tiep khong bi chan dau nao (.XXXX.) -> WIN
        val openFourBoard = GameBoard()
        val openFourMoves = arrayListOf(
            Move(row = "H", column = 5, moveOrder = 1, matchParticipant = pX),
            Move(row = "H", column = 6, moveOrder = 3, matchParticipant = pX),
            Move(row = "H", column = 7, moveOrder = 5, matchParticipant = pX),
            Move(row = "H", column = 8, moveOrder = 7, matchParticipant = pX)
        )
        openFourBoard.rebuildFromMoves(openFourMoves)
        assertEquals("WIN", CaroOutcomeChecker.checkOutcome(openFourBoard, openFourMoves.last()))

        // 2. 4 quan bi chan 1 dau (OXXXX.) -> ONGOING, dat quan thu 5 (OXXXXX) -> WIN
        val blockedFourBoard = GameBoard()
        val blockedFourMoves = arrayListOf(
            Move(row = "H", column = 4, moveOrder = 1, matchParticipant = pO),
            Move(row = "H", column = 5, moveOrder = 2, matchParticipant = pX),
            Move(row = "H", column = 6, moveOrder = 3, matchParticipant = pX),
            Move(row = "H", column = 7, moveOrder = 4, matchParticipant = pX),
            Move(row = "H", column = 8, moveOrder = 5, matchParticipant = pX)
        )
        blockedFourBoard.rebuildFromMoves(blockedFourMoves)
        assertEquals("ONGOING", CaroOutcomeChecker.checkOutcome(blockedFourBoard, blockedFourMoves.last()))

        val fifthMove = Move(row = "H", column = 9, moveOrder = 6, matchParticipant = pX)
        blockedFourMoves.add(fifthMove)
        blockedFourBoard.rebuildFromMoves(blockedFourMoves)
        assertEquals("WIN", CaroOutcomeChecker.checkOutcome(blockedFourBoard, fifthMove))
    }

    @Test
    fun testLiveNeonDaosAndControllersIfConfigured() {
        val playerCtrl = PlayerServerController()
        val challengeCtrl = ChallengeServerController()

        val loggedIn = playerCtrl.handleCheckLogin("ha88", "123456")
        assertNotNull(loggedIn, "ha88 seed account must authenticate on Neon")
        assertEquals("AVAILABLE", loggedIn.onlineStatus)

        val profile = playerCtrl.handleGetPlayerProfile(loggedIn.id)
        assertNotNull(profile, "PlayerStatisticDAO.getPlayerStatistic must return profile")
        assertEquals("ha88", profile.gameID)
        assertTrue(profile.totalMatches >= 2, "Seeded matches should be counted")
        assertTrue(profile.rank >= 1, "Rank must be >= 1")

        val onlineList = challengeCtrl.handleGetOnlinePlayers()
        assertTrue(onlineList.any { it.gameID == "ha88" }, "ha88 should appear in online players list")

        assertTrue(playerCtrl.handleLogout(loggedIn.id))
    }
}
