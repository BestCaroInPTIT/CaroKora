package com.caro.server

import com.caro.server.dao.DatabaseConfig
import com.caro.server.routes.configureHaRoutes
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("CaroServer")

fun main(args: Array<String>) {
    if (args.contains("--init-db-only")) {
        DatabaseConfig.initSchemaAndSeed()
        DatabaseConfig.getConnection().use { conn ->
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
    // Tu dong khoi tao bang CSDL tren Neon PostgreSQL khi server khoi dong
    try {
        DatabaseConfig.initSchemaAndSeed()
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

    routing {
        get("/") {
            call.respond(
                mapOf(
                    "service" to "Caro Game System Server",
                    "status" to "ONLINE"
                )
            )
        }

        get("/health") {
            val dbOk = try {
                DatabaseConfig.getConnection().use { conn ->
                    conn.createStatement().use { st ->
                        st.executeQuery("SELECT 1").use { it.next() }
                    }
                }
            } catch (_: Exception) {
                false
            }
            call.respond(
                if (dbOk) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                mapOf("status" to if (dbOk) "OK" else "DB_ERROR", "database" to "Neon PostgreSQL")
            )
        }

        // Module cua Pham Thi Thien Ha (B23DCCN266): F1 Doi mat khau, F2 Thach dau & Lich su nuoc di, F3 Choi voi nguoi
        configureHaRoutes()

        // Diem ghep code cho thanh vien cung nhom (Nguyen Manh Duc - B23DCKH028):
        // configureDucRoutes()
    }
}
