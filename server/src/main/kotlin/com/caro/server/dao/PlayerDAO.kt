package com.caro.server.dao

import com.caro.server.entity.Player

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
                if (rs.next()) {
                    Player(
                        id = rs.getInt("id"),
                        gameID = rs.getString("gameID") ?: "",
                        password = rs.getString("password") ?: "",
                        fullName = rs.getString("fullName") ?: "",
                        email = rs.getString("email") ?: "",
                        avatar = rs.getString("avatar"),
                        bio = rs.getString("bio"),
                        onlineStatus = rs.getString("onlineStatus") ?: "OFFLINE"
                    )
                } else null
            }
        }

        if (player != null) {
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
}
