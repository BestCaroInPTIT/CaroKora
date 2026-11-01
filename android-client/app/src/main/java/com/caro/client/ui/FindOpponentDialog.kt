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
import com.caro.client.controller.HumanMatchClientController
import com.caro.client.entity.Player
import java.util.Locale

class FindOpponentDialog(
    context: Context,
    private val player: Player
) : Dialog(context), View.OnClickListener {

    private var lblTitle: TextView
    private var lblMessage: TextView
    private var lblTimer: TextView
    private var btnContinueMatchmaking: Button
    private var btnCancel: Button

    private val humanMatchClientController = HumanMatchClientController()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var countDownTimer: CountDownTimer? = null
    private var isSearching = false
    private var isTransitioning = false

    private var startCycleAction: (() -> Unit)? = null
    private var stopCycleAction: (() -> Unit)? = null

    init {
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

        val dm = context.resources.displayMetrics
        fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), dm).toInt()

        val stopCycle = {
            isSearching = false
            mainHandler.removeCallbacksAndMessages(null)
            countDownTimer?.cancel()
            countDownTimer = null
        }
        stopCycleAction = stopCycle

        val pollRunnable = object : Runnable {
            override fun run() {
                if (!isSearching) return
                Thread {
                    val matched = try {
                        humanMatchClientController.findAndCreateMatch(player.id)
                    } catch (_: Exception) {
                        null
                    }
                    mainHandler.post {
                        if (!isShowing || !isSearching) return@post
                        if (matched != null && matched.id > 0 && !isTransitioning) {
                            isTransitioning = true
                            stopCycle()
                            dismiss()
                            val intent = Intent(context, MatchActivity::class.java)
                            intent.putExtra("extra_player", player)
                            intent.putExtra("extra_match", matched)
                            context.startActivity(intent)
                        } else if (isSearching) {
                            mainHandler.postDelayed(this, 1200L)
                        }
                    }
                }.start()
            }
        }

        val startCycle: () -> Unit = {
            stopCycle()
            isSearching = true

            lblTitle.text = "FINDING AN OPPONENT"
            lblTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_sky))
            lblMessage.text = "Searching for another player."
            lblTimer.visibility = View.VISIBLE
            lblTimer.text = "02:00"
            btnContinueMatchmaking.visibility = View.GONE

            val cancelLp = (btnCancel.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(dp(160), dp(44))
            cancelLp.width = dp(160)
            cancelLp.weight = 0f
            btnCancel.layoutParams = cancelLp

            countDownTimer = object : CountDownTimer(120_000L, 1_000L) {
                override fun onTick(millisUntilFinished: Long) {
                    val s = (millisUntilFinished / 1000L).toInt()
                    lblTimer.text = String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
                }

                override fun onFinish() {
                    lblTimer.text = "00:00"
                    stopCycle()
                    Thread {
                        try {
                            humanMatchClientController.postJson<Map<String, Any>>(
                                "/api/human-match/cancel-find",
                                mapOf("playerId" to player.id),
                                Map::class.java
                            )
                        } catch (_: Exception) {
                        }
                    }.start()

                    lblTitle.text = "NO OPPONENT AVAILABLE!"
                    lblTitle.setBackgroundColor(ContextCompat.getColor(context, R.color.kora_coral))
                    lblMessage.text = "No player was found within 2 minutes."
                    lblTimer.visibility = View.GONE
                    btnContinueMatchmaking.visibility = View.VISIBLE

                    val lp = (btnCancel.layoutParams as? LinearLayout.LayoutParams)
                        ?: LinearLayout.LayoutParams(0, dp(44), 0.9f)
                    lp.width = 0
                    lp.weight = 0.9f
                    btnCancel.layoutParams = lp
                }
            }.start()

            mainHandler.post(pollRunnable)
        }
        startCycleAction = startCycle

        startCycle()

        setOnShowListener {
            val widthPx = (dm.widthPixels * 0.90f).toInt()
            window?.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        setOnDismissListener {
            stopCycle()
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnContinueMatchmaking -> startCycleAction?.invoke()
            R.id.btnCancel -> {
                stopCycleAction?.invoke()
                Thread {
                    try {
                        humanMatchClientController.postJson<Map<String, Any>>(
                            "/api/human-match/cancel-find",
                            mapOf("playerId" to player.id),
                            Map::class.java
                        )
                    } catch (_: Exception) {
                    }
                }.start()
                dismiss()
            }
        }
    }
}
