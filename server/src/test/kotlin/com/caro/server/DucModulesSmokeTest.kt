package com.caro.server

import com.caro.server.controller.PlayerServerController
import com.caro.server.controller.RankingServerController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DucModulesSmokeTest {

    @Test
    fun testRankingListAndPlayerStatistics() {
        val playerController = PlayerServerController()
        val rankingController = RankingServerController()

        val ranking = rankingController.handleGetRanking()
        assertTrue(ranking.isNotEmpty(), "Ranking should contain players")

        var previousScore = Double.POSITIVE_INFINITY
        var previousWins = Int.MAX_VALUE
        ranking.forEach { player ->
            assertTrue(player.rank >= 1, "Ranking rank must be >= 1")
            assertTrue(
                player.totalScore < previousScore ||
                    (player.totalScore == previousScore && player.wins <= previousWins),
                "Ranking should be ordered by total score descending, then wins descending"
            )
            previousScore = player.totalScore
            previousWins = player.wins
        }

        val loggedIn = playerController.handleCheckLogin("ha88", "123456")
        assertNotNull(loggedIn, "ha88 seed account must authenticate on Neon")

        val statistics = rankingController.handleGetPlayerStatistics(loggedIn.id)
        assertNotNull(statistics, "Player ranking statistics should be returned")
        assertEquals(loggedIn.id, statistics.id)
        assertEquals("ha88", statistics.gameID)
        assertTrue(statistics.rank >= 1, "Player rank must be >= 1")

        val rankedPlayer = ranking.firstOrNull { it.id == loggedIn.id }
        assertNotNull(rankedPlayer, "Logged-in player should appear in ranking")
        assertEquals(rankedPlayer.rank, statistics.rank)
        assertEquals(rankedPlayer.totalScore, statistics.totalScore)
    }
}
