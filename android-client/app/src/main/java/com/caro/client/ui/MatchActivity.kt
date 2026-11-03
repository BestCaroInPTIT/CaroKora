package com.caro.client.ui

import android.content.Intent
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.caro.client.R
import com.caro.client.controller.ApiClient
import com.caro.client.controller.AIMatchClientController
import com.caro.client.controller.ChallengeClientController
import com.caro.client.controller.HumanMatchClientController
import com.caro.client.controller.PlayerClientController
import com.caro.client.entity.GameBoard
import com.caro.client.entity.Match
import com.caro.client.entity.MatchParticipant
import com.caro.client.entity.Move
import com.caro.client.entity.Player
import java.util.Locale
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class MatchActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var lblOpponentName: TextView
    private lateinit var lblOpponentRank: TextView
    private lateinit var lblMatchId: TextView
    private lateinit var grdBoard: GridLayout
    private lateinit var lblPlayedTime: TextView
    private lateinit var lblTurnTimer: TextView
    private lateinit var lblCurrentTurn: TextView
    private lateinit var imgCurrentTurnAvatar: ImageView
    private lateinit var lblPlayerMoves: TextView
    private lateinit var lblOpponentMoves: TextView
    private lateinit var lwMoveHistory: ListView
    private lateinit var btnStopPlaying: Button
    private lateinit var match: Match
    private lateinit var player: Player

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.match)

        val resolvedPlayer = (intent.getSerializableExtra("extra_player") as? Player)
            ?: ApiClient.getPlayer(this)
        val resolvedMatch = intent.getSerializableExtra("extra_match") as? Match
        if (resolvedPlayer == null || resolvedMatch == null) {
            finish()
            return
        }
        player = resolvedPlayer
        match = resolvedMatch

        var myParticipant: MatchParticipant? = null
        var currentMove: Move? = null
        var listMove: ArrayList<Move> = ArrayList(match.move)

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

        var isSubmittingMove = false
        var isReportingTimeout = false
        var turnDeadlineMs = System.currentTimeMillis() + 15_000L
        var hasShownResultDialog = false
        var resultDialog: MatchResultDialog? = null

        val challengeClientController = ChallengeClientController()
        val humanMatchClientController = HumanMatchClientController()
        val aiMatchClientController = AIMatchClientController()
        val playerClientController = PlayerClientController()
        val mainHandler = Handler(Looper.getMainLooper())

        val dm = resources.displayMetrics
        fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), dm).toInt()

        val boardCells = Array(GameBoard.BOARD_DIMENSION) {
            arrayOfNulls<TextView>(GameBoard.BOARD_DIMENSION)
        }
        val boardCellFills = Array(GameBoard.BOARD_DIMENSION) {
            IntArray(GameBoard.BOARD_DIMENSION)
        }

        val horizontalBudget = dm.widthPixels - dp(28)
        val verticalBudget = (dm.heightPixels * 0.48f).toInt()
        val gridDim = GameBoard.BOARD_DIMENSION + 1
        val cellPx = maxOf(dp(15), min(horizontalBudget, verticalBudget) / gridDim)

        val inkColor = ContextCompat.getColor(this, R.color.kora_ink)
        val mutedColor = ContextCompat.getColor(this, R.color.kora_muted)
        val surfaceColor = ContextCompat.getColor(this, R.color.kora_surface)
        val lastMoveBg = ContextCompat.getColor(this, R.color.kora_blush)
        val colorX = ContextCompat.getColor(this, R.color.kora_x)
        val colorO = ContextCompat.getColor(this, R.color.kora_o)
        val innerLineColor = ContextCompat.getColor(this, R.color.kora_border_subtle)
        val outerStrokePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, dm)

        grdBoard.removeAllViews()
        grdBoard.setBackgroundColor(Color.TRANSPARENT)

        for (r in 0..GameBoard.BOARD_DIMENSION) {
            for (c in 0..GameBoard.BOARD_DIMENSION) {
                val tv = TextView(this)
                tv.layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(r),
                    GridLayout.spec(c)
                ).apply {
                    width = cellPx
                    height = cellPx
                    setMargins(0, 0, 0, 0)
                }
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
                        boardCellFills[rowIdx][colIdx] = surfaceColor
                        val isTopOuter = rowIdx == 0
                        val isLeftOuter = colIdx == 0
                        val isRightOuter = colIdx == GameBoard.BOARD_DIMENSION - 1
                        val isBottomOuter = rowIdx == GameBoard.BOARD_DIMENSION - 1

                        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = 1f
                            color = innerLineColor
                        }
                        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = outerStrokePx
                            color = inkColor
                        }

                        tv.background = object : Drawable() {
                            override fun draw(canvas: Canvas) {
                                val b = bounds
                                val w = b.width().toFloat()
                                val h = b.height().toFloat()
                                fillPaint.color = boardCellFills[rowIdx][colIdx]
                                canvas.drawRect(0f, 0f, w, h, fillPaint)
                                if (!isRightOuter) canvas.drawLine(w - 0.5f, 0f, w - 0.5f, h, innerPaint)
                                if (!isBottomOuter) canvas.drawLine(0f, h - 0.5f, w, h - 0.5f, innerPaint)
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
                        tv.text = ""
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                        tv.setTextColor(inkColor)
                        tv.tag = intArrayOf(rowIdx, colIdx)
                        boardCells[rowIdx][colIdx] = tv
                    }
                }
                grdBoard.addView(tv)
            }
        }

        var boardScale = 1.0f
        var boardTransX = 0f
        var boardTransY = 0f
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var activePtrId = MotionEvent.INVALID_POINTER_ID
        var downX = 0f
        var downY = 0f
        var lastX = 0f
        var lastY = 0f
        var isPanOrZoom = false

        val applyTransform = {
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

        val boardFrame = grdBoard.parent as? View
        if (boardFrame != null) {
            val scaleDetector = ScaleGestureDetector(
                this,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                        isPanOrZoom = true
                        return true
                    }

                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val oldScale = boardScale
                        val newScale = (oldScale * detector.scaleFactor).coerceIn(1.0f, 3.0f)
                        if (newScale == oldScale) return true
                        val cx = grdBoard.left + grdBoard.width / 2f
                        val cy = grdBoard.top + grdBoard.height / 2f
                        val ratio = newScale / oldScale
                        boardTransX = (detector.focusX - cx) - ratio * ((detector.focusX - cx) - boardTransX)
                        boardTransY = (detector.focusY - cy) - ratio * ((detector.focusY - cy) - boardTransY)
                        boardScale = newScale
                        applyTransform()
                        return true
                    }
                }
            )

            boardFrame.setOnTouchListener { _, ev ->
                scaleDetector.onTouchEvent(ev)
                if (ev.pointerCount >= 2 || scaleDetector.isInProgress) {
                    isPanOrZoom = true
                }
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        activePtrId = ev.getPointerId(0)
                        downX = ev.x
                        downY = ev.y
                        lastX = ev.x
                        lastY = ev.y
                        isPanOrZoom = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val idx = ev.findPointerIndex(activePtrId)
                        if (idx >= 0) {
                            val x = ev.getX(idx)
                            val y = ev.getY(idx)
                            if (!isPanOrZoom && boardScale > 1.01f &&
                                hypot((x - downX).toDouble(), (y - downY).toDouble()) > touchSlop
                            ) {
                                isPanOrZoom = true
                                lastX = x
                                lastY = y
                            } else if (isPanOrZoom && !scaleDetector.isInProgress && ev.pointerCount == 1 && boardScale > 1.01f) {
                                boardTransX += (x - lastX)
                                boardTransY += (y - lastY)
                                applyTransform()
                                lastX = x
                                lastY = y
                            } else {
                                lastX = x
                                lastY = y
                            }
                        }
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        val pIdx = ev.actionIndex
                        if (ev.getPointerId(pIdx) == activePtrId) {
                            val nextIdx = if (pIdx == 0) 1 else 0
                            if (nextIdx < ev.pointerCount) {
                                lastX = ev.getX(nextIdx)
                                lastY = ev.getY(nextIdx)
                                activePtrId = ev.getPointerId(nextIdx)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isPanOrZoom &&
                            hypot((ev.x - downX).toDouble(), (ev.y - downY).toDouble()) <= touchSlop * 1.5 &&
                            cellPx > 0
                        ) {
                            val pw = grdBoard.width / 2f
                            val ph = grdBoard.height / 2f
                            val lx = (ev.x - grdBoard.left - boardTransX - pw) / boardScale + pw
                            val ly = (ev.y - grdBoard.top - boardTransY - ph) / boardScale + ph
                            val gCol = floor(lx / cellPx).toInt()
                            val gRow = floor(ly / cellPx).toInt()
                            if (gRow in 1..GameBoard.BOARD_DIMENSION && gCol in 1..GameBoard.BOARD_DIMENSION) {
                                boardCells[gRow - 1][gCol - 1]?.let { onClick(it) }
                            }
                        }
                        isPanOrZoom = false
                        activePtrId = MotionEvent.INVALID_POINTER_ID
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isPanOrZoom = false
                        activePtrId = MotionEvent.INVALID_POINTER_ID
                    }
                }
                true
            }
        }

        val moveHistoryAdapter = object : BaseAdapter() {
            override fun getCount(): Int = listMove.size
            override fun getItem(position: Int): Move = listMove[position]
            override fun getItemId(position: Int): Long = listMove[position].id.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val rowView = convertView ?: LayoutInflater.from(this@MatchActivity)
                    .inflate(R.layout.item_move_history, parent, false)
                val mv = getItem(position)
                rowView.setBackgroundResource(
                    if (position % 2 == 1) R.drawable.bg_move_row_zebra else R.drawable.bg_move_row_white
                )
                rowView.findViewById<TextView>(R.id.lblMoveOrder).text = mv.moveOrder.toString()
                rowView.findViewById<TextView>(R.id.lblMoveTime).text = mv.createdAt ?: "--:--:--"
                rowView.findViewById<TextView>(R.id.lblMoveTurn).text =
                    mv.matchParticipant?.player?.gameID ?: (mv.matchParticipant?.side ?: "-")
                rowView.findViewById<TextView>(R.id.lblMoveCoord).text =
                    "${mv.row.uppercase(Locale.US)}${mv.column}"
                return rowView
            }
        }
        lwMoveHistory.adapter = moveHistoryAdapter

        val matchStartEpochMs = System.currentTimeMillis()
        var lastObservedMoveCount = -1
        var lastObservedTurn = ""

        val resetTurnTimer = {
            val limitSec = if (match.turnTimeLimit > 0) match.turnTimeLimit else 15
            turnDeadlineMs = System.currentTimeMillis() + limitSec * 1000L
            lblTurnTimer.text = String.format(Locale.US, "%02d:%02d", limitSec / 60, limitSec % 60)
        }

        val renderState = {
            myParticipant = match.matchParticipant.firstOrNull { it.player?.id == player.id }
            val oppParticipant = match.matchParticipant.firstOrNull { it.player?.id != player.id }

            lastObservedMoveCount = listMove.size
            lastObservedTurn = match.currentTurn

            val oppName = if (oppParticipant?.participantType.equals("AI", ignoreCase = true)) {
                "AI ${oppParticipant?.aiDifficulty?.uppercase(Locale.US) ?: ""}".trim()
            } else {
                oppParticipant?.player?.gameID ?: "OPPONENT"
            }
            val oppSide = oppParticipant?.side ?: "O"
            val mySide = myParticipant?.side ?: "X"

            findViewById<TextView>(R.id.lblMatchScreenTitle)?.text =
                when {
                    match.mode.equals("CHALLENGE", ignoreCase = true) -> "CHALLENGE MATCH"
                    match.mode.equals("AI", ignoreCase = true) -> "AI MATCH"
                    else -> "HUMAN MATCH"
                }

            lblOpponentName.text = "OPPONENT: ${oppName.uppercase(Locale.US)}"
            lblMatchId.text = "MATCH ID: ${match.id}"

            val isMyTurn = mySide.equals(match.currentTurn, ignoreCase = true)
            if (match.status.equals("PLAYING", ignoreCase = true)) {
                lblCurrentTurn.text = if (isMyTurn) {
                    "YOUR TURN  ·  $mySide"
                } else {
                    "${oppName.uppercase(Locale.US)}'S TURN  ·  $oppSide"
                }
                lblCurrentTurn.setTextColor(if (isMyTurn) inkColor else mutedColor)
            } else {
                lblCurrentTurn.text = "MATCH ${match.status}"
                lblCurrentTurn.setTextColor(mutedColor)
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

            for (r in 0 until GameBoard.BOARD_DIMENSION) {
                for (c in 0 until GameBoard.BOARD_DIMENSION) {
                    val cellTv = boardCells[r][c] ?: continue
                    when (match.gameBoard.getCell(r, c)) {
                        'X', 'x' -> {
                            cellTv.text = "X"
                            cellTv.setTextColor(colorX)
                        }
                        'O', 'o' -> {
                            cellTv.text = "O"
                            cellTv.setTextColor(colorO)
                        }
                        else -> cellTv.text = ""
                    }
                    val targetFill = if (r == lastRowIdx && c == lastColIdx) lastMoveBg else surfaceColor
                    if (boardCellFills[r][c] != targetFill) {
                        boardCellFills[r][c] = targetFill
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

        lateinit var applyUpdate: (Match) -> Unit
        applyUpdate = { updated ->
            val prevCount = lastObservedMoveCount
            val prevTurn = lastObservedTurn

            match = updated
            listMove = ArrayList(updated.move)

            if (listMove.size != prevCount || !updated.currentTurn.equals(prevTurn, ignoreCase = true)) {
                resetTurnTimer()
            }
            renderState()

            if ((updated.status.equals("FINISHED", ignoreCase = true) ||
                    updated.status.equals("STOPPED", ignoreCase = true)) &&
                !hasShownResultDialog
            ) {
                hasShownResultDialog = true
                mainHandler.removeCallbacksAndMessages(null)
                Thread {
                    val latestProfile = try {
                        playerClientController.getPlayerProfile(player.id)
                    } catch (_: Exception) {
                        null
                    }
                    val totalScore = latestProfile?.totalScore ?: 0.0
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            resultDialog = MatchResultDialog(this, player, updated, totalScore).also {
                                it.show()
                            }
                        }
                    }
                }.start()
            }
        }

        val navigateHome = {
            val intent = Intent(this, PlayerHomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("extra_player", player)
            }
            startActivity(intent)
            finish()
        }

        val promptStop: () -> Unit = {
            if (!match.status.equals("PLAYING", ignoreCase = true)) {
                navigateHome()
            } else {
                ApiClient.showNotice(
                    context = this,
                    title = "STOP PLAYING?",
                    message = "Do you want to stop playing?\n\nYou will lose 1 point.",
                    headerColorRes = R.color.kora_coral,
                    primaryText = "YES, STOP",
                    primaryBgRes = R.drawable.bg_btn_blush,
                    onPrimary = {
                        btnStopPlaying.isEnabled = false
                        Thread {
                            val stoppedMatch = try {
                                val stopEndpoint = when {
                                    match.mode.equals("AI", ignoreCase = true) -> "/api/ai-match/stop"
                                    else -> "/api/human-match/stop"
                                }
                                humanMatchClientController.postJson<Match>(
                                    stopEndpoint,
                                    mapOf("matchId" to match.id, "playerId" to player.id),
                                    Match::class.java
                                )
                            } catch (_: Exception) {
                                null
                            }
                            runOnUiThread {
                                btnStopPlaying.isEnabled = true
                                if (!isFinishing && !isDestroyed && stoppedMatch != null) {
                                    applyUpdate(stoppedMatch)
                                }
                            }
                        }.start()
                    },
                    secondaryText = "NO, CONTINUE",
                    secondaryBgRes = R.drawable.bg_btn_mint
                )
            }
        }
        btnStopPlaying.setTag(R.id.btnStopPlaying, promptStop)

        val onCellClick = { rowIdx: Int, colIdx: Int ->
            if (!isSubmittingMove && !hasShownResultDialog &&
                match.status.equals("PLAYING", ignoreCase = true) &&
                turnDeadlineMs > System.currentTimeMillis()
            ) {
                val me = myParticipant ?: match.matchParticipant.firstOrNull { it.player?.id == player.id }
                if (me != null && me.side.equals(match.currentTurn, ignoreCase = true) &&
                    match.gameBoard.getCell(rowIdx, colIdx) == '.'
                ) {
                    val newMove = Move(
                        row = GameBoard.indexToRow(rowIdx),
                        column = colIdx + 1,
                        moveOrder = listMove.size + 1,
                        matchParticipant = me,
                        matchId = match.id
                    )
                    currentMove = newMove
                    isSubmittingMove = true

                    Thread {
                        var updatedMatch: Match? = null
                        try {
                            if (match.mode.equals("CHALLENGE", ignoreCase = true)) {
                                val ok = challengeClientController.makeMove(newMove)
                                if (ok) {
                                    val latestMoves = challengeClientController.getMoveHistory(match.id)
                                    updatedMatch = challengeClientController.getJson("/api/match/${match.id}", Match::class.java)
                                    if (updatedMatch != null && latestMoves.isNotEmpty()) {
                                        updatedMatch.move = latestMoves
                                    }
                                }
                            } else if (match.mode.equals("AI", ignoreCase = true)) {
                                updatedMatch = aiMatchClientController.makeMove(newMove)
                            } else {
                                updatedMatch = humanMatchClientController.makeMove(newMove)
                            }
                        } catch (_: Exception) {
                        }

                        runOnUiThread {
                            isSubmittingMove = false
                            if (!isFinishing && !isDestroyed && updatedMatch != null) {
                                applyUpdate(updatedMatch)
                            }
                        }
                    }.start()
                }
            }
        }
        grdBoard.setTag(R.id.grdBoard, onCellClick)

        resetTurnTimer()
        renderState()

        val oppId = match.matchParticipant.firstOrNull { it.player?.id != player.id }?.player?.id ?: 0
        if (oppId > 0) {
            Thread {
                val oppStat = try {
                    playerClientController.getPlayerProfile(oppId)
                } catch (_: Exception) {
                    null
                }
                if (oppStat != null) {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            lblOpponentRank.text = if (oppStat.rank > 0) "RANK #${oppStat.rank}" else "RANK #-"
                        }
                    }
                }
            }.start()
        }

        val clockRunnable = object : Runnable {
            override fun run() {
                if (!hasShownResultDialog && match.status.equals("PLAYING", ignoreCase = true)) {
                    val remainingSec = ((turnDeadlineMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                    lblTurnTimer.text = String.format(Locale.US, "%02d:%02d", remainingSec / 60, remainingSec % 60)

                    if (remainingSec <= 0L) {
                        isSubmittingMove = true
                        if (!isReportingTimeout) {
                            isReportingTimeout = true
                            val targetMatchId = match.id
                            val currentPlayerId = player.id
                            val timeoutEndpoint = if (match.mode.equals("CHALLENGE", ignoreCase = true)) {
                                "/api/challenge/timeout"
                            } else if (match.mode.equals("AI", ignoreCase = true)) {
                                "/api/ai-match/timeout"
                            } else {
                                "/api/human-match/timeout"
                            }
                            Thread {
                                val timedOutMatch = try {
                                    humanMatchClientController.postJson<Match>(
                                        timeoutEndpoint,
                                        mapOf("matchId" to targetMatchId, "playerId" to currentPlayerId),
                                        Match::class.java
                                    )
                                } catch (_: Exception) {
                                    null
                                }
                                runOnUiThread {
                                    isReportingTimeout = false
                                    if (!isFinishing && !isDestroyed) {
                                        if (timedOutMatch != null) {
                                            applyUpdate(timedOutMatch)
                                        } else {
                                            for (mp in match.matchParticipant) {
                                                if (mp.side.equals(match.currentTurn, ignoreCase = true)) {
                                                    mp.setOutcome("LOSS")
                                                    mp.setScore(0.0)
                                                } else {
                                                    mp.setOutcome("WIN")
                                                    mp.setScore(1.0)
                                                }
                                            }
                                            match.status = "FINISHED"
                                            applyUpdate(match)
                                        }
                                    }
                                }
                            }.start()
                        }
                        return
                    }

                    val elapsedSec = ((System.currentTimeMillis() - matchStartEpochMs) / 1000L).coerceAtLeast(0L)
                    lblPlayedTime.text = String.format(Locale.US, "%02d:%02d", elapsedSec / 60, elapsedSec % 60)

                    mainHandler.postDelayed(this, 1000L)
                }
            }
        }

        val syncRunnable = object : Runnable {
            override fun run() {
                if (!hasShownResultDialog) {
                    val currentMatchId = match.id
                    if (currentMatchId > 0 && !isSubmittingMove) {
                        Thread {
                            val polled = try {
                                humanMatchClientController.getJson<Match>("/api/match/$currentMatchId", Match::class.java)
                            } catch (_: Exception) {
                                null
                            }
                            if (polled != null) {
                                runOnUiThread {
                                    if (!isFinishing && !isDestroyed) {
                                        applyUpdate(polled)
                                    }
                                }
                            }
                        }.start()
                    }
                    mainHandler.postDelayed(this, 1000L)
                }
            }
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mainHandler.removeCallbacks(clockRunnable)
                    mainHandler.removeCallbacks(syncRunnable)
                    mainHandler.post(clockRunnable)
                    mainHandler.post(syncRunnable)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mainHandler.removeCallbacks(clockRunnable)
                    mainHandler.removeCallbacks(syncRunnable)
                }
                Lifecycle.Event.ON_DESTROY -> {
                    mainHandler.removeCallbacksAndMessages(null)
                    resultDialog?.dismiss()
                }
                else -> {}
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                promptStop()
            }
        })
    }

    override fun onClick(v: View?) {
        if (v == null) return
        if (v.id == R.id.btnStopPlaying) {
            val promptStop = btnStopPlaying.getTag(R.id.btnStopPlaying) as? (() -> Unit)
            promptStop?.invoke()
            return
        }

        val coord = v.tag as? IntArray ?: return
        if (coord.size < 2) return
        val onCellClick = grdBoard.getTag(R.id.grdBoard) as? ((Int, Int) -> Unit)
        onCellClick?.invoke(coord[0], coord[1])
    }
}
