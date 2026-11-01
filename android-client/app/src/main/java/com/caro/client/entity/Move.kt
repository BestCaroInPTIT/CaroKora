package com.caro.client.entity

import java.io.Serializable

class Move(
    var id: Int = 0,
    var row: String = "A",
    var column: Int = 1,
    var moveOrder: Int = 1,
    var createdAt: String? = null,
    var matchParticipant: MatchParticipant? = null,
    var matchId: Int = 0
) : Serializable
