package com.caro.server.dao

import com.caro.server.entity.Match

class MatchDAO : DAO() {

    fun createAIMatch(playerId: Int, difficulty: String): Match? = withConnection { connection ->
        val normalizedDifficulty = when (difficulty.lowercase()) {
            "beginner", "intermediate", "expert" -> difficulty.lowercase()
            else -> return@withConnection null
        }
        if (playerId <= 0) return@withConnection null

        val playerExists = connection.prepareStatement(
            "SELECT 1 FROM Player WHERE id = ?"
        ).use { ps ->
            ps.setInt(1, playerId)
            ps.executeQuery().use { rs -> rs.next() }
        }
        if (!playerExists) return@withConnection null

        val matchId = connection.prepareStatement(
            """
            INSERT INTO Match (mode, status, startedAt, currentTurn, turnTimeLimit, boardSize)
            VALUES ('AI', 'PLAYING', CURRENT_TIMESTAMP, 'X', 15, '17x17')
            RETURNING id
            """.trimIndent()
        ).use { ps ->
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
        if (matchId <= 0) return@withConnection null

        connection.prepareStatement(
            """
            INSERT INTO MatchParticipant (participantType, side, aiDifficulty, Playerid, Matchid)
            VALUES
                ('HUMAN', 'X', NULL, ?, ?),
                ('AI', 'O', ?, NULL, ?)
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, playerId)
            ps.setInt(2, matchId)
            ps.setString(3, normalizedDifficulty)
            ps.setInt(4, matchId)
            ps.executeUpdate()
        }

        loadMatchInternal(connection, matchId)
    }

    fun getMatchHistory(playerId: Int): ArrayList<Match> = withConnection { connection ->
        val matchIds = connection.prepareStatement(
            """
            SELECT m.id
            FROM Match m
            WHERE EXISTS (
                SELECT 1
                FROM MatchParticipant mp
                WHERE mp.Matchid = m.id AND mp.Playerid = ?
            )
              AND m.status IN ('FINISHED', 'STOPPED')
            ORDER BY COALESCE(m.endedAt, m.startedAt) DESC, m.id DESC
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, playerId)
            ps.executeQuery().use { rs ->
                val ids = ArrayList<Int>()
                while (rs.next()) ids.add(rs.getInt("id"))
                ids
            }
        }

        val history = ArrayList<Match>()
        for (matchId in matchIds) {
            loadMatchInternal(connection, matchId)?.let { history.add(it) }
        }
        history
    }

    fun createChallengeMatch(p1Id: Int, p2Id: Int): Match? = withConnection { connection ->
        val p2Status = connection.prepareStatement("SELECT onlineStatus FROM Player WHERE id = ?").use { ps ->
            ps.setInt(1, p2Id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
        if (p2Status != "AVAILABLE") return@withConnection null

        connection.prepareStatement(
            """
            UPDATE Match SET status = 'CANCELLED', endedAt = CURRENT_TIMESTAMP
            WHERE mode = 'CHALLENGE' AND status = 'PENDING'
              AND id IN (SELECT Matchid FROM MatchParticipant WHERE Playerid = ?)
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, p1Id)
            ps.executeUpdate()
        }

        val matchId = connection.prepareStatement(
            """
            INSERT INTO Match (mode, status, startedAt, currentTurn, turnTimeLimit, boardSize)
            VALUES ('CHALLENGE', 'PENDING', CURRENT_TIMESTAMP, 'X', 15, '17x17')
            RETURNING id
            """.trimIndent()
        ).use { ps ->
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
        if (matchId <= 0) return@withConnection null

        insertHumanParticipants(connection, matchId, p1Id, p2Id)
        loadMatchInternal(connection, matchId)
    }

    fun updateChallengeStatus(matchId: Int, accept: Boolean): Match? = withConnection { connection ->
        val currentMatch = loadMatchInternal(connection, matchId) ?: return@withConnection null
        if (currentMatch.status != "PENDING") return@withConnection currentMatch

        if (accept) {
            val busyCount = connection.prepareStatement(
                """
                SELECT COUNT(*) FROM MatchParticipant mp
                JOIN Player p ON p.id = mp.Playerid
                WHERE mp.Matchid = ? AND p.onlineStatus = 'IN_MATCH'
                """.trimIndent()
            ).use { ps ->
                ps.setInt(1, matchId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            if (busyCount > 0) {
                connection.prepareStatement(
                    "UPDATE Match SET status = 'BUSY', endedAt = CURRENT_TIMESTAMP WHERE id = ?"
                ).use { ps -> ps.setInt(1, matchId); ps.executeUpdate() }
                return@withConnection loadMatchInternal(connection, matchId)
            }

            connection.prepareStatement(
                "UPDATE Match SET status = 'PLAYING', startedAt = CURRENT_TIMESTAMP, currentTurn = 'X' WHERE id = ?"
            ).use { ps ->
                ps.setInt(1, matchId)
                ps.executeUpdate()
            }

            connection.prepareStatement(
                "UPDATE Player SET onlineStatus = 'IN_MATCH' WHERE id IN (SELECT Playerid FROM MatchParticipant WHERE Matchid = ?)"
            ).use { ps ->
                ps.setInt(1, matchId)
                ps.executeUpdate()
            }
        } else {
            connection.prepareStatement(
                "UPDATE Match SET status = 'REJECTED', endedAt = CURRENT_TIMESTAMP WHERE id = ?"
            ).use { ps ->
                ps.setInt(1, matchId)
                ps.executeUpdate()
            }
        }

        loadMatchInternal(connection, matchId)
    }

    fun createHumanMatch(p1Id: Int, p2Id: Int): Match? = createActiveMatch(p1Id, p2Id, "HUMAN")

    fun updateBoardAndTurn(match: Match): Boolean = withConnection { connection ->
        connection.prepareStatement("UPDATE Match SET currentTurn = ? WHERE id = ?").use { ps ->
            ps.setString(1, match.currentTurn)
            ps.setInt(2, match.id)
            ps.executeUpdate() > 0
        }
    }

    fun endMatchAndDetermineOutcome(matchId: Int, winnerPartId: Int, isDraw: Boolean): Match? = withConnection { connection ->
        val match = loadMatchInternal(connection, matchId) ?: return@withConnection null
        if (match.status == "FINISHED" || match.status == "STOPPED") {
            return@withConnection match
        }

        for (participant in match.matchParticipant) {
            if (isDraw) {
                participant.setOutcome("DRAW")
                participant.setScore(0.5)
            } else if (participant.id == winnerPartId) {
                participant.setOutcome("WIN")
                participant.setScore(1.0)
            } else {
                participant.setOutcome("LOSS")
                participant.setScore(0.0)
            }

            connection.prepareStatement(
                "UPDATE MatchParticipant SET outcome = ?, score = ? WHERE id = ?"
            ).use { ps ->
                ps.setString(1, participant.outcome)
                ps.setDouble(2, participant.score)
                ps.setInt(3, participant.id)
                ps.executeUpdate()
            }
        }

        connection.prepareStatement(
            "UPDATE Match SET status = 'FINISHED', endedAt = CURRENT_TIMESTAMP WHERE id = ?"
        ).use { ps ->
            ps.setInt(1, matchId)
            ps.executeUpdate()
        }

        releasePlayersToAvailable(connection, matchId)
        loadMatchInternal(connection, matchId)
    }
}
