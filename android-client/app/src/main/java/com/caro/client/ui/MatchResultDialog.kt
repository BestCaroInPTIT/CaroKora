package com.caro.client.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.caro.client.R
import com.caro.client.controller.HumanMatchClientController
import com.caro.client.entity.Match
import com.caro.client.entity.Player
import java.util.Locale
import java.util.concurrent.Executors

class MatchResultDialog @JvmOverloads constructor(
    context: Context,
    private val player: Player,
    private val match: Match,
    private val totalScore: Double = 0.0
) : Dialog(context), View.OnClickListener {

    private lateinit var lblResultTitle: TextView
    private lateinit var lblMessage: TextView
    private lateinit var lblPointsEarned: TextView
    private lateinit var lblTotalPoints: TextView
    private lateinit var btnYesRematch: Button
    private lateinit var btnNoHome: Button

    private val humanMatchClientController = HumanMatchClientController()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isWaitingRematch = false
    private var rematchPollAttempts = 0

    private val rematchPollRunnable = object : Runnable {
        override fun run() {
            if (!isWaitingRematch) return
            rematchPollAttempts++
            if (rematchPollAttempts > 20) {
                isWaitingRematch = false
                showRematchRefusedNotice()
                return
            }

            ioExecutor.execute {
                val resp = try {
                    humanMatchClientController.quickRematch(match.id, player.id, accept = true)
                } catch (_: Exception) {
                    null
                }
                mainHandler.post {
                    if (!isShowing || !isWaitingRematch) return@post
                    when (resp?.status?.uppercase(Locale.US)) {
                        "CREATED" -> {
                            val newMatch = resp.match
                            if (newMatch != null) {
                                isWaitingRematch = false
                                launchRematch(newMatch)
                            }
                        }
                        "REFUSED" -> {
                            isWaitingRematch = false
                            showRematchRefusedNotice()
                        }
                        else -> {
                            mainHandler.postDelayed(this, 1500L)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.matchresult)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setCancelable(false)

        lblResultTitle = findViewById(R.id.lblResultTitle)
        lblMessage = findViewById(R.id.lblMessage)
        lblPointsEarned = findViewById(R.id.lblPointsEarned)
        lblTotalPoints = findViewById(R.id.lblTotalPoints)
        btnYesRematch = findViewById(R.id.btnYesRematch)
        btnNoHome = findViewById(R.id.btnNoHome)

        btnYesRematch.setOnClickListener(this)
        btnNoHome.setOnClickListener(this)

        bindOutcomeAndPoints()
    }

    private fun bindOutcomeAndPoints() {
        val myPart = match.matchParticipant.firstOrNull { it.player?.id == player.id }
        val oppName = match.matchParticipant.firstOrNull { it.player?.id != player.id }?.player?.gameID ?: "Opponent"
        val outcome = myPart?.outcome?.uppercase(Locale.US) ?: "LOSS"
        val earned = myPart?.score ?: 0.0
        val isStopped = match.status.equals("STOPPED", ignoreCase = true)

        when {
            isStopped && outcome == "WIN" -> {
                lblResultTitle.text = "OPPONENT STOPPED"
                lblResultTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_mint))
                lblResultTitle.setTextColor(ContextCompat.getColor(context, R.color.kora_ink))
                lblMessage.text = "YOU WON!\nPlayer $oppName stopped playing."
            }
            isStopped && outcome == "LOSS" -> {
                lblResultTitle.text = "DEFEAT"
                lblResultTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_coral))
                lblResultTitle.setTextColor(ContextCompat.getColor(context, R.color.kora_ink))
                lblMessage.text = "YOU STOPPED PLAYING!"
            }
            outcome == "WIN" -> {
                lblResultTitle.text = "VICTORY"
                lblResultTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_mint))
                lblResultTitle.setTextColor(ContextCompat.getColor(context, R.color.kora_ink))
                lblMessage.text = "YOU WON!"
            }
            outcome == "DRAW" -> {
                lblResultTitle.text = "DRAW"
                lblResultTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_amber))
                lblResultTitle.setTextColor(ContextCompat.getColor(context, R.color.kora_ink))
                lblMessage.text = "DRAW!"
            }
            else -> {
                lblResultTitle.text = "DEFEAT"
                lblResultTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_coral))
                lblResultTitle.setTextColor(ContextCompat.getColor(context, R.color.kora_ink))
                lblMessage.text = "YOU LOST!"
            }
        }

        val sign = if (earned >= 0.0) "+" else ""
        val ptUnit = if (earned == 1.0 || earned == -1.0) "point" else "points"
        lblPointsEarned.text = String.format(Locale.US, "Points earned: %s%.1f %s", sign, earned, ptUnit)
        lblTotalPoints.text = String.format(Locale.US, "Total points: %.1f points", totalScore)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnYesRematch -> handleRequestRematch()
            R.id.btnNoHome -> handleDeclineAndClose()
        }
    }

    private fun handleRequestRematch() {
        if (isWaitingRematch) return
        isWaitingRematch = true
        rematchPollAttempts = 0
        btnYesRematch.isEnabled = false
        btnYesRematch.text = "WAITING..."
        lblMessage.text = "Waiting for your opponent to accept rematch..."
        mainHandler.post(rematchPollRunnable)
    }

    private fun handleDeclineAndClose() {
        isWaitingRematch = false
        mainHandler.removeCallbacks(rematchPollRunnable)
        ioExecutor.execute {
            try {
                humanMatchClientController.quickRematch(match.id, player.id, accept = false)
            } catch (_: Exception) {
            }
        }
        dismiss()
        (context as? Activity)?.finish()
    }

    private fun launchRematch(newMatch: Match) {
        mainHandler.removeCallbacks(rematchPollRunnable)
        dismiss()
        val hostActivity = context as? Activity
        val intent = Intent(context, MatchActivity::class.java)
        intent.putExtra(MatchActivity.EXTRA_PLAYER, player)
        intent.putExtra(MatchActivity.EXTRA_MATCH, newMatch)
        context.startActivity(intent)
        hostActivity?.finish()
    }

    private fun showRematchRefusedNotice() {
        mainHandler.removeCallbacks(rematchPollRunnable)
        KoraNoticeDialog.show(
            context = context,
            title = "REMATCH UNAVAILABLE",
            message = "Your opponent refused to rematch.",
            tone = KoraNoticeDialog.Tone.ERROR,
            primaryText = "BACK TO HOME",
            cancelable = false,
            onPrimary = {
                dismiss()
                (context as? Activity)?.finish()
            }
        )
    }

    override fun dismiss() {
        isWaitingRematch = false
        mainHandler.removeCallbacks(rematchPollRunnable)
        super.dismiss()
    }
}
