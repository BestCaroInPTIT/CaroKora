package com.caro.server.entity

import java.io.Serializable

open class Player(
    var id: Int = 0,
    var gameID: String = "",
    @set:JvmName("_assignPassword")
    var password: String = "",
    var fullName: String = "",
    var email: String = "",
    var avatar: String? = null,
    var bio: String? = null,
    var onlineStatus: String = "OFFLINE"
) : Serializable {

    fun setPassword(newPassword: String) {
        this.password = newPassword
    }
}
