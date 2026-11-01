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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.caro.client.R
import com.caro.client.controller.ApiClient
import com.caro.client.controller.PlayerClientController
import com.caro.client.entity.Match
import com.caro.client.entity.Player

class PlayerHomeActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var imgAvatar: ImageView
    private lateinit var lblPlayerName: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnViewRanking: Button
    private lateinit var btnViewOnlinePlayers: Button
    private lateinit var btnLogout: Button

    private lateinit var player: Player

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.playerhome)

        var activeChallengeDialog: ChallengeDialog? = null
        var lastShownChallengeMatchId: Int = -1

        val resolvedPlayer = (intent.getSerializableExtra("extra_player") as? Player)
            ?: ApiClient.getPlayer(this)
        if (resolvedPlayer == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        player = resolvedPlayer
        ApiClient.savePlayer(this, player)

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

        lblPlayerName.text = "HELLO, ${player.gameID}"

        data class HeartbeatResp(val alive: Boolean = false, val incomingChallenge: Match? = null)

        val mainHandler = Handler(Looper.getMainLooper())
        val controller = PlayerClientController()
        val heartbeatRunnable = object : Runnable {
            override fun run() {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
                val currentId = player.id
                if (currentId > 0) {
                    Thread {
                        val resp = try {
                            controller.postJson<HeartbeatResp>(
                                "/api/player/heartbeat",
                                mapOf("playerId" to currentId),
                                HeartbeatResp::class.java
                            )
                        } catch (_: Exception) {
                            null
                        }
                        val incoming = resp?.incomingChallenge ?: return@Thread
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@runOnUiThread
                            if (activeChallengeDialog?.isShowing == true) return@runOnUiThread
                            if (incoming.id == lastShownChallengeMatchId) return@runOnUiThread

                            val challenger = incoming.matchParticipant
                                .firstOrNull { it.player?.id != null && it.player?.id != player.id }
                                ?.player ?: return@runOnUiThread

                            lastShownChallengeMatchId = incoming.id
                            ApiClient.pendingIncomingChallenge = incoming
                            activeChallengeDialog = ChallengeDialog(this@PlayerHomeActivity, player, challenger).also {
                                it.show()
                            }
                        }
                    }.start()
                }
                mainHandler.postDelayed(this, 2500L)
            }
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mainHandler.removeCallbacks(heartbeatRunnable)
                    mainHandler.post(heartbeatRunnable)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mainHandler.removeCallbacks(heartbeatRunnable)
                }
                Lifecycle.Event.ON_DESTROY -> {
                    mainHandler.removeCallbacks(heartbeatRunnable)
                    activeChallengeDialog?.dismiss()
                }
                else -> {}
            }
        })
    }

    override fun onResume() {
        super.onResume()
        ApiClient.getPlayer(this)?.let {
            player = it
            lblPlayerName.text = "HELLO, ${player.gameID}"
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.imgAvatar -> {
                val intent = Intent(this, PlayerProfileActivity::class.java)
                intent.putExtra("extra_player", player)
                intent.putExtra("extra_target_player_id", player.id)
                intent.putExtra("extra_is_public", false)
                startActivity(intent)
            }
            R.id.btnPlay -> {
                val intent = Intent(this, GameModeActivity::class.java)
                intent.putExtra("extra_player", player)
                startActivity(intent)
            }
            R.id.btnViewOnlinePlayers -> {
                val intent = Intent(this, OnlinePlayersActivity::class.java)
                intent.putExtra("extra_player", player)
                startActivity(intent)
            }
            R.id.btnViewRanking -> {
                // Ngoai pham vi chuc nang ca nhan: bam vao khong thuc hien gi
            }
            R.id.btnLogout -> {
                val currentId = player.id
                val controller = PlayerClientController()
                Thread {
                    try {
                        controller.postJson<Map<String, Any>>(
                            "/api/player/logout",
                            mapOf("playerId" to currentId, "onlineStatus" to "OFFLINE"),
                            Map::class.java
                        )
                    } catch (_: Exception) {
                    }
                    runOnUiThread {
                        ApiClient.clearSession(this)
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }.start()
            }
        }
    }
}
