package com.caro.client.entity

import java.io.Serializable

class Match(
    var id: Int = 0,
    var mode: String = "HUMAN",
    var status: String = "PLAYING",
    var startedAt: String? = null,
    var endedAt: String? = null,
    var currentTurn: String = "X",
    var turnTimeLimit: Int = 15,
    var matchParticipant: ArrayList<MatchParticipant> = ArrayList(),
    var move: ArrayList<Move> = ArrayList(),
    var gameBoard: GameBoard = GameBoard()
) : Serializable
