package com.caro.server.entity

import java.io.Serializable

class MatchParticipant(
    var id: Int = 0,
    var participantType: String = "HUMAN",
    var side: String = "X",
    var aiDifficulty: String? = null,
    @set:JvmName("_assignOutcome")
    var outcome: String? = null,
    @set:JvmName("_assignScore")
    var score: Double = 0.0,
    var player: Player? = null
) : Serializable {

    fun setOutcome(outcome: String?) {
        this.outcome = outcome
    }

    fun setScore(score: Double) {
        this.score = score
    }
}
