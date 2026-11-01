package com.caro.client.entity

class PlayerStatistic(
    id: Int = 0,
    gameID: String = "",
    password: String = "",
    fullName: String = "",
    email: String = "",
    avatar: String? = null,
    bio: String? = null,
    onlineStatus: String = "OFFLINE",
    var totalScore: Double = 0.0,
    var totalMatches: Int = 0,
    var wins: Int = 0,
    var draws: Int = 0,
    var losses: Int = 0,
    var averageOpponentScore: Double = 0.0,
    var averageMovesInWin: Double = 0.0,
    var averageMovesInLoss: Double = 0.0,
    var rank: Int = 0
) : Player(
    id = id,
    gameID = gameID,
    password = password,
    fullName = fullName,
    email = email,
    avatar = avatar,
    bio = bio,
    onlineStatus = onlineStatus
)
