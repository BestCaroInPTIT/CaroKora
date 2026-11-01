package com.caro.client.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
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
import com.caro.client.controller.ChallengeClientController
import com.caro.client.entity.Match
import com.caro.client.entity.Player
import java.util.Locale
import java.util.concurrent.Executors

class ChallengeDialog @JvmOverloads constructor(
    context: Context,
    private val player: Player,
    private val opponent: Player,
    private val incomingMatch: Match? = null
) : Dialog(context), View.OnClickListener {

    private lateinit var lblTitle: TextView
    private lateinit var lblMessage: TextView
    private lateinit var lblTimer: TextView
    private lateinit var rowYesNo: LinearLayout
    private lateinit var rowBottomActions: LinearLayout
    private lateinit var btnYes: Button
    private lateinit var btnNo: Button
    private lateinit var btnCancel: Button
    private lateinit var btnChallengeAgain: Button

    private var match: Match? = incomingMatch
    private val challengeClientController = ChallengeClientController()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var countDownTimer: CountDownTimer? = null
    private var isPolling = false
    private var isTransitioningToMatch = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isPolling) return
            val activeMatchId = match?.id ?: return
            ioExecutor.execute {
                val latest = try {
                    challengeClientController.getMatchState(activeMatchId)
                } catch (_: Exception) {
                    null
                }
                mainHandler.post {
                    if (!isShowing || !isPolling) return@post
                    if (latest != null) {
                        match = latest
                        when (latest.status.uppercase(Locale.US)) {
                            "PLAYING" -> launchMatchActivity(latest)
                            "REJECTED", "EXPIRED", "BUSY" -> {
                                if (incomingMatch == null) {
                                    showDeclinedOrExpiredState()
                                } else {
                                    stopTimersAndPolling()
                                    dismiss()
                                }
                            }
                            "CANCELLED" -> {
                                stopTimersAndPolling()
                                dismiss()
                            }
                        }
                    }
                    if (isPolling) {
                        mainHandler.postDelayed(this, 1200L)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        if (incomingMatch != null) {
            setupIncomingChallengeState()
        } else {
            setupConfirmSendState()
        }
    }

    override fun onStart() {
        super.onStart()
        val widthPx = (context.resources.displayMetrics.widthPixels * 0.90f).toInt()
        window?.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun setupConfirmSendState() {
        stopTimersAndPolling()
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

    private fun setupWaitingForOpponentState() {
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
            ?: LinearLayout.LayoutParams(dpToPx(160), dpToPx(44))
        lp.width = dpToPx(160)
        lp.weight = 0f
        lp.marginStart = 0
        btnCancel.layoutParams = lp
        btnCancel.visibility = View.VISIBLE

        startTwoMinuteCountdown {
            val activeId = match?.id ?: 0
            if (activeId > 0) {
                Thread {
                    try {
                        challengeClientController.cancelChallenge(activeId)
                    } catch (_: Exception) {
                    }
                }.start()
            }
            showDeclinedOrExpiredState()
        }
        startPollingMatchState()
    }

    private fun setupIncomingChallengeState() {
        lblTitle.text = "PLAYER ${opponent.gameID.uppercase(Locale.US)} IS CHALLENGING YOU!"
        lblTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_amber))
        lblMessage.visibility = View.VISIBLE
        lblMessage.text = "Accept challenge?"
        lblTimer.visibility = View.VISIBLE
        lblTimer.text = "02:00"
        rowYesNo.visibility = View.VISIBLE
        rowBottomActions.visibility = View.GONE

        startTwoMinuteCountdown {
            handleIncomingResponse(accept = false)
        }
        startPollingMatchState()
    }

    private fun showDeclinedOrExpiredState() {
        stopTimersAndPolling()
        lblTitle.text = "MAYBE NEXT TIME..."
        lblTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_coral))
        lblMessage.visibility = View.GONE
        lblTimer.visibility = View.GONE
        rowYesNo.visibility = View.GONE

        rowBottomActions.visibility = View.VISIBLE
        btnChallengeAgain.visibility = View.VISIBLE
        val lp = (btnCancel.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(0, dpToPx(44), 1f)
        lp.width = 0
        lp.weight = 1f
        lp.marginStart = dpToPx(6)
        btnCancel.layoutParams = lp
        btnCancel.visibility = View.VISIBLE
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnYes -> {
                if (incomingMatch != null) {
                    handleIncomingResponse(accept = true)
                } else {
                    handleSendChallenge()
                }
            }
            R.id.btnNo -> {
                if (incomingMatch != null) {
                    handleIncomingResponse(accept = false)
                } else {
                    dismiss()
                }
            }
            R.id.btnChallengeAgain -> handleSendChallenge()
            R.id.btnCancel -> handleCancelPendingChallenge()
        }
    }

    private fun handleSendChallenge() {
        btnYes.isEnabled = false
        btnChallengeAgain.isEnabled = false
        ioExecutor.execute {
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
                    setupWaitingForOpponentState()
                } else {
                    dismiss()
                    KoraNoticeDialog.show(
                        context = context,
                        title = "PLAYER UNAVAILABLE",
                        message = "Player ${opponent.gameID} is in another match.\n\nTry again next time.",
                        tone = KoraNoticeDialog.Tone.WARNING,
                        primaryText = "OK"
                    )
                }
            }
        }
    }

    private fun handleIncomingResponse(accept: Boolean) {
        val targetMatchId = match?.id ?: incomingMatch?.id ?: 0
        if (targetMatchId <= 0) {
            dismiss()
            return
        }
        btnYes.isEnabled = false
        btnNo.isEnabled = false
        stopTimersAndPolling()

        Thread {
            val updated = try {
                challengeClientController.respondChallenge(targetMatchId, accept)
            } catch (_: Exception) {
                null
            }
            mainHandler.post {
                if (!accept) {
                    dismiss()
                    return@post
                }
                if (updated != null && updated.status.equals("PLAYING", ignoreCase = true)) {
                    launchMatchActivity(updated)
                } else {
                    dismiss()
                }
            }
        }.start()
    }

    private fun handleCancelPendingChallenge() {
        val activeId = match?.id ?: 0
        val currentStatus = match?.status ?: ""
        stopTimersAndPolling()
        if (activeId > 0 && currentStatus.equals("PENDING", ignoreCase = true)) {
            Thread {
                try {
                    challengeClientController.cancelChallenge(activeId)
                } catch (_: Exception) {
                }
            }.start()
        }
        dismiss()
    }

    private fun startTwoMinuteCountdown(onTimeout: () -> Unit) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(120_000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSec = (millisUntilFinished / 1000L).toInt()
                val min = totalSec / 60
                val sec = totalSec % 60
                lblTimer.text = String.format(Locale.US, "%02d:%02d", min, sec)
            }

            override fun onFinish() {
                lblTimer.text = "00:00"
                onTimeout()
            }
        }.start()
    }

    private fun startPollingMatchState() {
        isPolling = true
        mainHandler.removeCallbacks(pollRunnable)
        mainHandler.postDelayed(pollRunnable, 1000L)
    }

    private fun stopTimersAndPolling() {
        isPolling = false
        mainHandler.removeCallbacks(pollRunnable)
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun launchMatchActivity(readyMatch: Match) {
        if (isTransitioningToMatch) return
        isTransitioningToMatch = true
        stopTimersAndPolling()
        dismiss()

        val intent = Intent(context, MatchActivity::class.java)
        intent.putExtra(MatchActivity.EXTRA_PLAYER, player)
        intent.putExtra(MatchActivity.EXTRA_MATCH, readyMatch)
        context.startActivity(intent)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    override fun dismiss() {
        stopTimersAndPolling()
        super.dismiss()
        ioExecutor.shutdownNow()
    }
}
