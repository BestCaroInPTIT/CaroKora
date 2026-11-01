package com.caro.server.dao

import com.caro.server.entity.GameBoard
import com.caro.server.entity.Match
import com.caro.server.entity.MatchParticipant
import com.caro.server.entity.Player
import java.sql.Connection
import java.text.SimpleDateFormat
import java.util.Locale

class MatchDAO : DAO() {

    fun createChallengeMatch(p1Id: Int, p2Id: Int): Match? = withConnection { connection ->
        // Kiem tra doi thu co dang AVAILABLE khong
        val p2Status = connection.prepareStatement("SELECT onlineStatus FROM Player WHERE id = ?").use { ps ->
            ps.setInt(1, p2Id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
        if (p2Status != "AVAILABLE") return@withConnection null

        // Huy cac loi moi PENDING cu cua nguoi gui
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
            // Dam bao ca 2 van chua vao tran khac
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

        // Khoi tao Match, GameBoard, MatchParticipant day du theo bieu do tuan tu
        loadMatchInternal(connection, matchId)
    }

    fun createHumanMatch(p1Id: Int, p2Id: Int): Match? = createActiveMatch(p1Id, p2Id, "HUMAN")

    fun createActiveMatch(p1Id: Int, p2Id: Int, mode: String = "HUMAN"): Match? = withConnection { connection ->
        val normalizedMode = if (mode.equals("CHALLENGE", ignoreCase = true)) "CHALLENGE" else "HUMAN"
        val matchId = connection.prepareStatement(
            """
            INSERT INTO Match (mode, status, startedAt, currentTurn, turnTimeLimit, boardSize)
            VALUES (?, 'PLAYING', CURRENT_TIMESTAMP, 'X', 15, '17x17')
            RETURNING id
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, normalizedMode)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
        if (matchId <= 0) return@withConnection null

        insertHumanParticipants(connection, matchId, p1Id, p2Id)

        connection.prepareStatement("UPDATE Player SET onlineStatus = 'IN_MATCH' WHERE id IN (?, ?)").use { ps ->
            ps.setInt(1, p1Id)
            ps.setInt(2, p2Id)
            ps.executeUpdate()
        }

        loadMatchInternal(connection, matchId)
    }

    /**
     * Chi cap nhat currentTurn trong bang Match (boardState la thuoc tinh dan xuat cua GameBoard trong RAM)
     */
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

    // Ho tro ca 2 cach viet hoa/thuong trong bieu do tuan tu
    fun endMatchAndDeterMineOutcome(matchId: Int, winnerPartId: Int, isDraw: Boolean): Match? =
        endMatchAndDetermineOutcome(matchId, winnerPartId, isDraw)

    /**
     * Truong hop nguoi choi nhan nut Stop Playing giua tran:
     * Nguoi dung choi bi tru 1.0 diem (LOSS, -1.0), doi thu duoc +1.0 diem (WIN, +1.0)
     */
    fun stopPlaying(matchId: Int, stopperPlayerId: Int): Match? = withConnection { connection ->
        val match = loadMatchInternal(connection, matchId) ?: return@withConnection null
        if (match.status != "PLAYING") return@withConnection match

        for (participant in match.matchParticipant) {
            if (participant.player?.id == stopperPlayerId) {
                participant.setOutcome("LOSS")
                participant.setScore(-1.0)
            } else {
                participant.setOutcome("WIN")
                participant.setScore(1.0)
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
            "UPDATE Match SET status = 'STOPPED', endedAt = CURRENT_TIMESTAMP WHERE id = ?"
        ).use { ps ->
            ps.setInt(1, matchId)
            ps.executeUpdate()
        }

        releasePlayersToAvailable(connection, matchId)
        loadMatchInternal(connection, matchId)
    }

    fun cancelPendingChallenge(matchId: Int): Boolean = withConnection { connection ->
        connection.prepareStatement(
            "UPDATE Match SET status = 'CANCELLED', endedAt = CURRENT_TIMESTAMP WHERE id = ? AND status = 'PENDING'"
        ).use { ps ->
            ps.setInt(1, matchId)
            ps.executeUpdate() > 0
        }
    }

    fun getMatchById(matchId: Int): Match? = withConnection { connection ->
        loadMatchInternal(connection, matchId)
    }

    /**
     * Lay loi moi thach dau PENDING gui toi playerId (trong vong 2 phut gan nhat)
     */
    fun getIncomingPendingChallenge(playerId: Int): Match? = withConnection { connection ->
        // Tu dong het han cac loi moi qua 2 phut
        connection.createStatement().use { st ->
            st.executeUpdate(
                """
                UPDATE Match SET status = 'EXPIRED', endedAt = CURRENT_TIMESTAMP
                WHERE mode = 'CHALLENGE' AND status = 'PENDING'
                  AND startedAt < CURRENT_TIMESTAMP - INTERVAL '125 seconds'
                """.trimIndent()
            )
        }

        // Nguoi duoc thach dau la participant co side = 'O'
        val matchId = connection.prepareStatement(
            """
            SELECT m.id
            FROM Match m
            JOIN MatchParticipant mp ON mp.Matchid = m.id
            WHERE m.mode = 'CHALLENGE'
              AND m.status = 'PENDING'
              AND mp.Playerid = ?
              AND mp.side = 'O'
            ORDER BY m.id DESC
            LIMIT 1
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, playerId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }

        if (matchId > 0) loadMatchInternal(connection, matchId) else null
    }

    private fun insertHumanParticipants(connection: Connection, matchId: Int, p1Id: Int, p2Id: Int) {
        val sql = """
            INSERT INTO MatchParticipant (participantType, side, Playerid, Matchid)
            VALUES ('HUMAN', ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, "X")
            ps.setInt(2, p1Id)
            ps.setInt(3, matchId)
            ps.addBatch()

            ps.setString(1, "O")
            ps.setInt(2, p2Id)
            ps.setInt(3, matchId)
            ps.addBatch()

            ps.executeBatch()
        }
    }

    private fun releasePlayersToAvailable(connection: Connection, matchId: Int) {
        connection.prepareStatement(
            """
            UPDATE Player SET onlineStatus = 'AVAILABLE'
            WHERE onlineStatus = 'IN_MATCH'
              AND id IN (SELECT Playerid FROM MatchParticipant WHERE Matchid = ? AND Playerid IS NOT NULL)
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, matchId)
            ps.executeUpdate()
        }
    }

    companion object {
        private val dtFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        fun loadMatchInternal(connection: Connection, matchId: Int): Match? {
            val match = connection.prepareStatement(
                """
                SELECT id, mode, status, startedAt, endedAt, currentTurn, turnTimeLimit, boardSize
                FROM Match
                WHERE id = ?
                """.trimIndent()
            ).use { ps ->
                ps.setInt(1, matchId)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    val sTs = rs.getTimestamp("startedAt")
                    val eTs = rs.getTimestamp("endedAt")
                    val bSize = rs.getString("boardSize") ?: "17x17"
                    Match(
                        id = rs.getInt("id"),
                        mode = rs.getString("mode") ?: "HUMAN",
                        status = rs.getString("status") ?: "PLAYING",
                        startedAt = if (sTs != null) synchronized(dtFmt) { dtFmt.format(sTs) } else null,
                        endedAt = if (eTs != null) synchronized(dtFmt) { dtFmt.format(eTs) } else null,
                        currentTurn = rs.getString("currentTurn") ?: "X",
                        turnTimeLimit = rs.getInt("turnTimeLimit"),
                        gameBoard = GameBoard(id = matchId, boardSize = bSize)
                    )
                }
            }

            // Load MatchParticipant[]
            val partSql = """
                SELECT
                    mp.id AS mp_id,
                    mp.participantType,
                    mp.side,
                    mp.aiDifficulty,
                    mp.outcome,
                    mp.score,
                    p.id AS p_id,
                    p.gameID,
                    p.fullName,
                    p.email,
                    p.avatar,
                    p.bio,
                    p.onlineStatus
                FROM MatchParticipant mp
                LEFT JOIN Player p ON p.id = mp.Playerid
                WHERE mp.Matchid = ?
                ORDER BY mp.id ASC
            """.trimIndent()

            connection.prepareStatement(partSql).use { ps ->
                ps.setInt(1, matchId)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val pId = rs.getInt("p_id")
                        val player = if (!rs.wasNull() && pId > 0) {
                            Player(
                                id = pId,
                                gameID = rs.getString("gameID") ?: "",
                                password = "",
                                fullName = rs.getString("fullName") ?: "",
                                email = rs.getString("email") ?: "",
                                avatar = rs.getString("avatar"),
                                bio = rs.getString("bio"),
                                onlineStatus = rs.getString("onlineStatus") ?: "OFFLINE"
                            )
                        } else null

                        val scoreVal = rs.getDouble("score")
                        match.matchParticipant.add(
                            MatchParticipant(
                                id = rs.getInt("mp_id"),
                                participantType = rs.getString("participantType") ?: "HUMAN",
                                side = rs.getString("side") ?: "X",
                                aiDifficulty = rs.getString("aiDifficulty"),
                                outcome = rs.getString("outcome"),
                                score = if (rs.wasNull()) 0.0 else scoreVal,
                                player = player
                            )
                        )
                    }
                }
            }

            // Load Move[] va tai tao GameBoard.boardState trong RAM
            match.move = MoveDAO.getMoveHistoryInternal(connection, matchId)
            match.gameBoard.rebuildFromMoves(match.move)
            return match
        }
    }
}
