package com.caro.server.dao

import com.caro.server.entity.MatchParticipant
import com.caro.server.entity.Move
import com.caro.server.entity.Player
import java.sql.Connection
import java.text.SimpleDateFormat
import java.util.Locale

class MoveDAO : DAO() {

    fun addMove(move: Move): Boolean = withConnection { connection ->
        addMoveInternal(connection, move)
    }

    fun getMoveHistory(matchId: Int): ArrayList<Move> = withConnection { connection ->
        getMoveHistoryInternal(connection, matchId)
    }

    companion object {
        private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

        fun addMoveInternal(connection: Connection, move: Move): Boolean {
            val partId = move.matchParticipant?.id ?: 0
            val matchId = move.matchId
            if (partId <= 0 || matchId <= 0) return false

            // Kiem tra o co da bi danh chua va dam bao moveOrder chinh xac
            val dupCheckSql = """
                SELECT 1 FROM Move
                WHERE Matchid = ? AND UPPER("row") = UPPER(?) AND "column" = ?
            """.trimIndent()
            val alreadyTaken = connection.prepareStatement(dupCheckSql).use { ps ->
                ps.setInt(1, matchId)
                ps.setString(2, move.row.trim())
                ps.setInt(3, move.column)
                ps.executeQuery().use { it.next() }
            }
            if (alreadyTaken) return false

            val nextOrder = connection.prepareStatement(
                "SELECT COALESCE(MAX(moveOrder), 0) + 1 FROM Move WHERE Matchid = ?"
            ).use { ps ->
                ps.setInt(1, matchId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 1 }
            }
            move.moveOrder = nextOrder
            move.row = move.row.trim().uppercase()

            val sql = """
                INSERT INTO Move ("row", "column", moveOrder, createdAt, MatchParticipantid, Matchid)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?)
                RETURNING id, createdAt
            """.trimIndent()

            return connection.prepareStatement(sql).use { ps ->
                ps.setString(1, move.row)
                ps.setInt(2, move.column)
                ps.setInt(3, move.moveOrder)
                ps.setInt(4, partId)
                ps.setInt(5, matchId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        move.id = rs.getInt("id")
                        val ts = rs.getTimestamp("createdAt")
                        move.createdAt = if (ts != null) synchronized(timeFmt) { timeFmt.format(ts) } else null
                        true
                    } else {
                        false
                    }
                }
            }
        }

        fun getMoveHistoryInternal(connection: Connection, matchId: Int): ArrayList<Move> {
            val sql = """
                SELECT
                    mv.id AS mv_id,
                    mv."row" AS mv_row,
                    mv."column" AS mv_col,
                    mv.moveOrder AS mv_order,
                    mv.createdAt AS mv_created,
                    mv.Matchid AS mv_match_id,
                    mp.id AS mp_id,
                    mp.participantType AS mp_type,
                    mp.side AS mp_side,
                    mp.aiDifficulty AS mp_ai,
                    mp.outcome AS mp_outcome,
                    mp.score AS mp_score,
                    p.id AS p_id,
                    p.gameID AS p_game_id,
                    p.fullName AS p_full_name,
                    p.email AS p_email,
                    p.avatar AS p_avatar,
                    p.bio AS p_bio,
                    p.onlineStatus AS p_online
                FROM Move mv
                JOIN MatchParticipant mp ON mp.id = mv.MatchParticipantid
                LEFT JOIN Player p ON p.id = mp.Playerid
                WHERE mv.Matchid = ?
                ORDER BY mv.moveOrder ASC, mv.id ASC
            """.trimIndent()

            val moves = ArrayList<Move>()
            connection.prepareStatement(sql).use { ps ->
                ps.setInt(1, matchId)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val pId = rs.getInt("p_id")
                        val player = if (!rs.wasNull() && pId > 0) {
                            Player(
                                id = pId,
                                gameID = rs.getString("p_game_id") ?: "",
                                password = "",
                                fullName = rs.getString("p_full_name") ?: "",
                                email = rs.getString("p_email") ?: "",
                                avatar = rs.getString("p_avatar"),
                                bio = rs.getString("p_bio"),
                                onlineStatus = rs.getString("p_online") ?: "OFFLINE"
                            )
                        } else null

                        val participant = MatchParticipant(
                            id = rs.getInt("mp_id"),
                            participantType = rs.getString("mp_type") ?: "HUMAN",
                            side = rs.getString("mp_side") ?: "X",
                            aiDifficulty = rs.getString("mp_ai"),
                            outcome = rs.getString("mp_outcome"),
                            score = rs.getDouble("mp_score"),
                            player = player
                        )

                        val ts = rs.getTimestamp("mv_created")
                        moves.add(
                            Move(
                                id = rs.getInt("mv_id"),
                                row = rs.getString("mv_row") ?: "A",
                                column = rs.getInt("mv_col"),
                                moveOrder = rs.getInt("mv_order"),
                                createdAt = if (ts != null) synchronized(timeFmt) { timeFmt.format(ts) } else null,
                                matchParticipant = participant,
                                matchId = rs.getInt("mv_match_id")
                            )
                        )
                    }
                }
            }
            return moves
        }
    }
}
