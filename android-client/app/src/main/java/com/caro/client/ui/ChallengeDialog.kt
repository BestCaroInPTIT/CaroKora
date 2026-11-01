package com.caro.client.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.caro.client.R
import com.caro.client.controller.ApiClient
import com.caro.client.controller.ChallengeClientController
import com.caro.client.entity.Match
import com.caro.client.entity.Player
import java.util.Locale

class ChallengeDialog(
    context: Context,
    private val player: Player,
    private val opponent: Player
) : Dialog(context), View.OnClickListener {

    private var lblTitle: TextView
    private var lblMessage: TextView
    private var lblTimer: TextView
    private var rowYesNo: LinearLayout
    private var rowBottomActions: LinearLayout
    private var btnYes: Button
    private var btnNo: Button
    private var btnCancel: Button
    private var btnChallengeAgain: Button

    private var match: Match? = null
    private val isIncomingChallenge: Boolean
    private val challengeClientController = ChallengeClientController()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var countDownTimer: CountDownTimer? = null
    private var isPolling = false
    private var isTransitioningToMatch = false

    private var showDeclinedAction: (() -> Unit)? = null
    private var startWaitingAction: (() -> Unit)? = null
    private var stopAllAction: (() -> Unit)? = null
    private var respondIncomingAction: ((Boolean) -> Unit)? = null

    init {
        val incoming = ApiClient.consumeIncomingChallenge()
        match = incoming
        isIncomingChallenge = incoming != null

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.challenge)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setCancelable(false)

        lblTitle = findViewById(R.id.lblTitle)
        lblMessage = findViewById(R.id.lblMessage)
        lblTimer = findViewById(R.id.lblTimer)
        rowYesNo = findViewById(R.id.rowYesNo)
        rowBottomActions = findViewById(R.id.rowBottomActions)
        btnYes = findViewById(R.id.btnYes)
        btnNo = findViewById(R.id.btnNo)
        btnCancel = findViewById(R.id.btnCancel)
        btnChallengeAgain = findViewById(R.id.btnChallengeAgain)

        btnYes.setOnClickListener(this)
        btnNo.setOnClickListener(this)
        btnCancel.setOnClickListener(this)
        btnChallengeAgain.setOnClickListener(this)

        val dm = context.resources.displayMetrics
        fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), dm).toInt()

        lateinit var pollRunnable: Runnable

        val stopAll = {
            isPolling = false
            mainHandler.removeCallbacksAndMessages(null)
            countDownTimer?.cancel()
            countDownTimer = null
        }
        stopAllAction = stopAll

        val launchMatch = { readyMatch: Match ->
            if (!isTransitioningToMatch) {
                isTransitioningToMatch = true
                stopAll()
                dismiss()
                val intent = Intent(context, MatchActivity::class.java)
                intent.putExtra("extra_player", player)
                intent.putExtra("extra_match", readyMatch)
                context.startActivity(intent)
            }
        }

        val showDeclined = {
            stopAll()
            lblTitle.text = "MAYBE NEXT TIME..."
            lblTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_coral))
            lblMessage.visibility = View.GONE
            lblTimer.visibility = View.GONE
            rowYesNo.visibility = View.GONE

            rowBottomActions.visibility = View.VISIBLE
            btnChallengeAgain.visibility = View.VISIBLE
            val lp = (btnCancel.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(0, dp(44), 1f)
            lp.width = 0
            lp.weight = 1f
            lp.marginStart = dp(6)
            btnCancel.layoutParams = lp
            btnCancel.visibility = View.VISIBLE
        }
        showDeclinedAction = showDeclined

        pollRunnable = object : Runnable {
            override fun run() {
                if (!isPolling) return
                val activeMatchId = match?.id ?: return
                Thread {
                    val latest = try {
                        challengeClientController.getJson<Match>("/api/match/$activeMatchId", Match::class.java)
                    } catch (_: Exception) {
                        null
                    }
                    mainHandler.post {
                        if (!isShowing || !isPolling) return@post
                        if (latest != null) {
                            match = latest
                            when (latest.status.uppercase(Locale.US)) {
                                "PLAYING" -> launchMatch(latest)
                                "REJECTED", "EXPIRED", "BUSY" -> {
                                    if (!isIncomingChallenge) {
                                        showDeclined()
                                    } else {
                                        stopAll()
                                        dismiss()
                                    }
                                }
                                "CANCELLED" -> {
                                    stopAll()
                                    dismiss()
                                }
                            }
                        }
                        if (isPolling) {
                            mainHandler.postDelayed(this, 1200L)
                        }
                    }
                }.start()
            }
        }

        val respondIncoming = { accept: Boolean ->
            val targetId = match?.id ?: 0
            if (targetId <= 0) {
                dismiss()
            } else {
                btnYes.isEnabled = false
                btnNo.isEnabled = false
                stopAll()
                Thread {
                    val updated = try {
                        challengeClientController.respondChallenge(targetId, accept)
                    } catch (_: Exception) {
                        null
                    }
                    mainHandler.post {
                        if (!accept) {
                            dismiss()
                        } else if (updated != null && updated.status.equals("PLAYING", ignoreCase = true)) {
                            launchMatch(updated)
                        } else {
                            dismiss()
                        }
                    }
                }.start()
            }
        }
        respondIncomingAction = respondIncoming

        val startWaiting: () -> Unit = {
            stopAll()
            lblTitle.text = "CHALLENGE PLAYER ${opponent.gameID.uppercase(Locale.US)}!"
            lblTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_sky))
            lblMessage.visibility = View.VISIBLE
            lblMessage.text = "Waiting for ${opponent.gameID} to accept challenge..."
            lblTimer.visibility = View.VISIBLE
            lblTimer.text = "02:00"
            rowYesNo.visibility = View.GONE

            rowBottomActions.visibility = View.VISIBLE
            btnChallengeAgain.visibility = View.GONE
            val lp = (btnCancel.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(dp(160), dp(44))
            lp.width = dp(160)
            lp.weight = 0f
            lp.marginStart = 0
            btnCancel.layoutParams = lp
            btnCancel.visibility = View.VISIBLE

            countDownTimer = object : CountDownTimer(120_000L, 1_000L) {
                override fun onTick(millisUntilFinished: Long) {
                    val s = (millisUntilFinished / 1000L).toInt()
                    lblTimer.text = String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
                }

                override fun onFinish() {
                    lblTimer.text = "00:00"
                    val activeId = match?.id ?: 0
                    if (activeId > 0) {
                        Thread {
                            try {
                                challengeClientController.postJson<Map<String, Any>>(
                                    "/api/challenge/cancel",
                                    mapOf("matchId" to activeId, "accept" to false),
                                    Map::class.java
                                )
                            } catch (_: Exception) {
                            }
                        }.start()
                    }
                    showDeclined()
                }
            }.start()

            isPolling = true
            mainHandler.postDelayed(pollRunnable, 1000L)
        }
        startWaitingAction = startWaiting

        if (isIncomingChallenge) {
            lblTitle.text = "PLAYER ${opponent.gameID.uppercase(Locale.US)} IS CHALLENGING YOU!"
            lblTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_amber))
            lblMessage.visibility = View.VISIBLE
            lblMessage.text = "Accept challenge?"
            lblTimer.visibility = View.VISIBLE
            lblTimer.text = "02:00"
            rowYesNo.visibility = View.VISIBLE
            rowBottomActions.visibility = View.GONE

            countDownTimer = object : CountDownTimer(120_000L, 1_000L) {
                override fun onTick(millisUntilFinished: Long) {
                    val s = (millisUntilFinished / 1000L).toInt()
                    lblTimer.text = String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
                }

                override fun onFinish() {
                    lblTimer.text = "00:00"
                    respondIncoming(false)
                }
            }.start()

            isPolling = true
            mainHandler.postDelayed(pollRunnable, 1000L)
        } else {
            lblTitle.text = "CHALLENGE PLAYER ${opponent.gameID.uppercase(Locale.US)}?"
            lblTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_mint))
            lblMessage.visibility = View.VISIBLE
            lblMessage.text = "Do you want to challenge player “${opponent.gameID}”?"
            lblTimer.visibility = View.GONE
            rowYesNo.visibility = View.VISIBLE
            btnYes.isEnabled = true
            btnNo.isEnabled = true
            rowBottomActions.visibility = View.GONE
        }

        setOnShowListener {
            val widthPx = (dm.widthPixels * 0.90f).toInt()
            window?.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        setOnDismissListener {
            stopAll()
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnYes, R.id.btnChallengeAgain -> {
                if (isIncomingChallenge && v.id == R.id.btnYes) {
                    respondIncomingAction?.invoke(true)
                } else {
                    btnYes.isEnabled = false
                    btnChallengeAgain.isEnabled = false
                    Thread {
                        val created = try {
                            challengeClientController.sendChallenge(player.id, opponent.id)
                        } catch (_: Exception) {
                            null
                        }
                        mainHandler.post {
                            btnYes.isEnabled = true
                            btnChallengeAgain.isEnabled = true
                            if (!isShowing) return@post
                            if (created != null) {
                                match = created
                                startWaitingAction?.invoke()
                            } else {
                                dismiss()
                                ApiClient.showNotice(
                                    context = context,
                                    title = "PLAYER UNAVAILABLE",
                                    message = "Player ${opponent.gameID} is in another match.\n\nTry again next time.",
                                    headerColorRes = R.color.kora_amber,
                                    primaryText = "OK"
                                )
                            }
                        }
                    }.start()
                }
            }
            R.id.btnNo -> {
                if (isIncomingChallenge) {
                    respondIncomingAction?.invoke(false)
                } else {
                    dismiss()
                }
            }
            R.id.btnCancel -> {
                val activeId = match?.id ?: 0
                val currentStatus = match?.status ?: ""
                stopAllAction?.invoke()
                if (activeId > 0 && currentStatus.equals("PENDING", ignoreCase = true)) {
                    Thread {
                        try {
                            challengeClientController.postJson<Map<String, Any>>(
                                "/api/challenge/cancel",
                                mapOf("matchId" to activeId, "accept" to false),
                                Map::class.java
                            )
                        } catch (_: Exception) {
                        }
                    }.start()
                }
                dismiss()
            }
        }
    }
}
