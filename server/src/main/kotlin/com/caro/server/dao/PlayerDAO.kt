package com.caro.server.dao

import com.caro.server.entity.Player
import java.sql.ResultSet

class PlayerDAO : DAO() {

    fun checkLogin(username: String, password: String): Player? = withConnection { connection ->
        val sql = """
            SELECT id, gameID, password, fullName, email, avatar, bio, onlineStatus
            FROM Player
            WHERE (LOWER(gameID) = LOWER(?) OR LOWER(email) = LOWER(?))
              AND password = ?
            LIMIT 1
        """.trimIndent()

        val player = connection.prepareStatement(sql).use { ps ->
            ps.setString(1, username.trim())
            ps.setString(2, username.trim())
            ps.setString(3, password)
            ps.executeQuery().use { rs ->
                if (rs.next()) mapPlayer(rs) else null
            }
        }

        if (player != null) {
            // Neu khong dang trong tran dau thi chuyen trang thai truc tuyen thanh AVAILABLE
            val newStatus = if (player.onlineStatus == "IN_MATCH") "IN_MATCH" else "AVAILABLE"
            connection.prepareStatement("UPDATE Player SET onlineStatus = ? WHERE id = ?").use { ps ->
                ps.setString(1, newStatus)
                ps.setInt(2, player.id)
                ps.executeUpdate()
            }
            player.onlineStatus = newStatus
        }
        player
    }

    fun changePassword(playerId: Int, oldPass: String, newPass: String): Boolean = withConnection { connection ->
        val sql = "UPDATE Player SET password = ? WHERE id = ? AND password = ?"
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, newPass)
            ps.setInt(2, playerId)
            ps.setString(3, oldPass)
            ps.executeUpdate() > 0
        }
    }

    fun registerPlayer(
        gameID: String,
        password: String,
        fullName: String,
        email: String,
        avatar: String? = null,
        bio: String? = null
    ): Player? = withConnection { connection ->
        val exists = connection.prepareStatement("SELECT 1 FROM Player WHERE LOWER(gameID) = LOWER(?)").use { ps ->
            ps.setString(1, gameID.trim())
            ps.executeQuery().use { it.next() }
        }
        if (exists) return@withConnection null

        val sql = """
            INSERT INTO Player (gameID, password, fullName, email, avatar, bio, onlineStatus)
            VALUES (?, ?, ?, ?, ?, ?, 'AVAILABLE')
            RETURNING id, gameID, password, fullName, email, avatar, bio, onlineStatus
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, gameID.trim())
            ps.setString(2, password)
            ps.setString(3, fullName.ifBlank { gameID.trim() })
            ps.setString(4, email.ifBlank { "${gameID.trim()}@ptit.edu.vn" })
            ps.setString(5, avatar)
            ps.setString(6, bio ?: "Caro Player")
            ps.executeQuery().use { rs ->
                if (rs.next()) mapPlayer(rs) else null
            }
        }
    }

    fun updateOnlineStatus(playerId: Int, onlineStatus: String): Boolean = withConnection { connection ->
        connection.prepareStatement("UPDATE Player SET onlineStatus = ? WHERE id = ?").use { ps ->
            ps.setString(1, onlineStatus)
            ps.setInt(2, playerId)
            ps.executeUpdate() > 0
        }
    }

    fun updateBioAndAvatar(playerId: Int, bio: String?, avatar: String?): Boolean = withConnection { connection ->
        connection.prepareStatement(
            "UPDATE Player SET bio = COALESCE(?, bio), avatar = COALESCE(?, avatar) WHERE id = ?"
        ).use { ps ->
            ps.setString(1, bio)
            ps.setString(2, avatar)
            ps.setInt(3, playerId)
            ps.executeUpdate() > 0
        }
    }

    fun getPlayerById(playerId: Int): Player? = withConnection { connection ->
        connection.prepareStatement(
            "SELECT id, gameID, password, fullName, email, avatar, bio, onlineStatus FROM Player WHERE id = ?"
        ).use { ps ->
            ps.setInt(1, playerId)
            ps.executeQuery().use { rs ->
                if (rs.next()) mapPlayer(rs) else null
            }
        }
    }

    companion object {
        fun mapPlayer(rs: ResultSet): Player {
            return Player(
                id = rs.getInt("id"),
                gameID = rs.getString("gameID") ?: "",
                password = rs.getString("password") ?: "",
                fullName = rs.getString("fullName") ?: "",
                email = rs.getString("email") ?: "",
                avatar = rs.getString("avatar"),
                bio = rs.getString("bio"),
                onlineStatus = rs.getString("onlineStatus") ?: "OFFLINE"
            )
        }
    }
}
