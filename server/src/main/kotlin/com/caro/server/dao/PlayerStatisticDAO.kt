package com.caro.server.dao

import com.caro.server.entity.PlayerStatistic
import java.sql.Connection
import kotlin.math.round

class PlayerStatisticDAO : DAO() {

    fun getPlayerStatistic(playerId: Int): PlayerStatistic? = withConnection { connection ->
        queryPlayerStatistics(connection, filterPlayerId = playerId, onlyOnline = false).firstOrNull()
    }

    fun getPlayerStatistics(playerId: Int): PlayerStatistic? = getPlayerStatistic(playerId)

    fun getOnlinePlayers(): ArrayList<PlayerStatistic> = withConnection { connection ->
        queryPlayerStatistics(connection, filterPlayerId = null, onlyOnline = true)
    }

    private fun queryPlayerStatistics(
        connection: Connection,
        filterPlayerId: Int?,
        onlyOnline: Boolean
    ): ArrayList<PlayerStatistic> {
        val whereClause = when {
            filterPlayerId != null -> "WHERE ranked.id = ?"
            onlyOnline -> "WHERE ranked.onlineStatus IN ('AVAILABLE', 'IN_MATCH')"
            else -> ""
        }

        val sql = """
            WITH player_scores AS (
                SELECT
                    p.id,
                    p.gameID,
                    p.password,
                    p.fullName,
                    p.email,
                    p.avatar,
                    p.bio,
                    p.onlineStatus,
                    GREATEST(0.0, COALESCE(SUM(CASE WHEN m.status IN ('FINISHED', 'STOPPED') THEN mp.score ELSE 0 END), 0.0)) AS total_score,
                    COUNT(CASE WHEN m.status IN ('FINISHED', 'STOPPED') AND mp.outcome IS NOT NULL THEN 1 END) AS total_matches,
                    COUNT(CASE WHEN m.status IN ('FINISHED', 'STOPPED') AND mp.outcome = 'WIN' THEN 1 END) AS wins,
                    COUNT(CASE WHEN m.status IN ('FINISHED', 'STOPPED') AND mp.outcome = 'DRAW' THEN 1 END) AS draws,
                    COUNT(CASE WHEN m.status IN ('FINISHED', 'STOPPED') AND mp.outcome = 'LOSS' THEN 1 END) AS losses
                FROM Player p
                LEFT JOIN MatchParticipant mp ON mp.Playerid = p.id
                LEFT JOIN Match m ON m.id = mp.Matchid
                GROUP BY p.id, p.gameID, p.password, p.fullName, p.email, p.avatar, p.bio, p.onlineStatus
            ),
            opp_avg AS (
                SELECT
                    mp1.Playerid AS player_id,
                    AVG(ps_opp.total_score) AS avg_opp_score
                FROM MatchParticipant mp1
                JOIN Match m ON m.id = mp1.Matchid AND m.status IN ('FINISHED', 'STOPPED')
                JOIN MatchParticipant mp2 ON mp2.Matchid = mp1.Matchid AND mp2.id <> mp1.id
                JOIN player_scores ps_opp ON ps_opp.id = mp2.Playerid
                WHERE mp1.Playerid IS NOT NULL
                GROUP BY mp1.Playerid
            ),
            part_moves AS (
                SELECT
                    mp.Playerid AS player_id,
                    mp.outcome,
                    COUNT(mv.id) AS move_cnt
                FROM MatchParticipant mp
                JOIN Match m ON m.id = mp.Matchid AND m.status IN ('FINISHED', 'STOPPED')
                LEFT JOIN Move mv ON mv.Matchid = m.id AND mv.MatchParticipantid = mp.id
                WHERE mp.Playerid IS NOT NULL AND mp.outcome IN ('WIN', 'LOSS')
                GROUP BY mp.id, mp.Playerid, mp.outcome
            ),
            move_avg AS (
                SELECT
                    player_id,
                    AVG(CASE WHEN outcome = 'WIN' THEN move_cnt END) AS avg_moves_win,
                    AVG(CASE WHEN outcome = 'LOSS' THEN move_cnt END) AS avg_moves_loss
                FROM part_moves
                GROUP BY player_id
            ),
            ranked AS (
                SELECT
                    ps.*,
                    COALESCE(oa.avg_opp_score, 0.0) AS avg_opp_score,
                    COALESCE(ma.avg_moves_win, 0.0) AS avg_moves_win,
                    COALESCE(ma.avg_moves_loss, 0.0) AS avg_moves_loss,
                    RANK() OVER (ORDER BY ps.total_score DESC, ps.wins DESC, ps.id ASC) AS rnk
                FROM player_scores ps
                LEFT JOIN opp_avg oa ON oa.player_id = ps.id
                LEFT JOIN move_avg ma ON ma.player_id = ps.id
            )
            SELECT *
            FROM ranked
            $whereClause
            ORDER BY
                CASE WHEN ranked.onlineStatus = 'AVAILABLE' THEN 0 ELSE 1 END,
                ranked.rnk ASC,
                ranked.id ASC
        """.trimIndent()

        val list = ArrayList<PlayerStatistic>()
        connection.prepareStatement(sql).use { ps ->
            if (filterPlayerId != null) {
                ps.setInt(1, filterPlayerId)
            }
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    list.add(
                        PlayerStatistic(
                            id = rs.getInt("id"),
                            gameID = rs.getString("gameID") ?: "",
                            password = rs.getString("password") ?: "",
                            fullName = rs.getString("fullName") ?: "",
                            email = rs.getString("email") ?: "",
                            avatar = rs.getString("avatar"),
                            bio = rs.getString("bio"),
                            onlineStatus = rs.getString("onlineStatus") ?: "OFFLINE",
                            totalScore = round1(rs.getDouble("total_score")),
                            totalMatches = rs.getInt("total_matches"),
                            wins = rs.getInt("wins"),
                            draws = rs.getInt("draws"),
                            losses = rs.getInt("losses"),
                            averageOpponentScore = round1(rs.getDouble("avg_opp_score")),
                            averageMovesInWin = round1(rs.getDouble("avg_moves_win")),
                            averageMovesInLoss = round1(rs.getDouble("avg_moves_loss")),
                            rank = rs.getInt("rnk")
                        )
                    )
                }
            }
        }
        return list
    }

    private fun round1(value: Double): Double = round(value * 10.0) / 10.0
}
