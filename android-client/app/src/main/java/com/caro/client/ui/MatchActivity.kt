package com.caro.client.ui

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.caro.client.R
import com.caro.client.controller.ChallengeClientController
import com.caro.client.controller.HumanMatchClientController
import com.caro.client.controller.PlayerClientController
import com.caro.client.entity.GameBoard
import com.caro.client.entity.Match
import com.caro.client.entity.MatchParticipant
import com.caro.client.entity.Move
import com.caro.client.entity.Player
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

class MatchActivity : AppCompatActivity(), View.OnClickListener {

    private data class CellCoord(val rowIdx: Int, val colIdx: Int)

    private lateinit var lblOpponentName: TextView
    private lateinit var lblOpponentRank: TextView
    private lateinit var lblMatchId: TextView
    private lateinit var lblPlayedTime: TextView
    private lateinit var lblTurnTimer: TextView
    private lateinit var lblCurrentTurn: TextView
    private lateinit var imgCurrentTurnAvatar: ImageView
    private lateinit var lblPlayerMoves: TextView
    private lateinit var lblOpponentMoves: TextView
    private lateinit var grdBoard: GridLayout
    private lateinit var lwMoveHistory: ListView
    private lateinit var btnStopPlaying: Button

    private lateinit var player: Player
    private lateinit var match: Match
    private var myParticipant: MatchParticipant? = null
    private var opponentParticipant: MatchParticipant? = null
    private var currentMove: Move? = null
    private var listMove: ArrayList<Move> = ArrayList()

    private val challengeClientController = ChallengeClientController()
    private val humanMatchClientController = HumanMatchClientController()
    private val playerClientController = PlayerClientController()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val boardCells = Array(GameBoard.BOARD_DIMENSION) {
        arrayOfNulls<TextView>(GameBoard.BOARD_DIMENSION)
    }
    private lateinit var moveHistoryAdapter: MoveHistoryAdapter

    private var matchStartEpochMs: Long = System.currentTimeMillis()
    private var turnDeadlineEpochMs: Long = System.currentTimeMillis() + 15_000L
    private var lastObservedMoveCount: Int = -1
    private var lastObservedTurn: String = ""

    @Volatile
    private var isSubmittingMove: Boolean = false
    private var hasShownResultDialog: Boolean = false
    private var resultDialog: MatchResultDialog? = null

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateTimersOnTick()
            mainHandler.postDelayed(this, 1000L)
        }
    }

    private val syncMatchRunnable = object : Runnable {
        override fun run() {
            if (!hasShownResultDialog) {
                pollMatchStateFromServer()
                mainHandler.postDelayed(this, 1200L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.match)

        val resolvedPlayer = (intent.getSerializableExtra(EXTRA_PLAYER) as? Player)
            ?: SessionManager.getPlayer(this)
        val resolvedMatch = intent.getSerializableExtra(EXTRA_MATCH) as? Match
        if (resolvedPlayer == null || resolvedMatch == null) {
            finish()
            return
        }
        player = resolvedPlayer
        match = resolvedMatch
        listMove = ArrayList(match.move)

        bindViews()
        resolveParticipants()
        buildCaroBoardGrid()

        moveHistoryAdapter = MoveHistoryAdapter()
        lwMoveHistory.adapter = moveHistoryAdapter

        matchStartEpochMs = System.currentTimeMillis()
        resetTurnTimer()
        renderMatchState()
        fetchOpponentRankAsync()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (match.status.equals("PLAYING", ignoreCase = true)) {
                    promptStopPlayingConfirmation()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        mainHandler.removeCallbacks(clockRunnable)
        mainHandler.removeCallbacks(syncMatchRunnable)
        mainHandler.post(clockRunnable)
        mainHandler.post(syncMatchRunnable)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(clockRunnable)
        mainHandler.removeCallbacks(syncMatchRunnable)
    }

    private fun bindViews() {
        lblOpponentName = findViewById(R.id.lblOpponentName)
        lblOpponentRank = findViewById(R.id.lblOpponentRank)
        lblMatchId = findViewById(R.id.lblMatchId)
        lblPlayedTime = findViewById(R.id.lblPlayedTime)
        lblTurnTimer = findViewById(R.id.lblTurnTimer)
        lblCurrentTurn = findViewById(R.id.lblCurrentTurn)
        imgCurrentTurnAvatar = findViewById(R.id.imgCurrentTurnAvatar)
        lblPlayerMoves = findViewById(R.id.lblPlayerMoves)
        lblOpponentMoves = findViewById(R.id.lblOpponentMoves)
        grdBoard = findViewById(R.id.grdBoard)
        lwMoveHistory = findViewById(R.id.lwMoveHistory)
        btnStopPlaying = findViewById(R.id.btnStopPlaying)

        btnStopPlaying.setOnClickListener(this)
    }

    private fun resolveParticipants() {
        myParticipant = match.matchParticipant.firstOrNull { it.player?.id == player.id }
        opponentParticipant = match.matchParticipant.firstOrNull { it.player?.id != player.id }
    }

    private fun buildCaroBoardGrid() {
        grdBoard.removeAllViews()
        val dm = resources.displayMetrics
        val horizontalBudget = dm.widthPixels - dpToPx(28)
        val verticalBudget = (dm.heightPixels * 0.49f).toInt()
        val gridDim = GameBoard.BOARD_DIMENSION + 1 // 18x18 (1 hang/cot nhan + 17x17 o co)
        val cellPx = maxOf(dpToPx(15), min(horizontalBudget, verticalBudget) / gridDim)

        val inkColor = ContextCompat.getColor(this, R.color.kora_ink)
        val headerBgColor = ContextCompat.getColor(this, R.color.kora_card)
        val mutedColor = ContextCompat.getColor(this, R.color.kora_muted)
        val surfaceColor = ContextCompat.getColor(this, R.color.kora_surface)

        for (r in 0..GameBoard.BOARD_DIMENSION) {
            for (c in 0..GameBoard.BOARD_DIMENSION) {
                val tv = TextView(this)
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(r),
                    GridLayout.spec(c)
                ).apply {
                    width = cellPx - 1
                    height = cellPx - 1
                    setMargins(0, 0, 1, 1)
                }
                tv.layoutParams = params
                tv.gravity = Gravity.CENTER
                tv.typeface = Typeface.MONOSPACE

                when {
                    r == 0 && c == 0 -> {
                        tv.text = ""
                        tv.setBackgroundColor(headerBgColor)
                    }
                    r == 0 -> {
                        tv.text = c.toString()
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                        tv.setTextColor(mutedColor)
                        tv.setBackgroundColor(headerBgColor)
                    }
                    c == 0 -> {
                        tv.text = GameBoard.indexToRow(r - 1).lowercase(Locale.US)
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
                        tv.setTextColor(mutedColor)
                        tv.setBackgroundColor(headerBgColor)
                    }
                    else -> {
                        val rowIdx = r - 1
                        val colIdx = c - 1
                        tv.text = ""
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                        tv.setTextColor(inkColor)
                        tv.setBackgroundColor(surfaceColor)
                        tv.tag = CellCoord(rowIdx, colIdx)
                        tv.setOnClickListener(this)
                        boardCells[rowIdx][colIdx] = tv
                    }
                }
                grdBoard.addView(tv)
            }
        }
    }

    private fun fetchOpponentRankAsync() {
        val oppId = opponentParticipant?.player?.id ?: return
        if (oppId <= 0) return
        ioExecutor.execute {
            val oppStat = try {
                playerClientController.getPlayerProfile(oppId)
            } catch (_: Exception) {
                null
            }
            if (oppStat != null) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        val rStr = if (oppStat.rank > 0) "RANK #${oppStat.rank}" else "RANK #-"
                        lblOpponentRank.text = rStr
                    }
                }
            }
        }
    }

    override fun onClick(v: View?) {
        if (v == null) return
        if (v.id == R.id.btnStopPlaying) {
            promptStopPlayingConfirmation()
            return
        }
        val coord = v.tag as? CellCoord ?: return
        handleBoardCellClick(coord.rowIdx, coord.colIdx)
    }

    private fun handleBoardCellClick(rowIdx: Int, colIdx: Int) {
        if (isSubmittingMove || hasShownResultDialog) return
        if (!match.status.equals("PLAYING", ignoreCase = true)) return

        val me = myParticipant ?: return
        if (!me.side.equals(match.currentTurn, ignoreCase = true)) {
            return
        }
        if (match.gameBoard.getCell(rowIdx, colIdx) != '.') {
            return
        }

        // Buoc 59-60 trong bieu do tuan tu: dong goi nuoc di vao thuc the Move
        val newMove = Move(
            row = GameBoard.indexToRow(rowIdx),
            column = colIdx + 1,
            moveOrder = listMove.size + 1,
            matchParticipant = me,
            matchId = match.id
        )
        currentMove = newMove
        isSubmittingMove = true

        ioExecutor.execute {
            var updatedMatch: Match? = null
            var latestMoves: ArrayList<Move>? = null

            try {
                if (match.mode.equals("CHALLENGE", ignoreCase = true)) {
                    // Dung theo bieu do tuan tu Chuc nang 2: makeMove() -> getMoveHistory()
                    updatedMatch = challengeClientController.makeMoveAndGetMatch(newMove)
                    if (updatedMatch != null) {
                        latestMoves = challengeClientController.getMoveHistory(match.id)
                    }
                } else {
                    // Dung theo bieu do tuan tu Chuc nang 3: HumanMatchClientController.makeMove()
                    updatedMatch = humanMatchClientController.makeMove(newMove)
                }
            } catch (_: Exception) {
            }

            runOnUiThread {
                isSubmittingMove = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (updatedMatch != null) {
                    if (latestMoves != null && latestMoves.isNotEmpty()) {
                        updatedMatch.move = latestMoves
                    }
                    applyServerMatchUpdate(updatedMatch)
                }
            }
        }
    }

    private fun pollMatchStateFromServer() {
        val currentMatchId = match.id
        if (currentMatchId <= 0 || isSubmittingMove) return

        ioExecutor.execute {
            val polled = try {
                if (match.mode.equals("CHALLENGE", ignoreCase = true)) {
                    challengeClientController.getMatchState(currentMatchId)
                } else {
                    humanMatchClientController.getMatchState(currentMatchId)
                }
            } catch (_: Exception) {
                null
            }
            if (polled != null) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        applyServerMatchUpdate(polled)
                    }
                }
            }
        }
    }

    private fun applyServerMatchUpdate(updated: Match) {
        val prevMoveCount = lastObservedMoveCount
        val prevTurn = lastObservedTurn

        match = updated
        resolveParticipants()
        listMove = ArrayList(updated.move)

        if (listMove.size != prevMoveCount || !updated.currentTurn.equals(prevTurn, ignoreCase = true)) {
            resetTurnTimer()
        }

        renderMatchState()

        if ((updated.status.equals("FINISHED", ignoreCase = true) ||
                updated.status.equals("STOPPED", ignoreCase = true)) &&
            !hasShownResultDialog
        ) {
            showMatchResultPopup(updated)
        }
    }

    private fun renderMatchState() {
        lastObservedMoveCount = listMove.size
        lastObservedTurn = match.currentTurn

        val oppName = opponentParticipant?.player?.gameID ?: "OPPONENT"
        val oppSide = opponentParticipant?.side ?: "O"
        val mySide = myParticipant?.side ?: "X"

        findViewById<TextView>(R.id.lblMatchScreenTitle)?.text =
            if (match.mode.equals("CHALLENGE", ignoreCase = true)) "MATCH BOARD" else "HUMAN MATCH"

        lblOpponentName.text = "OPPONENT: ${oppName.uppercase(Locale.US)}"
        lblMatchId.text = "MATCH ID: ${match.id}"

        val isMyTurn = mySide.equals(match.currentTurn, ignoreCase = true)
        if (match.status.equals("PLAYING", ignoreCase = true)) {
            lblCurrentTurn.text = if (isMyTurn) {
                "YOUR TURN  ·  $mySide"
            } else {
                "${oppName.uppercase(Locale.US)}'S TURN  ·  $oppSide"
            }
            lblCurrentTurn.setTextColor(
                ContextCompat.getColor(this, if (isMyTurn) R.color.kora_ink else R.color.kora_muted)
            )
        } else {
            lblCurrentTurn.text = "MATCH ${match.status}"
            lblCurrentTurn.setTextColor(ContextCompat.getColor(this, R.color.kora_muted))
        }

        val myMoveCount = listMove.count {
            it.matchParticipant?.id == myParticipant?.id ||
                it.matchParticipant?.side.equals(mySide, ignoreCase = true)
        }
        val oppMoveCount = listMove.size - myMoveCount
        lblPlayerMoves.text = "Player ${player.gameID}’s move: $myMoveCount"
        lblOpponentMoves.text = "Player ${oppName}’s move: $oppMoveCount"

        val lastMove = listMove.lastOrNull()
        val lastRowIdx = if (lastMove != null) GameBoard.rowToIndex(lastMove.row) else -1
        val lastColIdx = if (lastMove != null) lastMove.column - 1 else -1

        val surfaceColor = ContextCompat.getColor(this, R.color.kora_surface)
        val lastMoveBg = ContextCompat.getColor(this, R.color.kora_blush)
        val colorX = ContextCompat.getColor(this, R.color.kora_x)
        val colorO = ContextCompat.getColor(this, R.color.kora_o)

        for (r in 0 until GameBoard.BOARD_DIMENSION) {
            for (c in 0 until GameBoard.BOARD_DIMENSION) {
                val cellTv = boardCells[r][c] ?: continue
                val symbol = match.gameBoard.getCell(r, c)
                when (symbol) {
                    'X', 'x' -> {
                        cellTv.text = "X"
                        cellTv.setTextColor(colorX)
                    }
                    'O', 'o' -> {
                        cellTv.text = "O"
                        cellTv.setTextColor(colorO)
                    }
                    else -> {
                        cellTv.text = ""
                    }
                }
                cellTv.setBackgroundColor(
                    if (r == lastRowIdx && c == lastColIdx) lastMoveBg else surfaceColor
                )
            }
        }

        moveHistoryAdapter.notifyDataSetChanged()
        if (listMove.isNotEmpty()) {
            lwMoveHistory.post {
                lwMoveHistory.setSelection(listMove.size - 1)
            }
        }
    }

    private fun resetTurnTimer() {
        val limitSec = if (match.turnTimeLimit > 0) match.turnTimeLimit else 15
        turnDeadlineEpochMs = System.currentTimeMillis() + limitSec * 1000L
        lblTurnTimer.text = String.format(Locale.US, "%02d:%02d", limitSec / 60, limitSec % 60)
    }

    private fun updateTimersOnTick() {
        if (!match.status.equals("PLAYING", ignoreCase = true)) return

        val elapsedSec = ((System.currentTimeMillis() - matchStartEpochMs) / 1000L).coerceAtLeast(0L)
        lblPlayedTime.text = String.format(Locale.US, "%02d:%02d", elapsedSec / 60, elapsedSec % 60)

        val remainingSec = ((turnDeadlineEpochMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
        lblTurnTimer.text = String.format(Locale.US, "%02d:%02d", remainingSec / 60, remainingSec % 60)

        // Neu het 15s ma van den luot minh, tu dong danh o trong gan tam ban co nhat de khong treo luot
        val mySide = myParticipant?.side ?: ""
        if (remainingSec == 0L && mySide.equals(match.currentTurn, ignoreCase = true) && !isSubmittingMove) {
            findNearestEmptyCellToCenter()?.let { (r, c) ->
                resetTurnTimer()
                handleBoardCellClick(r, c)
            }
        }
    }

    private fun findNearestEmptyCellToCenter(): Pair<Int, Int>? {
        val center = GameBoard.BOARD_DIMENSION / 2
        var best: Pair<Int, Int>? = null
        var bestDist = Int.MAX_VALUE
        for (r in 0 until GameBoard.BOARD_DIMENSION) {
            for (c in 0 until GameBoard.BOARD_DIMENSION) {
                if (match.gameBoard.getCell(r, c) == '.') {
                    val dist = abs(r - center) + abs(c - center)
                    if (dist < bestDist) {
                        bestDist = dist
                        best = r to c
                    }
                }
            }
        }
        return best
    }

    private fun promptStopPlayingConfirmation() {
        if (!match.status.equals("PLAYING", ignoreCase = true)) {
            finish()
            return
        }

        KoraNoticeDialog.show(
            context = this,
            title = "STOP PLAYING?",
            message = "Do you want to stop playing?\nYou will lose 1 point.",
            tone = KoraNoticeDialog.Tone.ERROR,
            primaryText = "YES, STOP",
            onPrimary = { executeStopPlaying() },
            secondaryText = "NO, CONTINUE"
        )
    }

    private fun executeStopPlaying() {
        btnStopPlaying.isEnabled = false
        ioExecutor.execute {
            val stoppedMatch = try {
                humanMatchClientController.stopPlaying(match.id, player.id)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                btnStopPlaying.isEnabled = true
                if (!isFinishing && !isDestroyed && stoppedMatch != null) {
                    applyServerMatchUpdate(stoppedMatch)
                }
            }
        }
    }

    private fun showMatchResultPopup(endedMatch: Match) {
        hasShownResultDialog = true
        mainHandler.removeCallbacks(syncMatchRunnable)

        ioExecutor.execute {
            val latestProfile = try {
                playerClientController.getPlayerProfile(player.id)
            } catch (_: Exception) {
                null
            }
            val totalScore = latestProfile?.totalScore ?: 0.0
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    resultDialog = MatchResultDialog(this, player, endedMatch, totalScore).also {
                        it.show()
                    }
                }
            }
        }
    }

    private inner class MoveHistoryAdapter : BaseAdapter() {
        override fun getCount(): Int = listMove.size
        override fun getItem(position: Int): Move = listMove[position]
        override fun getItemId(position: Int): Long = listMove[position].moveOrder.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val rowView = convertView ?: LayoutInflater.from(this@MatchActivity)
                .inflate(R.layout.item_move_history, parent, false)

            val mv = getItem(position)
            rowView.setBackgroundColor(
                ContextCompat.getColor(
                    this@MatchActivity,
                    if (position % 2 == 1) R.color.kora_zebra else R.color.kora_surface
                )
            )
            val lblOrder = rowView.findViewById<TextView>(R.id.lblMoveOrder)
            val lblTime = rowView.findViewById<TextView>(R.id.lblMoveTime)
            val lblTurn = rowView.findViewById<TextView>(R.id.lblMoveTurn)
            val lblCoord = rowView.findViewById<TextView>(R.id.lblMoveCoord)

            lblOrder.text = mv.moveOrder.toString()
            lblTime.text = extractShortTime(mv.createdAt)

            val side = mv.matchParticipant?.side ?: if (mv.moveOrder % 2 == 1) "X" else "O"
            val pName = mv.matchParticipant?.player?.gameID?.takeIf { it.isNotBlank() }
                ?: match.matchParticipant.firstOrNull { it.side.equals(side, ignoreCase = true) }?.player?.gameID
                ?: side
            lblTurn.text = pName
            lblTurn.setTextColor(ContextCompat.getColor(this@MatchActivity, R.color.kora_ink))
            lblCoord.text = "${mv.row.uppercase(Locale.US)}${mv.column}"
            return rowView
        }

        private fun extractShortTime(raw: String?): String {
            if (raw.isNullOrBlank()) return "--:--"
            // raw co dang "yyyy-MM-dd HH:mm:ss" hoac "HH:mm:ss"
            val parts = raw.trim().split(" ")
            val timePart = if (parts.size >= 2) parts[1] else parts[0]
            return if (timePart.length >= 8) timePart.substring(0, 8) else timePart
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(clockRunnable)
        mainHandler.removeCallbacks(syncMatchRunnable)
        resultDialog?.dismiss()
        ioExecutor.shutdownNow()
    }

    companion object {
        const val EXTRA_PLAYER = "extra_player"
        const val EXTRA_MATCH = "extra_match"
    }
}
