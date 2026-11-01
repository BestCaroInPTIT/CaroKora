package com.caro.server.routes

import com.caro.server.controller.ChallengeServerController
import com.caro.server.controller.HumanMatchServerController
import com.caro.server.controller.PlayerServerController
import com.caro.server.dao.PlayerDAO
import com.caro.server.entity.Move
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class LoginRequest(val username: String = "", val password: String = "")
data class RegisterRequest(
    val gameID: String = "",
    val password: String = "",
    val fullName: String = "",
    val email: String = "",
    val avatar: String? = null
)
data class ChangePasswordRequest(val playerId: Int = 0, val oldPass: String = "", val newPass: String = "")
data class UpdateProfileRequest(val playerId: Int = 0, val bio: String? = null, val avatar: String? = null)
data class StatusRequest(val playerId: Int = 0, val onlineStatus: String = "AVAILABLE")
data class SendChallengeRequest(val p1Id: Int = 0, val p2Id: Int = 0)
data class RespondChallengeRequest(val matchId: Int = 0, val accept: Boolean = false)
data class FindMatchRequest(val playerId: Int = 0)
data class StopMatchRequest(val matchId: Int = 0, val playerId: Int = 0)
data class RematchRequest(val oldMatchId: Int = 0, val playerId: Int = 0, val accept: Boolean = true)

fun Route.configureHaRoutes(
    playerController: PlayerServerController = PlayerServerController(),
    challengeController: ChallengeServerController = ChallengeServerController(),
    humanMatchController: HumanMatchServerController = HumanMatchServerController()
) {
    val playerDAO = PlayerDAO()

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

        post("/player/register") {
            val req = call.receive<RegisterRequest>()
            val player = playerController.handleRegister(req.gameID, req.password, req.fullName, req.email, req.avatar)
            if (player != null) {
                call.respond(HttpStatusCode.Created, player)
            } else {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "GameID already exists or invalid data"))
            }
        }

        post("/player/logout") {
            val req = call.receive<StatusRequest>()
            humanMatchController.handleCancelFindMatch(req.playerId)
            val ok = playerController.handleLogout(req.playerId)
            call.respond(HttpStatusCode.OK, mapOf("success" to ok))
        }

        post("/player/heartbeat") {
            val req = call.receive<StatusRequest>()
            if (req.playerId > 0) {
                val p = playerDAO.getPlayerById(req.playerId)
                if (p != null && p.onlineStatus == "OFFLINE") {
                    playerDAO.updateOnlineStatus(req.playerId, "AVAILABLE")
                }
            }
            val incomingChallenge = challengeController.handleGetIncomingChallenge(req.playerId)
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

        post("/player/update-profile") {
            val req = call.receive<UpdateProfileRequest>()
            val success = playerController.handleUpdateProfile(req.playerId, req.bio, req.avatar)
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
            val ok = challengeController.handleCancelChallenge(req.matchId)
            call.respond(HttpStatusCode.OK, mapOf("success" to ok))
        }

        get("/challenge/incoming/{playerId}") {
            val pId = call.parameters["playerId"]?.toIntOrNull() ?: 0
            val match = challengeController.handleGetIncomingChallenge(pId)
            if (match != null) {
                call.respond(HttpStatusCode.OK, match)
            } else {
                call.respond(HttpStatusCode.NoContent)
            }
        }

        post("/challenge/move") {
            val move = call.receive<Move>()
            val updatedMatch = challengeController.processChallengeMove(move)
            if (updatedMatch != null) {
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
            humanMatchController.handleCancelFindMatch(req.playerId)
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
            val endedMatch = humanMatchController.handleStopPlaying(req.matchId, req.playerId)
            if (endedMatch != null) {
                call.respond(HttpStatusCode.OK, endedMatch)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Match not found"))
            }
        }

        post("/human-match/rematch") {
            val req = call.receive<RematchRequest>()
            val res = humanMatchController.handleQuickRematch(req.oldMatchId, req.playerId, req.accept)
            call.respond(HttpStatusCode.OK, res)
        }

        // Endpoint dong bo trang thai tran dau chung cho ca Challenge & Human Match
        get("/match/{id}") {
            val matchId = call.parameters["id"]?.toIntOrNull() ?: 0
            val match = humanMatchController.handleGetMatchState(matchId)
            if (match != null) {
                call.respond(HttpStatusCode.OK, match)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Match not found"))
            }
        }
    }
}
