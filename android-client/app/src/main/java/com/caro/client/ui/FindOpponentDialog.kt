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

class FindOpponentDialog(
    context: Context,
    private val player: Player
) : Dialog(context), View.OnClickListener {

    private lateinit var lblTitle: TextView
    private lateinit var lblMessage: TextView
    private lateinit var lblTimer: TextView
    private lateinit var btnContinueMatchmaking: Button
    private lateinit var btnCancel: Button

    private val humanMatchClientController = HumanMatchClientController()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var countDownTimer: CountDownTimer? = null
    private var isSearching = false
    private var isTransitioning = false

    private val matchmakingPollRunnable = object : Runnable {
        override fun run() {
            if (!isSearching) return
            ioExecutor.execute {
                val matched = try {
                    humanMatchClientController.findAndCreateMatch(player.id)
                } catch (_: Exception) {
                    null
                }
                mainHandler.post {
                    if (!isShowing || !isSearching) return@post
                    if (matched != null && matched.id > 0) {
                        onMatchFound(matched)
                    } else if (isSearching) {
                        mainHandler.postDelayed(this, 1500L)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.findopponent)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setCancelable(false)

        lblTitle = findViewById(R.id.lblTitle)
        lblMessage = findViewById(R.id.lblMessage)
        lblTimer = findViewById(R.id.lblTimer)
        btnContinueMatchmaking = findViewById(R.id.btnContinueMatchmaking)
        btnCancel = findViewById(R.id.btnCancel)

        btnContinueMatchmaking.setOnClickListener(this)
        btnCancel.setOnClickListener(this)

        startMatchmakingCycle()
    }

    private fun startMatchmakingCycle() {
        stopTimersAndQueueLocal()
        isSearching = true

        lblTitle.text = "FINDING AN OPPONENT"
        lblTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_sky))
        lblMessage.text = "Searching for another player."
        lblTimer.visibility = View.VISIBLE
        lblTimer.text = "02:00"
        btnContinueMatchmaking.visibility = View.GONE

        countDownTimer = object : CountDownTimer(120_000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secTotal = (millisUntilFinished / 1000L).toInt()
                lblTimer.text = String.format(Locale.US, "%02d:%02d", secTotal / 60, secTotal % 60)
            }

            override fun onFinish() {
                lblTimer.text = "00:00"
                showNoOpponentAvailableState()
            }
        }.start()

        mainHandler.post(matchmakingPollRunnable)
    }

    private fun showNoOpponentAvailableState() {
        stopTimersAndQueueLocal()
        ioExecutor.execute {
            try {
                humanMatchClientController.cancelFindMatch(player.id)
            } catch (_: Exception) {
            }
        }

        lblTitle.text = "NO OPPONENT AVAILABLE!"
        lblTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_amber))
        lblMessage.text = "No player was found within 2 minutes."
        lblTimer.visibility = View.GONE
        btnContinueMatchmaking.visibility = View.VISIBLE
    }

    private fun onMatchFound(matched: Match) {
        if (isTransitioning) return
        isTransitioning = true
        stopTimersAndQueueLocal()
        dismiss()

        val intent = Intent(context, MatchActivity::class.java)
        intent.putExtra(MatchActivity.EXTRA_PLAYER, player)
        intent.putExtra(MatchActivity.EXTRA_MATCH, matched)
        context.startActivity(intent)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnContinueMatchmaking -> startMatchmakingCycle()
            R.id.btnCancel -> {
                stopTimersAndQueueLocal()
                ioExecutor.execute {
                    try {
                        humanMatchClientController.cancelFindMatch(player.id)
                    } catch (_: Exception) {
                    }
                }
                dismiss()
            }
        }
    }

    private fun stopTimersAndQueueLocal() {
        isSearching = false
        mainHandler.removeCallbacks(matchmakingPollRunnable)
        countDownTimer?.cancel()
        countDownTimer = null
    }

    override fun dismiss() {
        stopTimersAndQueueLocal()
        super.dismiss()
    }
}
