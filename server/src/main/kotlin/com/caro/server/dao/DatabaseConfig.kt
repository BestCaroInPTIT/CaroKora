package com.caro.server.dao

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.DriverManager

object DatabaseConfig {
    private val logger = LoggerFactory.getLogger(DatabaseConfig::class.java)

    private val localEnv: Map<String, String> by lazy { loadDotEnv() }

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
        // prepareThreshold=0 tuong thich an toan voi Neon PgBouncer (-pooler)
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

    /**
     * Khoi tao bang CSDL tren Neon (uu tien ket noi Direct khong qua -pooler cho DDL migration)
     */
    fun initSchemaAndSeed() {
        val directRaw = getEnvVar("DATABASE_URL") ?: getEnvVar("DATABASE_URL_POOLED")
            ?: throw IllegalStateException("Thieu DATABASE_URL de khoi tao CSDL")
        val parsed = parseDatabaseUrl(directRaw)

        val schemaStream = DatabaseConfig::class.java.classLoader.getResourceAsStream("db/schema.sql")
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

        // Tran mau 1: ha88 thang Bn (5 nuoc cua ha88, 4 nuoc cua Bn)
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

        // Tran mau 2: A hoa ha88 (+0.5 moi nguoi)
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
