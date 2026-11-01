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
    fun testCaroFiveInARowOutcomeChecker() {
        val board = GameBoard()
        val pX = MatchParticipant(id = 1, side = "X")
        val moves = arrayListOf(
            Move(row = "H", column = 5, moveOrder = 1, matchParticipant = pX),
            Move(row = "H", column = 6, moveOrder = 3, matchParticipant = pX),
            Move(row = "H", column = 7, moveOrder = 5, matchParticipant = pX),
            Move(row = "H", column = 8, moveOrder = 7, matchParticipant = pX),
            Move(row = "H", column = 9, moveOrder = 9, matchParticipant = pX)
        )
        board.rebuildFromMoves(moves)
        val outcome = CaroOutcomeChecker.checkOutcome(board, moves.last())
        assertEquals("WIN", outcome)
    }

    @Test
    fun testLiveNeonDaosAndControllersIfConfigured() {
        // Kiem thu truc tiep tren Neon PostgreSQL khi co file .env
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

        // Dua trang thai ve OFFLINE gon gang sau khi test
        assertTrue(playerCtrl.handleLogout(loggedIn.id))
    }
}
