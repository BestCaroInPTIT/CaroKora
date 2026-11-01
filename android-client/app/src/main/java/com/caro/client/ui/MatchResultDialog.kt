package com.caro.client.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import com.caro.client.controller.HumanMatchClientController
import com.caro.client.entity.Match
import com.caro.client.entity.Player
import java.util.Locale

class MatchResultDialog(
    context: Context,
    private var player: Player,
    private var match: Match,
    totalScore: Double = 0.0
) : Dialog(context), View.OnClickListener {

    private var lblResultTitle: TextView
    private var lblMessage: TextView
    private var lblPointsEarned: TextView
    private var lblTotalPoints: TextView
    private var btnYesRematch: Button
    private var btnNoHome: Button

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.matchresult)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setCancelable(false)

        lblResultTitle = findViewById(R.id.lblResultTitle)
        lblMessage = findViewById(R.id.lblMessage)
        lblPointsEarned = findViewById(R.id.lblPointsEarned)
        lblTotalPoints = findViewById(R.id.lblTotalPoints)
        val lblRematchPrompt = findViewById<TextView>(R.id.lblRematchPrompt)
        btnYesRematch = findViewById(R.id.btnYesRematch)
        btnNoHome = findViewById(R.id.btnNoHome)

        btnYesRematch.setOnClickListener(this)
        btnNoHome.setOnClickListener(this)

        val humanMatchClientController = HumanMatchClientController()
        val mainHandler = Handler(Looper.getMainLooper())
        val isStoppedMatch = match.status.equals("STOPPED", ignoreCase = true)
        var isMonitoringRematch = false
        var hasAcceptedRematch = false
        var hasHandledOutcome = false
        var rematchPollAttempts = 0

        val dm = context.resources.displayMetrics
        fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), dm).toInt()

        val myPart = match.matchParticipant.firstOrNull { it.player?.id == player.id }
        val oppName = match.matchParticipant.firstOrNull { it.player?.id != player.id }?.player?.gameID ?: "Opponent"
        val outcome = myPart?.outcome?.uppercase(Locale.US) ?: "LOSS"
        val earned = myPart?.score ?: 0.0

        when {
            isStoppedMatch && outcome == "WIN" -> {
                lblResultTitle.text = "OPPONENT STOPPED"
                lblResultTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_mint))
                lblResultTitle.setTextColor(ContextCompat.getColor(context, R.color.kora_ink))
                lblMessage.text = "YOU WON!"
                lblRematchPrompt.text = "Player $oppName stopped playing. Return to home."
            }
            isStoppedMatch && outcome == "LOSS" -> {
                lblResultTitle.text = "DEFEAT"
                lblResultTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_coral))
                lblResultTitle.setTextColor(ContextCompat.getColor(context, R.color.kora_ink))
                lblMessage.text = "YOU STOPPED PLAYING!"
                lblRematchPrompt.text = "Match ended early. Return to home."
            }
            outcome == "WIN" -> {
                lblResultTitle.text = "VICTORY"
                lblResultTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_mint))
                lblResultTitle.setTextColor(ContextCompat.getColor(context, R.color.kora_ink))
                lblMessage.text = "YOU WON!"
                lblRematchPrompt.text = "Continue playing?"
            }
            outcome == "DRAW" -> {
                lblResultTitle.text = "DRAW"
                lblResultTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_amber))
                lblResultTitle.setTextColor(ContextCompat.getColor(context, R.color.kora_ink))
                lblMessage.text = "DRAW!"
                lblRematchPrompt.text = "Continue playing?"
            }
            else -> {
                lblResultTitle.text = "DEFEAT"
                lblResultTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_coral))
                lblResultTitle.setTextColor(ContextCompat.getColor(context, R.color.kora_ink))
                lblMessage.text = "YOU LOST!"
                lblRematchPrompt.text = "Continue playing?"
            }
        }

        val sign = if (earned >= 0.0) "+" else ""
        val ptUnit = if (earned == 1.0 || earned == -1.0) "point" else "points"
        lblPointsEarned.text = String.format(Locale.US, "Points earned: %s%.1f %s", sign, earned, ptUnit)
        val totalStr = if (totalScore % 1.0 == 0.0) {
            totalScore.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", totalScore)
        }
        lblTotalPoints.text = "Total points: $totalStr points"

        val navigateHome = {
            val hostActivity = context as? Activity
            val intent = Intent(context, PlayerHomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("extra_player", player)
            }
            context.startActivity(intent)
            hostActivity?.finish()
            Unit
        }

        data class RematchResp(val status: String = "PENDING", val match: Match? = null)

        val showRefusedNotice = {
            mainHandler.removeCallbacksAndMessages(null)
            dismiss()
            navigateHome()
        }

        var countdownSec = 10
        val countdownRunnable = object : Runnable {
            override fun run() {
                if (!isShowing || !isMonitoringRematch || hasHandledOutcome) return
                countdownSec--
                if (countdownSec <= 0) {
                    hasHandledOutcome = true
                    isMonitoringRematch = false
                    mainHandler.removeCallbacksAndMessages(null)
                    val targetOldMatchId = match.id
                    val currentPlayerId = player.id
                    Thread {
                        try {
                            humanMatchClientController.postJson<Map<String, Any>>(
                                "/api/human-match/rematch",
                                mapOf("oldMatchId" to targetOldMatchId, "playerId" to currentPlayerId, "accept" to false),
                                Map::class.java
                            )
                        } catch (_: Exception) {
                        }
                    }.start()
                    dismiss()
                    navigateHome()
                    return
                }

                if (hasAcceptedRematch) {
                    lblRematchPrompt.text = "Waiting for opponent... (${countdownSec}s)"
                } else {
                    lblRematchPrompt.text = "Continue playing? (${countdownSec}s)"
                }
                mainHandler.postDelayed(this, 1000L)
            }
        }

        lateinit var rematchPollRunnable: Runnable
        rematchPollRunnable = object : Runnable {
            override fun run() {
                if (!isMonitoringRematch || hasHandledOutcome) return
                if (hasAcceptedRematch) {
                    rematchPollAttempts++
                    if (rematchPollAttempts > 120) {
                        hasHandledOutcome = true
                        isMonitoringRematch = false
                        showRefusedNotice()
                        return
                    }
                }

                Thread {
                    val resp = try {
                        if (hasAcceptedRematch) {
                            humanMatchClientController.postJson<RematchResp>(
                                "/api/human-match/rematch",
                                mapOf("oldMatchId" to match.id, "playerId" to player.id, "accept" to true),
                                RematchResp::class.java
                            )
                        } else {
                            humanMatchClientController.getJson<RematchResp>(
                                "/api/human-match/rematch/${match.id}",
                                RematchResp::class.java
                            )
                        }
                    } catch (_: Exception) {
                        null
                    }

                    mainHandler.post {
                        if (!isShowing || !isMonitoringRematch || hasHandledOutcome) return@post
                        when (resp?.status?.uppercase(Locale.US)) {
                            "CREATED" -> {
                                val newMatch = resp.match
                                if (newMatch != null) {
                                    hasHandledOutcome = true
                                    isMonitoringRematch = false
                                    mainHandler.removeCallbacksAndMessages(null)
                                    dismiss()
                                    val hostActivity = context as? Activity
                                    val intent = Intent(context, MatchActivity::class.java)
                                    intent.putExtra("extra_player", player)
                                    intent.putExtra("extra_match", newMatch)
                                    context.startActivity(intent)
                                    hostActivity?.finish()
                                }
                            }
                            "REFUSED" -> {
                                hasHandledOutcome = true
                                isMonitoringRematch = false
                                showRefusedNotice()
                            }
                            else -> {
                                mainHandler.postDelayed(this, 1000L)
                            }
                        }
                    }
                }.start()
            }
        }

        val onYesClicked = {
            if (isStoppedMatch) {
                dismiss()
                navigateHome()
            } else if (!hasAcceptedRematch && !hasHandledOutcome) {
                hasAcceptedRematch = true
                rematchPollAttempts = 0
                btnYesRematch.isEnabled = false
                btnYesRematch.text = "WAITING..."
                lblRematchPrompt.text = "Waiting for opponent... (${countdownSec}s)"
                mainHandler.removeCallbacks(rematchPollRunnable)
                mainHandler.post(rematchPollRunnable)
            }
        }

        val onNoClicked = {
            if (!hasHandledOutcome) {
                hasHandledOutcome = true
                isMonitoringRematch = false
                mainHandler.removeCallbacksAndMessages(null)

                val targetOldMatchId = match.id
                val currentPlayerId = player.id
                Thread {
                    try {
                        humanMatchClientController.postJson<Map<String, Any>>(
                            "/api/human-match/rematch",
                            mapOf("oldMatchId" to targetOldMatchId, "playerId" to currentPlayerId, "accept" to false),
                            Map::class.java
                        )
                    } catch (_: Exception) {
                    }
                }.start()

                dismiss()
                navigateHome()
            }
        }

        btnYesRematch.setTag(R.id.btnYesRematch, onYesClicked)
        btnNoHome.setTag(R.id.btnYesRematch, onNoClicked)

        if (isStoppedMatch) {
            btnNoHome.visibility = View.GONE
            btnYesRematch.text = "BACK"
            val lp = (btnYesRematch.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(dp(176), dp(44))
            lp.width = dp(176)
            lp.weight = 0f
            lp.marginEnd = 0
            btnYesRematch.layoutParams = lp
        } else {
            isMonitoringRematch = true
            lblRematchPrompt.text = "Continue playing? (10s)"
            mainHandler.postDelayed(rematchPollRunnable, 1000L)
            mainHandler.postDelayed(countdownRunnable, 1000L)
        }

        setOnShowListener {
            val widthPx = (dm.widthPixels * 0.90f).toInt()
            window?.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        setOnDismissListener {
            isMonitoringRematch = false
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onClick(v: View?) {
        val action = v?.getTag(R.id.btnYesRematch) as? (() -> Unit)
        action?.invoke()
    }
}
