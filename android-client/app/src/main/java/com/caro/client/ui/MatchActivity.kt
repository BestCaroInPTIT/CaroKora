package com.caro.client.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class MatchActivity : AppCompatActivity(), View.OnClickListener {

    private data class CellCoord(val rowIdx: Int, val colIdx: Int)

    private class BoardCellDrawable(
        var fillColor: Int,
        private val innerLineColor: Int,
        private val outerBorderColor: Int,
        private val outerStrokePx: Float,
        private val isTopOuter: Boolean,
        private val isLeftOuter: Boolean,
        private val isRightOuter: Boolean,
        private val isBottomOuter: Boolean
    ) : Drawable() {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = innerLineColor
        }
        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = outerStrokePx
            color = outerBorderColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()

            fillPaint.color = fillColor
            canvas.drawRect(0f, 0f, w, h, fillPaint)

            if (!isRightOuter) {
                canvas.drawLine(w - 0.5f, 0f, w - 0.5f, h, innerPaint)
            }
            if (!isBottomOuter) {
                canvas.drawLine(0f, h - 0.5f, w, h - 0.5f, innerPaint)
            }

            val half = outerStrokePx / 2f
            if (isTopOuter) canvas.drawLine(0f, half, w, half, outerPaint)
            if (isLeftOuter) canvas.drawLine(half, 0f, half, h, outerPaint)
            if (isRightOuter) canvas.drawLine(w - half, 0f, w - half, h, outerPaint)
            if (isBottomOuter) canvas.drawLine(0f, h - half, w, h - half, outerPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }

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
    private val boardCellDrawables = Array(GameBoard.BOARD_DIMENSION) {
        arrayOfNulls<BoardCellDrawable>(GameBoard.BOARD_DIMENSION)
    }
    private lateinit var moveHistoryAdapter: MoveHistoryAdapter

    // Trang thai phong to / thu nho 2 ngon tay va keo ban co ngay trong MatchActivity (khong phat sinh lop thua)
    private var boardScale = 1.0f
    private var boardTransX = 0f
    private var boardTransY = 0f
    private var boardTouchSlop = 16
    private var isTouchInBoard = false
    private var isBoardPanningOrZooming = false
    private var hasCancelledChildTap = false
    private var boardActivePointerId = MotionEvent.INVALID_POINTER_ID
    private var boardDownX = 0f
    private var boardDownY = 0f
    private var boardLastX = 0f
    private var boardLastY = 0f
    private val boardWindowLoc = IntArray(2)
    private lateinit var boardScaleDetector: ScaleGestureDetector

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
                mainHandler.postDelayed(this, 1000L)
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
        setupBoardZoomGestureDetector()
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
                    navigateBackToHome()
                }
            }
        })
    }

    private fun setupBoardZoomGestureDetector() {
        boardTouchSlop = ViewConfiguration.get(this).scaledTouchSlop
        boardScaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    isBoardPanningOrZooming = true
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val boardFrame = grdBoard.parent as? View ?: return false
                    val oldScale = boardScale
                    val newScale = (oldScale * detector.scaleFactor).coerceIn(1.0f, 3.0f)
                    if (newScale == oldScale) return true

                    boardFrame.getLocationInWindow(boardWindowLoc)
                    val cx = boardWindowLoc[0] + boardFrame.width / 2f
                    val cy = boardWindowLoc[1] + boardFrame.height / 2f
                    val fx = detector.focusX
                    val fy = detector.focusY
                    val ratio = newScale / oldScale

                    boardTransX = (fx - cx) - ratio * ((fx - cx) - boardTransX)
                    boardTransY = (fy - cy) - ratio * ((fy - cy) - boardTransY)
                    boardScale = newScale

                    applyBoardTransform()
                    return true
                }
            }
        )
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::grdBoard.isInitialized && ::boardScaleDetector.isInitialized) {
            val boardFrame = grdBoard.parent as? View
            if (boardFrame != null && boardFrame.width > 0 && boardFrame.height > 0) {
                boardFrame.getLocationInWindow(boardWindowLoc)
                val left = boardWindowLoc[0].toFloat()
                val top = boardWindowLoc[1].toFloat()
                val right = left + boardFrame.width
                val bottom = top + boardFrame.height

                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isTouchInBoard = ev.x in left..right && ev.y in top..bottom
                        boardActivePointerId = ev.getPointerId(0)
                        boardDownX = ev.x
                        boardDownY = ev.y
                        boardLastX = ev.x
                        boardLastY = ev.y
                        isBoardPanningOrZooming = false
                        hasCancelledChildTap = false
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        for (i in 0 until ev.pointerCount) {
                            if (ev.getX(i) in left..right && ev.getY(i) in top..bottom) {
                                isTouchInBoard = true
                                break
                            }
                        }
                    }
                }

                if (isTouchInBoard) {
                    boardScaleDetector.onTouchEvent(ev)
                    if (ev.pointerCount >= 2 || boardScaleDetector.isInProgress) {
                        isBoardPanningOrZooming = true
                    }

                    when (ev.actionMasked) {
                        MotionEvent.ACTION_MOVE -> {
                            val idx = ev.findPointerIndex(boardActivePointerId)
                            if (idx >= 0) {
                                val x = ev.getX(idx)
                                val y = ev.getY(idx)
                                if (!isBoardPanningOrZooming && boardScale > 1.01f) {
                                    if (hypot((x - boardDownX).toDouble(), (y - boardDownY).toDouble()) > boardTouchSlop) {
                                        isBoardPanningOrZooming = true
                                        boardLastX = x
                                        boardLastY = y
                                    }
                                } else if (isBoardPanningOrZooming && !boardScaleDetector.isInProgress && ev.pointerCount == 1 && boardScale > 1.01f) {
                                    boardTransX += (x - boardLastX)
                                    boardTransY += (y - boardLastY)
                                    applyBoardTransform()
                                    boardLastX = x
                                    boardLastY = y
                                } else {
                                    boardLastX = x
                                    boardLastY = y
                                }
                            }
                        }
                        MotionEvent.ACTION_POINTER_UP -> {
                            val pIdx = ev.actionIndex
                            val pId = ev.getPointerId(pIdx)
                            if (pId == boardActivePointerId) {
                                val nextIdx = if (pIdx == 0) 1 else 0
                                if (nextIdx < ev.pointerCount) {
                                    boardLastX = ev.getX(nextIdx)
                                    boardLastY = ev.getY(nextIdx)
                                    boardActivePointerId = ev.getPointerId(nextIdx)
                                }
                            }
                        }
                    }

                    if (isBoardPanningOrZooming) {
                        if (!hasCancelledChildTap) {
                            hasCancelledChildTap = true
                            val cancelEv = MotionEvent.obtain(ev).apply {
                                action = MotionEvent.ACTION_CANCEL
                            }
                            super.dispatchTouchEvent(cancelEv)
                            cancelEv.recycle()
                        }
                        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                            isTouchInBoard = false
                            isBoardPanningOrZooming = false
                            hasCancelledChildTap = false
                            boardActivePointerId = MotionEvent.INVALID_POINTER_ID
                        }
                        return true
                    }

                    if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                        isTouchInBoard = false
                        boardActivePointerId = MotionEvent.INVALID_POINTER_ID
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun applyBoardTransform() {
        if (boardScale <= 1.005f) {
            boardScale = 1.0f
            boardTransX = 0f
            boardTransY = 0f
        } else {
            val maxDx = max(0f, (grdBoard.width * boardScale - grdBoard.width) / 2f)
            val maxDy = max(0f, (grdBoard.height * boardScale - grdBoard.height) / 2f)
            boardTransX = boardTransX.coerceIn(-maxDx, maxDx)
            boardTransY = boardTransY.coerceIn(-maxDy, maxDy)
        }

        grdBoard.pivotX = grdBoard.width / 2f
        grdBoard.pivotY = grdBoard.height / 2f
        grdBoard.scaleX = boardScale
        grdBoard.scaleY = boardScale
        grdBoard.translationX = boardTransX
        grdBoard.translationY = boardTransY
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
        grdBoard.setBackgroundColor(Color.TRANSPARENT)

        val dm = resources.displayMetrics
        val horizontalBudget = dm.widthPixels - dpToPx(28)
        val verticalBudget = (dm.heightPixels * 0.48f).toInt()
        val gridDim = GameBoard.BOARD_DIMENSION + 1 // 18x18 (1 hang/cot nhan + 17x17 o co)
        val cellPx = maxOf(dpToPx(15), min(horizontalBudget, verticalBudget) / gridDim)

        val inkColor = ContextCompat.getColor(this, R.color.kora_ink)
        val mutedColor = ContextCompat.getColor(this, R.color.kora_muted)
        val surfaceColor = ContextCompat.getColor(this, R.color.kora_surface)
        val innerLineColor = ContextCompat.getColor(this, R.color.kora_border_subtle)
        val outerStrokePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, dm)

        for (r in 0..GameBoard.BOARD_DIMENSION) {
            for (c in 0..GameBoard.BOARD_DIMENSION) {
                val tv = TextView(this)
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(r),
                    GridLayout.spec(c)
                ).apply {
                    width = cellPx
                    height = cellPx
                    setMargins(0, 0, 0, 0)
                }
                tv.layoutParams = params
                tv.gravity = Gravity.CENTER
                tv.typeface = Typeface.MONOSPACE

                when {
                    r == 0 && c == 0 -> {
                        tv.text = ""
                        tv.setBackgroundColor(Color.TRANSPARENT)
                    }
                    r == 0 -> {
                        tv.text = c.toString()
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                        tv.setTextColor(mutedColor)
                        tv.setBackgroundColor(Color.TRANSPARENT)
                    }
                    c == 0 -> {
                        tv.text = GameBoard.indexToRow(r - 1).lowercase(Locale.US)
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
                        tv.setTextColor(mutedColor)
                        tv.setBackgroundColor(Color.TRANSPARENT)
                    }
                    else -> {
                        val rowIdx = r - 1
                        val colIdx = c - 1
                        val cellDrawable = BoardCellDrawable(
                            fillColor = surfaceColor,
                            innerLineColor = innerLineColor,
                            outerBorderColor = inkColor,
                            outerStrokePx = outerStrokePx,
                            isTopOuter = rowIdx == 0,
                            isLeftOuter = colIdx == 0,
                            isRightOuter = colIdx == GameBoard.BOARD_DIMENSION - 1,
                            isBottomOuter = rowIdx == GameBoard.BOARD_DIMENSION - 1
                        )
                        tv.text = ""
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                        tv.setTextColor(inkColor)
                        tv.background = cellDrawable
                        tv.tag = CellCoord(rowIdx, colIdx)
                        tv.setOnClickListener(this)
                        boardCells[rowIdx][colIdx] = tv
                        boardCellDrawables[rowIdx][colIdx] = cellDrawable
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
                    updatedMatch = challengeClientController.makeMoveAndGetMatch(newMove)
                    if (updatedMatch != null) {
                        latestMoves = challengeClientController.getMoveHistory(match.id)
                    }
                } else {
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
            if (match.mode.equals("CHALLENGE", ignoreCase = true)) "CHALLENGE MATCH" else "HUMAN MATCH"

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
                val targetFill = if (r == lastRowIdx && c == lastColIdx) lastMoveBg else surfaceColor
                val drawable = boardCellDrawables[r][c]
                if (drawable != null && drawable.fillColor != targetFill) {
                    drawable.fillColor = targetFill
                    cellTv.invalidate()
                }
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
            navigateBackToHome()
            return
        }

        KoraNoticeDialog.show(
            context = this,
            title = "STOP PLAYING?",
            message = "Do you want to stop playing?\n\nYou will lose 1 point.",
            tone = KoraNoticeDialog.Tone.ERROR,
            primaryText = "YES, STOP",
            primaryBgRes = R.drawable.bg_btn_blush,
            onPrimary = { executeStopPlaying() },
            secondaryText = "NO, CONTINUE",
            secondaryBgRes = R.drawable.bg_btn_mint
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

    private fun navigateBackToHome() {
        val intent = android.content.Intent(this, PlayerHomeActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(PlayerHomeActivity.EXTRA_PLAYER, player)
        }
        startActivity(intent)
        finish()
    }

    private inner class MoveHistoryAdapter : BaseAdapter() {
        override fun getCount(): Int = listMove.size
        override fun getItem(position: Int): Move = listMove[position]
        override fun getItemId(position: Int): Long = listMove[position].moveOrder.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val rowView = convertView ?: LayoutInflater.from(this@MatchActivity)
                .inflate(R.layout.item_move_history, parent, false)

            val mv = getItem(position)
            rowView.setBackgroundResource(
                if (position % 2 == 1) R.drawable.bg_move_row_zebra else R.drawable.bg_move_row_white
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
            val parts = raw.trim().split(" ")
            val timePart = if (parts.size >= 2) parts[1] else parts[0]
            return if (timePart.length >= 5) timePart.substring(0, 5) else timePart
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
