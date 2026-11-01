package com.caro.client.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.caro.client.R
import com.caro.client.controller.PlayerClientController
import com.caro.client.entity.Match
import com.caro.client.entity.Player
import java.util.concurrent.Executors

class PlayerHomeActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var imgAvatar: ImageView
    private lateinit var lblPlayerName: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnViewRanking: Button
    private lateinit var btnViewOnlinePlayers: Button
    private lateinit var btnLogout: Button

    private lateinit var player: Player
    private val playerClientController = PlayerClientController()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeChallengeDialog: ChallengeDialog? = null
    private var lastShownChallengeMatchId: Int = -1

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            pollHeartbeatAndIncomingChallenge()
            mainHandler.postDelayed(this, 2500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.playerhome)

        val resolvedPlayer = (intent.getSerializableExtra(EXTRA_PLAYER) as? Player)
            ?: SessionManager.getPlayer(this)
        if (resolvedPlayer == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        player = resolvedPlayer

        imgAvatar = findViewById(R.id.imgAvatar)
        lblPlayerName = findViewById(R.id.lblPlayerName)
        btnPlay = findViewById(R.id.btnPlay)
        btnViewRanking = findViewById(R.id.btnViewRanking)
        btnViewOnlinePlayers = findViewById(R.id.btnViewOnlinePlayers)
        btnLogout = findViewById(R.id.btnLogout)

        imgAvatar.setOnClickListener(this)
        btnPlay.setOnClickListener(this)
        btnViewRanking.setOnClickListener(this)
        btnViewOnlinePlayers.setOnClickListener(this)
        btnLogout.setOnClickListener(this)

        bindPlayerHeader()
    }

    override fun onResume() {
        super.onResume()
        SessionManager.getPlayer(this)?.let {
            player = it
            bindPlayerHeader()
        }
        mainHandler.removeCallbacks(heartbeatRunnable)
        mainHandler.post(heartbeatRunnable)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(heartbeatRunnable)
    }

    private fun bindPlayerHeader() {
        lblPlayerName.text = "HELLO, ${player.gameID.uppercase()}"
        findViewById<TextView>(R.id.lblPlayerGameIdSub)?.text = "GAME ID  ·  ${player.gameID}"
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.imgAvatar -> {
                val intent = Intent(this, PlayerProfileActivity::class.java)
                intent.putExtra(PlayerProfileActivity.EXTRA_PLAYER, player)
                intent.putExtra(PlayerProfileActivity.EXTRA_TARGET_PLAYER_ID, player.id)
                intent.putExtra(PlayerProfileActivity.EXTRA_IS_PUBLIC, false)
                startActivity(intent)
            }
            R.id.btnPlay -> {
                val intent = Intent(this, GameModeActivity::class.java)
                intent.putExtra(GameModeActivity.EXTRA_PLAYER, player)
                startActivity(intent)
            }
            R.id.btnViewOnlinePlayers -> {
                val intent = Intent(this, OnlinePlayersActivity::class.java)
                intent.putExtra(OnlinePlayersActivity.EXTRA_PLAYER, player)
                startActivity(intent)
            }
            R.id.btnViewRanking -> {
                // Diem tich hop cho thanh vien cung nhom (Nguyen Manh Duc - Chuc nang Bang xep hang)
                KoraNoticeDialog.show(
                    context = this,
                    title = "VIEW RANKING",
                    message = "Chức năng Xem bảng xếp hạng (RankingActivity) là điểm ghép nối của thành viên Nguyễn Mạnh Đức.",
                    tone = KoraNoticeDialog.Tone.INFO
                )
            }
            R.id.btnLogout -> handleLogout()
        }
    }

    private fun pollHeartbeatAndIncomingChallenge() {
        val currentId = player.id
        if (currentId <= 0) return
        ioExecutor.execute {
            val resp = try {
                playerClientController.sendHeartbeat(currentId)
            } catch (_: Exception) {
                null
            }
            val incoming: Match = resp?.incomingChallenge ?: return@execute
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (activeChallengeDialog?.isShowing == true) return@runOnUiThread
                if (incoming.id == lastShownChallengeMatchId) return@runOnUiThread

                val challenger = incoming.matchParticipant
                    .firstOrNull { it.player?.id != null && it.player?.id != player.id }
                    ?.player ?: return@runOnUiThread

                lastShownChallengeMatchId = incoming.id
                activeChallengeDialog = ChallengeDialog(
                    context = this,
                    player = player,
                    opponent = challenger,
                    incomingMatch = incoming
                ).also { it.show() }
            }
        }
    }

    private fun handleLogout() {
        val currentId = player.id
        ioExecutor.execute {
            try {
                playerClientController.logout(currentId)
            } catch (_: Exception) {
            }
            runOnUiThread {
                SessionManager.clearSession(this)
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(heartbeatRunnable)
        activeChallengeDialog?.dismiss()
        ioExecutor.shutdownNow()
    }

    companion object {
        const val EXTRA_PLAYER = "extra_player"
    }
}
