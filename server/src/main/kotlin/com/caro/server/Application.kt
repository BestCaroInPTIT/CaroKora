package com.caro.server

import com.caro.server.controller.ChallengeServerController
import com.caro.server.controller.HumanMatchServerController
import com.caro.server.controller.MatchHistoryServerController
import com.caro.server.controller.PlayerServerController
import com.caro.server.dao.DAO
import com.caro.server.dao.PlayerStatisticDAO
import com.caro.server.entity.Match
import com.caro.server.entity.Move
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger("CaroServer")

private data class LoginRequest(val username: String = "", val password: String = "")
private data class ChangePasswordRequest(val playerId: Int = 0, val oldPass: String = "", val newPass: String = "")
private data class StatusRequest(val playerId: Int = 0, val onlineStatus: String = "AVAILABLE")
private data class SendChallengeRequest(val p1Id: Int = 0, val p2Id: Int = 0)
private data class RespondChallengeRequest(val matchId: Int = 0, val accept: Boolean = false)
private data class FindMatchRequest(val playerId: Int = 0)
private data class StopMatchRequest(val matchId: Int = 0, val playerId: Int = 0)
private data class RematchRequest(val oldMatchId: Int = 0, val playerId: Int = 0, val accept: Boolean = true)
private data class RematchSession(
    val acceptedPlayerIds: MutableSet<Int> = mutableSetOf(),
    var refusedByPlayerId: Int? = null,
    var newMatchId: Int? = null
)
private data class RematchResponse(val status: String, val match: Match? = null)

fun main(args: Array<String>) {
    if (args.contains("--init-db-only")) {
        DAO.initSchemaAndSeed()
        DAO.getConnection().use { conn ->
            for (tbl in listOf("Player", "Match", "MatchParticipant", "Move")) {
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT COUNT(*) FROM $tbl").use { rs ->
                        if (rs.next()) {
                            logger.info("Neon Table [$tbl] row count = ${rs.getInt(1)}")
                        }
                    }
                }
            }
        }
        return
    }

    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    logger.info("Starting Caro Game Server on port $port...")
    embeddedServer(Netty, host = "0.0.0.0", port = port, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    try {
        DAO.initSchemaAndSeed()
    } catch (e: Exception) {
        logger.error("Loi khi khoi tao CSDL Neon: ${e.message}", e)
    }

    install(CallLogging)

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }

    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
            serializeNulls()
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Unhandled server exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Internal server error"))
            )
        }
    }

    val playerController = PlayerServerController()
    val challengeController = ChallengeServerController()
    val humanMatchController = HumanMatchServerController()
    val matchHistoryController = MatchHistoryServerController()
    val playerStatisticDAO = PlayerStatisticDAO()
    val rematchSessions = ConcurrentHashMap<Int, RematchSession>()

    fun enrichEndedMatchStats(match: Match?) {
        if (match == null) return
        if (match.status == "FINISHED" || match.status == "STOPPED") {
            for (mp in match.matchParticipant) {
                val pId = mp.player?.id ?: continue
                playerStatisticDAO.getPlayerStatistic(pId)?.let { mp.player = it }
            }
        }
    }

    routing {
        get("/") {
            call.respond(
                mapOf(
                    "service" to "Caro Game System Server",
                    "status" to "ONLINE",
                    "version" to "v5-match-history"
                )
            )
        }

        get("/health") {
            val dbOk = try {
                DAO.getConnection().use { conn ->
                    conn.createStatement().use { st ->
                        st.executeQuery("SELECT 1").use { it.next() }
                    }
                }
            } catch (_: Exception) {
                false
            }
            call.respond(
                if (dbOk) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                mapOf(
                    "status" to if (dbOk) "OK" else "DB_ERROR",
                    "database" to "Neon PostgreSQL",
                    "version" to "v5-match-history"
                )
            )
        }

        route("/api") {
            // ============================================================
            // CHUC NANG 1: DANG NHAP, HO SO & DOI MAT KHAU (PlayerServerController)
            // ============================================================
            post("/player/login") {
                val req = call.receive<LoginRequest>()
                val player = playerController.handleCheckLogin(req.username, req.password)
                if (player != null) {
                    call.respond(HttpStatusCode.OK, player)
                } else {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid username or password"))
                }
            }

            post("/player/logout") {
                val req = call.receive<StatusRequest>()
                if (req.playerId > 0) {
                    humanMatchController.handleFindAndCreateMatch(-req.playerId)
                    DAO.getConnection().use { conn ->
                        conn.prepareStatement("UPDATE Player SET onlineStatus = 'OFFLINE' WHERE id = ?").use { ps ->
                            ps.setInt(1, req.playerId)
                            ps.executeUpdate()
                        }
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("success" to true))
            }

            post("/player/heartbeat") {
                val req = call.receive<StatusRequest>()
                if (req.playerId > 0) {
                    DAO.getConnection().use { conn ->
                        conn.prepareStatement(
                            "UPDATE Player SET onlineStatus = 'AVAILABLE' WHERE id = ? AND onlineStatus = 'OFFLINE'"
                        ).use { ps ->
                            ps.setInt(1, req.playerId)
                            ps.executeUpdate()
                        }
                    }
                }
                val incomingChallenge = DAO.getIncomingPendingChallenge(req.playerId)
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "alive" to true,
                        "incomingChallenge" to incomingChallenge
                    )
                )
            }

            get("/player/{id}/profile") {
                val id = call.parameters["id"]?.toIntOrNull() ?: 0
                val stat = playerController.handleGetPlayerProfile(id)
                if (stat != null) {
                    call.respond(HttpStatusCode.OK, stat)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Player not found"))
                }
            }

            post("/player/change-password") {
                val req = call.receive<ChangePasswordRequest>()
                val success = playerController.handleChangePassword(req.playerId, req.oldPass, req.newPass)
                call.respond(HttpStatusCode.OK, mapOf("success" to success))
            }

            // ============================================================
            // CHUC NANG 2: THACH DAU & XEM LICH SU NUOC DI (ChallengeServerController)
            // ============================================================
            get("/challenge/online-players") {
                val onlineList = challengeController.handleGetOnlinePlayers()
                call.respond(HttpStatusCode.OK, onlineList)
            }

            post("/challenge/send") {
                val req = call.receive<SendChallengeRequest>()
                val match = challengeController.handleSendChallenge(req.p1Id, req.p2Id)
                if (match != null) {
                    call.respond(HttpStatusCode.OK, match)
                } else {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Player is unavailable or in another match"))
                }
            }

            post("/challenge/respond") {
                val req = call.receive<RespondChallengeRequest>()
                val match = challengeController.handleRespondChallenge(req.matchId, req.accept)
                if (match != null) {
                    call.respond(HttpStatusCode.OK, match)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Challenge match not found"))
                }
            }

            post("/challenge/cancel") {
                val req = call.receive<RespondChallengeRequest>()
                val ok = DAO.cancelPendingChallenge(req.matchId)
                call.respond(HttpStatusCode.OK, mapOf("success" to ok))
            }

            get("/challenge/incoming/{playerId}") {
                val pId = call.parameters["playerId"]?.toIntOrNull() ?: 0
                val match = DAO.getIncomingPendingChallenge(pId)
                if (match != null) {
                    call.respond(HttpStatusCode.OK, match)
                } else {
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            post("/challenge/move") {
                val move = call.receive<Move>()
                val ok = challengeController.handleMakeMove(move)
                if (ok) {
                    val updatedMatch = synchronized(DAO.lockForMatch(move.matchId)) {
                        val m = DAO.getMatchById(move.matchId)
                        enrichEndedMatchStats(m)
                        m
                    }
                    call.respond(HttpStatusCode.OK, mapOf("success" to true, "match" to updatedMatch))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false))
                }
            }

            get("/challenge/match/{matchId}/moves") {
                val matchId = call.parameters["matchId"]?.toIntOrNull() ?: 0
                val moves = challengeController.handleGetMoveHistory(matchId)
                call.respond(HttpStatusCode.OK, moves)
            }

            // ============================================================
            // CHUC NANG 3: CHOI VOI NGUOI (HumanMatchServerController)
            // ============================================================
            post("/human-match/find") {
                val req = call.receive<FindMatchRequest>()
                val match = humanMatchController.handleFindAndCreateMatch(req.playerId)
                if (match != null) {
                    call.respond(HttpStatusCode.OK, match)
                } else {
                    call.respond(HttpStatusCode.Accepted, mapOf("waiting" to true))
                }
            }

            post("/human-match/cancel-find") {
                val req = call.receive<FindMatchRequest>()
                if (req.playerId > 0) {
                    humanMatchController.handleFindAndCreateMatch(-req.playerId)
                }
                call.respond(HttpStatusCode.OK, mapOf("cancelled" to true))
            }

            post("/human-match/move") {
                val move = call.receive<Move>()
                val updatedMatch = humanMatchController.handleMakeMove(move)
                if (updatedMatch != null) {
                    call.respond(HttpStatusCode.OK, updatedMatch)
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid move or wrong turn"))
                }
            }

            post("/human-match/stop") {
                val req = call.receive<StopMatchRequest>()
                val endedMatch = if (req.matchId > 0) {
                    synchronized(DAO.lockForMatch(req.matchId)) {
                        val m = DAO.stopPlaying(req.matchId, req.playerId)
                        enrichEndedMatchStats(m)
                        m
                    }
                } else null
                if (endedMatch != null) {
                    call.respond(HttpStatusCode.OK, endedMatch)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Match not found"))
                }
            }

            post("/challenge/timeout") {
                val req = call.receive<StopMatchRequest>()
                val updatedMatch = if (req.matchId > 0) {
                    synchronized(DAO.lockForMatch(req.matchId)) {
                        val m = DAO.checkAndEnforceTurnTimeout(req.matchId, force = true)
                        enrichEndedMatchStats(m)
                        m
                    }
                } else null
                if (updatedMatch != null) {
                    call.respond(HttpStatusCode.OK, updatedMatch)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Match not found"))
                }
            }

            post("/human-match/timeout") {
                val req = call.receive<StopMatchRequest>()
                val updatedMatch = if (req.matchId > 0) {
                    synchronized(DAO.lockForMatch(req.matchId)) {
                        val m = DAO.checkAndEnforceTurnTimeout(req.matchId, force = true)
                        enrichEndedMatchStats(m)
                        m
                    }
                } else null
                if (updatedMatch != null) {
                    call.respond(HttpStatusCode.OK, updatedMatch)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Match not found"))
                }
            }

            get("/human-match/rematch/{oldMatchId}") {
                val oldMatchId = call.parameters["oldMatchId"]?.toIntOrNull() ?: 0
                val session = rematchSessions[oldMatchId]
                val res = if (session == null) {
                    RematchResponse("PENDING", null)
                } else {
                    synchronized(session) {
                        when {
                            session.refusedByPlayerId != null -> RematchResponse("REFUSED", null)
                            session.newMatchId != null -> RematchResponse("CREATED", DAO.getMatchById(session.newMatchId!!))
                            session.acceptedPlayerIds.isNotEmpty() -> RematchResponse("WAITING", null)
                            else -> RematchResponse("PENDING", null)
                        }
                    }
                }
                call.respond(HttpStatusCode.OK, res)
            }

            post("/human-match/rematch") {
                val req = call.receive<RematchRequest>()
                val oldMatch = DAO.getMatchById(req.oldMatchId)
                val playerIds = oldMatch?.matchParticipant?.mapNotNull { it.player?.id } ?: emptyList()
                val res = if (oldMatch == null || playerIds.size < 2 || req.playerId !in playerIds) {
                    RematchResponse("REFUSED", null)
                } else {
                    val session = rematchSessions.computeIfAbsent(req.oldMatchId) { RematchSession() }
                    synchronized(session) {
                        when {
                            !req.accept -> {
                                session.refusedByPlayerId = req.playerId
                                RematchResponse("REFUSED", null)
                            }
                            session.refusedByPlayerId != null -> RematchResponse("REFUSED", null)
                            session.newMatchId != null -> RematchResponse("CREATED", DAO.getMatchById(session.newMatchId!!))
                            else -> {
                                session.acceptedPlayerIds.add(req.playerId)
                                if (session.acceptedPlayerIds.containsAll(playerIds)) {
                                    val newMatch = DAO.createActiveMatch(playerIds[1], playerIds[0], oldMatch.mode)
                                    if (newMatch != null) {
                                        session.newMatchId = newMatch.id
                                        RematchResponse("CREATED", newMatch)
                                    } else {
                                        RematchResponse("REFUSED", null)
                                    }
                                } else {
                                    RematchResponse("WAITING", null)
                                }
                            }
                        }
                    }
                }
                call.respond(HttpStatusCode.OK, res)
            }

            get("/match/{id}") {
                val matchId = call.parameters["id"]?.toIntOrNull() ?: 0
                val match = if (matchId > 0) {
                    synchronized(DAO.lockForMatch(matchId)) {
                        val m = DAO.checkAndEnforceTurnTimeout(matchId, graceSeconds = 1.0)
                        enrichEndedMatchStats(m)
                        m
                    }
                } else null
                if (match != null) {
                    call.respond(HttpStatusCode.OK, match)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Match not found"))
                }
            }

            // ============================================================
            // CHUC NANG 4: XEM LICH SU DAU CA NHAN (MatchHistoryServerController)
            // ============================================================
            get("/match-history/{playerId}") {
                val playerId = call.parameters["playerId"]?.toIntOrNull() ?: 0
                call.respond(HttpStatusCode.OK, matchHistoryController.handleGetMatchHistory(playerId))
            }

            get("/match-history/{playerId}/{matchId}") {
                val playerId = call.parameters["playerId"]?.toIntOrNull() ?: 0
                val matchId = call.parameters["matchId"]?.toIntOrNull() ?: 0
                val match = matchHistoryController.handleGetMatchDetail(matchId, playerId)
                if (match != null) {
                    call.respond(HttpStatusCode.OK, match)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Match history not found"))
                }
            }

            get("/match-history/{playerId}/{matchId}/moves") {
                val playerId = call.parameters["playerId"]?.toIntOrNull() ?: 0
                val matchId = call.parameters["matchId"]?.toIntOrNull() ?: 0
                call.respond(HttpStatusCode.OK, matchHistoryController.handleGetMoveHistory(playerId, matchId))
            }
        }
    }
}
