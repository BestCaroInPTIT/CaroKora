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
    private lateinit var lblRematchPrompt: TextView
    private lateinit var btnYesRematch: Button
    private lateinit var btnNoHome: Button

    private val humanMatchClientController = HumanMatchClientController()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isStoppedMatch = false
    private var isMonitoringRematch = false
    private var hasAcceptedRematch = false
    private var hasHandledOutcome = false
    private var rematchPollAttempts = 0

    private val rematchStatusPollRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoringRematch || hasHandledOutcome) return
            if (hasAcceptedRematch) {
                rematchPollAttempts++
                if (rematchPollAttempts > 120) {
                    isMonitoringRematch = false
                    showRematchRefusedNotice()
                    return
                }
            }

            ioExecutor.execute {
                val resp = try {
                    if (hasAcceptedRematch) {
                        humanMatchClientController.quickRematch(match.id, player.id, accept = true)
                    } else {
                        humanMatchClientController.getRematchStatus(match.id)
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
                                launchRematch(newMatch)
                            }
                        }
                        "REFUSED" -> {
                            hasHandledOutcome = true
                            isMonitoringRematch = false
                            showRematchRefusedNotice()
                        }
                        else -> {
                            mainHandler.postDelayed(this, 1000L)
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
        lblRematchPrompt = findViewById(R.id.lblRematchPrompt)
        btnYesRematch = findViewById(R.id.btnYesRematch)
        btnNoHome = findViewById(R.id.btnNoHome)

        btnYesRematch.setOnClickListener(this)
        btnNoHome.setOnClickListener(this)

        bindOutcomeAndPoints()
    }

    override fun onStart() {
        super.onStart()
        val widthPx = (context.resources.displayMetrics.widthPixels * 0.90f).toInt()
        window?.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun bindOutcomeAndPoints() {
        val myPart = match.matchParticipant.firstOrNull { it.player?.id == player.id }
        val oppName = match.matchParticipant.firstOrNull { it.player?.id != player.id }?.player?.gameID ?: "Opponent"
        val outcome = myPart?.outcome?.uppercase(Locale.US) ?: "LOSS"
        val earned = myPart?.score ?: 0.0
        isStoppedMatch = match.status.equals("STOPPED", ignoreCase = true)

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

        if (isStoppedMatch) {
            // Tran dau ket thuc som do 1 ben bam STOP PLAYING: hien 1 nut BACK quay ve PlayerHome
            btnNoHome.visibility = View.GONE
            btnYesRematch.text = "BACK"
            val lp = (btnYesRematch.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(dpToPx(176), dpToPx(44))
            lp.width = dpToPx(176)
            lp.weight = 0f
            lp.marginEnd = 0
            btnYesRematch.layoutParams = lp
        } else {
            // Tran dau ket thuc binh thuong: bat dau theo doi trang thai tai dau ngay khi mo hop thoai
            isMonitoringRematch = true
            mainHandler.postDelayed(rematchStatusPollRunnable, 1000L)
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnYesRematch -> {
                if (isStoppedMatch) {
                    dismiss()
                    navigateToPlayerHome()
                } else {
                    handleRequestRematch()
                }
            }
            R.id.btnNoHome -> handleDeclineAndReturnHome()
        }
    }

    private fun handleRequestRematch() {
        if (hasAcceptedRematch || hasHandledOutcome) return
        hasAcceptedRematch = true
        rematchPollAttempts = 0
        btnYesRematch.isEnabled = false
        btnYesRematch.text = "WAITING..."
        lblRematchPrompt.text = "Waiting for your opponent to accept rematch..."

        mainHandler.removeCallbacks(rematchStatusPollRunnable)
        mainHandler.post(rematchStatusPollRunnable)
    }

    private fun handleDeclineAndReturnHome() {
        if (hasHandledOutcome) return
        hasHandledOutcome = true
        isMonitoringRematch = false
        mainHandler.removeCallbacks(rematchStatusPollRunnable)

        // Gui quyet dinh tu choi tai dau tren luong doc lap de khong bi huy khi Activity/Dialog dong
        val targetOldMatchId = match.id
        val currentPlayerId = player.id
        Thread {
            try {
                humanMatchClientController.quickRematch(targetOldMatchId, currentPlayerId, accept = false)
            } catch (_: Exception) {
            }
        }.start()

        dismiss()
        navigateToPlayerHome()
    }

    private fun launchRematch(newMatch: Match) {
        mainHandler.removeCallbacks(rematchStatusPollRunnable)
        dismiss()
        val hostActivity = context as? Activity
        val intent = Intent(context, MatchActivity::class.java)
        intent.putExtra(MatchActivity.EXTRA_PLAYER, player)
        intent.putExtra(MatchActivity.EXTRA_MATCH, newMatch)
        context.startActivity(intent)
        hostActivity?.finish()
    }

    /**
     * Dung luong Muc 1.4:
     * "neu mot ben khong chap nhan thi he thong hien thi thong bao 'Your opponent refused to rematch.'
     *  -> nguoi choi chon OK -> he thong quay ve man hinh chinh."
     */
    private fun showRematchRefusedNotice() {
        mainHandler.removeCallbacks(rematchStatusPollRunnable)
        val hostContext = context
        dismiss()
        KoraNoticeDialog.show(
            context = hostContext,
            title = "REMATCH UNAVAILABLE",
            message = "Your opponent refused to rematch.",
            tone = KoraNoticeDialog.Tone.ERROR,
            primaryText = "OK",
            cancelable = false,
            onPrimary = {
                navigateToPlayerHome(hostContext)
            }
        )
    }

    private fun navigateToPlayerHome(targetContext: Context = context) {
        val hostActivity = targetContext as? Activity
        val intent = Intent(targetContext, PlayerHomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(PlayerHomeActivity.EXTRA_PLAYER, player)
        }
        targetContext.startActivity(intent)
        hostActivity?.finish()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    override fun dismiss() {
        isMonitoringRematch = false
        mainHandler.removeCallbacks(rematchStatusPollRunnable)
        super.dismiss()
        ioExecutor.shutdownNow()
    }
}
