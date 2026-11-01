package com.caro.server.dao

import com.caro.server.entity.GameBoard
import com.caro.server.entity.Match
import com.caro.server.entity.MatchParticipant
import com.caro.server.entity.Player
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.DriverManager
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

open class DAO {
    protected var con: Connection? = null

    protected fun <T> withConnection(block: (Connection) -> T): T {
        getConnection().use { connection ->
            this.con = connection
            try {
                return block(connection)
            } finally {
                this.con = null
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DAO::class.java)
        private val dtFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        private val matchLocks = ConcurrentHashMap<Int, Any>()
        private val localEnv: Map<String, String> by lazy { loadDotEnv() }

        fun lockForMatch(matchId: Int): Any = matchLocks.computeIfAbsent(matchId) { Any() }

        private fun getEnvVar(key: String): String? {
            return System.getenv(key)?.takeIf { it.isNotBlank() }
                ?: localEnv[key]?.takeIf { it.isNotBlank() }
        }

        data class ParsedDbConfig(
            val jdbcUrl: String,
            val username: String?,
            val password: String?
        )

        fun parseDatabaseUrl(rawUrl: String): ParsedDbConfig {
            val cleaned = rawUrl.trim().removeSurrounding("\"").removeSurrounding("'")
            if (cleaned.startsWith("jdbc:postgresql://")) {
                return ParsedDbConfig(cleaned, null, null)
            }
            val normalized = when {
                cleaned.startsWith("postgres://") -> "postgresql://" + cleaned.removePrefix("postgres://")
                cleaned.startsWith("postgresql://") -> cleaned
                else -> throw IllegalArgumentException("Unsupported DATABASE_URL format")
            }
            val uri = URI(normalized)
            val userInfo = uri.rawUserInfo ?: ""
            val user = URLDecoder.decode(userInfo.substringBefore(":", ""), StandardCharsets.UTF_8)
            val pass = URLDecoder.decode(userInfo.substringAfter(":", ""), StandardCharsets.UTF_8)
            val host = uri.host
            val port = if (uri.port > 0) uri.port else 5432
            val dbName = uri.path.removePrefix("/")
            val sslMode = if (host == "localhost" || host == "127.0.0.1") "disable" else "require"
            val jdbc = "jdbc:postgresql://$host:$port/$dbName?sslmode=$sslMode&prepareThreshold=0"
            return ParsedDbConfig(
                jdbcUrl = jdbc,
                username = user.ifEmpty { null },
                password = pass.ifEmpty { null }
            )
        }

        private val dataSource: HikariDataSource by lazy {
            val rawUrl = getEnvVar("DATABASE_URL_POOLED")
                ?: getEnvVar("DATABASE_URL")
                ?: throw IllegalStateException("Thieu bien moi truong DATABASE_URL! Hay cau hinh tren Render hoac file .env")

            val parsed = parseDatabaseUrl(rawUrl)
            val config = HikariConfig().apply {
                jdbcUrl = parsed.jdbcUrl
                if (parsed.username != null) username = parsed.username
                if (parsed.password != null) password = parsed.password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 8
                minimumIdle = 1
                connectionTimeout = 20_000
                idleTimeout = 120_000
                maxLifetime = 300_000
                isAutoCommit = true
            }
            logger.info("Connecting HikariCP pool to: ${parsed.jdbcUrl}")
            HikariDataSource(config)
        }

        fun getConnection(): Connection = dataSource.connection

        fun initSchemaAndSeed() {
            val directRaw = getEnvVar("DATABASE_URL") ?: getEnvVar("DATABASE_URL_POOLED")
                ?: throw IllegalStateException("Thieu DATABASE_URL de khoi tao CSDL")
            val parsed = parseDatabaseUrl(directRaw)

            val schemaStream = DAO::class.java.classLoader.getResourceAsStream("db/schema.sql")
                ?: throw IllegalStateException("Khong tim thay db/schema.sql trong resources")
            val schemaSql = schemaStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            val conn = if (parsed.username != null) {
                DriverManager.getConnection(parsed.jdbcUrl, parsed.username, parsed.password)
            } else {
                DriverManager.getConnection(parsed.jdbcUrl)
            }

            conn.use { c ->
                c.createStatement().use { stmt ->
                    stmt.execute(schemaSql)
                }
                seedSampleMatchesIfEmpty(c)
            }
            logger.info("Khoi tao schema CSDL va du lieu mau tren Neon thanh cong!")
        }

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

            match.move = MoveDAO.getMoveHistoryInternal(connection, matchId)
            match.gameBoard.rebuildFromMoves(match.move)
            return match
        }

        fun insertHumanParticipants(connection: Connection, matchId: Int, p1Id: Int, p2Id: Int) {
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

        fun releasePlayersToAvailable(connection: Connection, matchId: Int) {
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

        fun createActiveMatch(p1Id: Int, p2Id: Int, mode: String = "HUMAN"): Match? = getConnection().use { connection ->
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
            if (matchId <= 0) return@use null

            insertHumanParticipants(connection, matchId, p1Id, p2Id)

            connection.prepareStatement("UPDATE Player SET onlineStatus = 'IN_MATCH' WHERE id IN (?, ?)").use { ps ->
                ps.setInt(1, p1Id)
                ps.setInt(2, p2Id)
                ps.executeUpdate()
            }

            loadMatchInternal(connection, matchId)
        }

        fun getMatchById(matchId: Int): Match? = getConnection().use { connection ->
            loadMatchInternal(connection, matchId)
        }

        /**
         * Xu thua ngay lap tuc khi het 15 giay luot di ma nguoi den luot chua danh.
         */
        fun checkAndEnforceTurnTimeout(matchId: Int, graceSeconds: Double = 0.0): Match? = getConnection().use { connection ->
            val timing = connection.prepareStatement(
                """
                SELECT
                    m.status,
                    m.currentTurn,
                    COALESCE(m.turnTimeLimit, 15) AS turn_limit,
                    EXTRACT(EPOCH FROM (
                        CURRENT_TIMESTAMP - COALESCE(
                            (SELECT MAX(mv.createdAt) FROM Move mv WHERE mv.Matchid = m.id),
                            m.startedAt
                        )
                    )) AS elapsed_sec
                FROM Match m
                WHERE m.id = ?
                """.trimIndent()
            ).use { ps ->
                ps.setInt(1, matchId)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) null
                    else Triple(
                        rs.getString("status") ?: "",
                        rs.getString("currentTurn") ?: "X",
                        rs.getDouble("elapsed_sec") to rs.getInt("turn_limit")
                    )
                }
            } ?: return@use null

            val status = timing.first
            val currentTurn = timing.second
            val (elapsedSec, turnLimit) = timing.third

            val currentMatch = loadMatchInternal(connection, matchId) ?: return@use null
            if (status != "PLAYING" || elapsedSec < (turnLimit + graceSeconds)) {
                return@use currentMatch
            }

            val winnerPart = currentMatch.matchParticipant.firstOrNull {
                !it.side.equals(currentTurn, ignoreCase = true)
            } ?: return@use currentMatch

            for (participant in currentMatch.matchParticipant) {
                if (participant.id == winnerPart.id) {
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

        fun stopPlaying(matchId: Int, stopperPlayerId: Int): Match? = getConnection().use { connection ->
            val match = loadMatchInternal(connection, matchId) ?: return@use null
            if (match.status != "PLAYING") return@use match

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

        fun getIncomingPendingChallenge(playerId: Int): Match? = getConnection().use { connection ->
            connection.createStatement().use { st ->
                st.executeUpdate(
                    """
                    UPDATE Match SET status = 'EXPIRED', endedAt = CURRENT_TIMESTAMP
                    WHERE mode = 'CHALLENGE' AND status = 'PENDING'
                      AND startedAt < CURRENT_TIMESTAMP - INTERVAL '125 seconds'
                    """.trimIndent()
                )
            }

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

        fun cancelPendingChallenge(matchId: Int): Boolean = getConnection().use { connection ->
            connection.prepareStatement(
                "UPDATE Match SET status = 'CANCELLED', endedAt = CURRENT_TIMESTAMP WHERE id = ? AND status = 'PENDING'"
            ).use { ps ->
                ps.setInt(1, matchId)
                ps.executeUpdate() > 0
            }
        }

        private fun seedSampleMatchesIfEmpty(c: Connection) {
            val matchCount = c.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM Match").use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
            if (matchCount > 0) return

            val playerIds = mutableMapOf<String, Int>()
            c.createStatement().use { st ->
                st.executeQuery("SELECT id, gameID FROM Player").use { rs ->
                    while (rs.next()) {
                        playerIds[rs.getString("gameID")] = rs.getInt("id")
                    }
                }
            }
            val haId = playerIds["ha88"] ?: return
            val bnId = playerIds["Bn"] ?: return
            val aId = playerIds["A"] ?: return

            val m1Id = c.prepareStatement(
                "INSERT INTO Match (mode, status, startedAt, endedAt, currentTurn, turnTimeLimit, boardSize) " +
                    "VALUES ('HUMAN', 'FINISHED', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '110 minutes', 'O', 15, '17x17') RETURNING id"
            ).use { ps -> ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) } }

            val m1P1 = c.prepareStatement(
                "INSERT INTO MatchParticipant (participantType, side, outcome, score, Playerid, Matchid) VALUES ('HUMAN', 'X', 'WIN', 1.0, ?, ?) RETURNING id"
            ).use { ps -> ps.setInt(1, haId); ps.setInt(2, m1Id); ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) } }

            val m1P2 = c.prepareStatement(
                "INSERT INTO MatchParticipant (participantType, side, outcome, score, Playerid, Matchid) VALUES ('HUMAN', 'O', 'LOSS', 0.0, ?, ?) RETURNING id"
            ).use { ps -> ps.setInt(1, bnId); ps.setInt(2, m1Id); ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) } }

            c.prepareStatement(
                "INSERT INTO Move (\"row\", \"column\", moveOrder, MatchParticipantid, Matchid) VALUES (?, ?, ?, ?, ?)"
            ).use { ps ->
                for (i in 0 until 5) {
                    ps.setString(1, "H")
                    ps.setInt(2, 5 + i)
                    ps.setInt(3, i * 2 + 1)
                    ps.setInt(4, m1P1)
                    ps.setInt(5, m1Id)
                    ps.addBatch()
                    if (i < 4) {
                        ps.setString(1, "I")
                        ps.setInt(2, 5 + i)
                        ps.setInt(3, i * 2 + 2)
                        ps.setInt(4, m1P2)
                        ps.setInt(5, m1Id)
                        ps.addBatch()
                    }
                }
                ps.executeBatch()
            }

            val m2Id = c.prepareStatement(
                "INSERT INTO Match (mode, status, startedAt, endedAt, currentTurn, turnTimeLimit, boardSize) " +
                    "VALUES ('CHALLENGE', 'FINISHED', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP - INTERVAL '50 minutes', 'X', 15, '17x17') RETURNING id"
            ).use { ps -> ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) } }

            c.prepareStatement(
                "INSERT INTO MatchParticipant (participantType, side, outcome, score, Playerid, Matchid) VALUES ('HUMAN', 'X', 'DRAW', 0.5, ?, ?)"
            ).use { ps -> ps.setInt(1, aId); ps.setInt(2, m2Id); ps.executeUpdate() }

            c.prepareStatement(
                "INSERT INTO MatchParticipant (participantType, side, outcome, score, Playerid, Matchid) VALUES ('HUMAN', 'O', 'DRAW', 0.5, ?, ?)"
            ).use { ps -> ps.setInt(1, haId); ps.setInt(2, m2Id); ps.executeUpdate() }
        }

        private fun loadDotEnv(): Map<String, String> {
            val candidates = listOf(File(".env"), File("../.env"))
            val envFile = candidates.firstOrNull { it.exists() && it.isFile } ?: return emptyMap()
            val result = mutableMapOf<String, String>()
            envFile.readLines(StandardCharsets.UTF_8).forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) return@forEach
                val key = line.substringBefore("=").trim()
                val value = line.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                result[key] = value
            }
            return result
        }
    }
}
