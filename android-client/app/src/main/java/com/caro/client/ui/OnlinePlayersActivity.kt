package com.caro.client.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.caro.client.R
import com.caro.client.controller.ApiClient
import com.caro.client.controller.ChallengeClientController
import com.caro.client.entity.Match
import com.caro.client.entity.Player
import com.caro.client.entity.PlayerStatistic
import java.util.Locale

class OnlinePlayersActivity : AppCompatActivity(), View.OnClickListener, AdapterView.OnItemClickListener {

    private lateinit var lblTitle: TextView
    private lateinit var lblTotalOnline: TextView
    private lateinit var lwOnlinePlayers: ListView
    private lateinit var btnBack: Button

    private lateinit var player: Player
    private var listOnline: ArrayList<PlayerStatistic> = ArrayList()
    private var adapter: BaseAdapter? = null
    private var activeChallengeDialog: ChallengeDialog? = null
    private var lastShownIncomingMatchId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.onlineplayers)

        val resolvedPlayer = (intent.getSerializableExtra("extra_player") as? Player)
            ?: ApiClient.getPlayer(this)
        if (resolvedPlayer == null) {
            finish()
            return
        }
        player = resolvedPlayer

        lblTitle = findViewById(R.id.lblTitle)
        lblTotalOnline = findViewById(R.id.lblTotalOnline)
        lwOnlinePlayers = findViewById(R.id.lwOnlinePlayers)
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener(this)

        val onlineAdapter = object : BaseAdapter() {
            override fun getCount(): Int = listOnline.size
            override fun getItem(position: Int): PlayerStatistic = listOnline[position]
            override fun getItemId(position: Int): Long = listOnline[position].id.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val rowView = convertView ?: LayoutInflater.from(this@OnlinePlayersActivity)
                    .inflate(R.layout.item_online_player, parent, false)

                val item = getItem(position)
                val cardPlayerBox = rowView.findViewById<LinearLayout>(R.id.cardPlayerBox)
                val lblIndex = rowView.findViewById<TextView>(R.id.lblIndex)
                val lblGameID = rowView.findViewById<TextView>(R.id.lblItemGameID)
                val lblStats = rowView.findViewById<TextView>(R.id.lblItemStats)
                val lblStatus = rowView.findViewById<TextView>(R.id.lblItemStatus)
                val btnViewProfile = rowView.findViewById<Button>(R.id.btnViewProfile)

                lblIndex.text = "${position + 1}"
                lblGameID.text = item.gameID

                val rankText = if (item.rank > 0) "RANK #${item.rank}" else "RANK #-"
                val ptsStr = if (item.totalScore % 1.0 == 0.0) {
                    item.totalScore.toLong().toString()
                } else {
                    String.format(Locale.US, "%.1f", item.totalScore)
                }
                lblStats.text = "POINTS $ptsStr  ·  $rankText"

                val isAvailable = item.onlineStatus.equals("AVAILABLE", ignoreCase = true)
                if (isAvailable) {
                    lblStatus.text = "AVAILABLE"
                    lblStatus.setBackgroundResource(R.drawable.bg_status_available)
                    lblStatus.setTextColor(ContextCompat.getColor(this@OnlinePlayersActivity, R.color.kora_ink))
                    cardPlayerBox.isEnabled = true
                    cardPlayerBox.isClickable = true
                    cardPlayerBox.setOnClickListener {
                        if (item.id != player.id) {
                            activeChallengeDialog?.dismiss()
                            ApiClient.pendingIncomingChallenge = null
                            activeChallengeDialog = ChallengeDialog(this@OnlinePlayersActivity, player, item).also {
                                it.show()
                            }
                        }
                    }
                } else {
                    lblStatus.text = "IN MATCH"
                    lblStatus.setBackgroundResource(R.drawable.bg_status_inmatch)
                    lblStatus.setTextColor(ContextCompat.getColor(this@OnlinePlayersActivity, R.color.kora_ink))
                    cardPlayerBox.setOnClickListener(null)
                    cardPlayerBox.isClickable = false
                    cardPlayerBox.isEnabled = false
                }

                btnViewProfile.setOnClickListener {
                    val intent = Intent(this@OnlinePlayersActivity, PlayerProfileActivity::class.java)
                    intent.putExtra("extra_player", player)
                    intent.putExtra("extra_target_player_id", item.id)
                    intent.putExtra("extra_is_public", true)
                    startActivity(intent)
                }

                return rowView
            }
        }
        adapter = onlineAdapter
        lwOnlinePlayers.adapter = onlineAdapter
        lwOnlinePlayers.onItemClickListener = this

        val mainHandler = Handler(Looper.getMainLooper())
        val refreshRunnable = object : Runnable {
            override fun run() {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
                onResume()
                mainHandler.postDelayed(this, 2500L)
            }
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mainHandler.removeCallbacks(refreshRunnable)
                    mainHandler.postDelayed(refreshRunnable, 2500L)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mainHandler.removeCallbacks(refreshRunnable)
                }
                Lifecycle.Event.ON_DESTROY -> {
                    mainHandler.removeCallbacks(refreshRunnable)
                    activeChallengeDialog?.dismiss()
                }
                else -> {}
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (!::player.isInitialized) return
        val myId = player.id
        val challengeClientController = ChallengeClientController()
        data class HeartbeatResp(val alive: Boolean = false, val incomingChallenge: Match? = null)

        Thread {
            val heartbeat = try {
                challengeClientController.postJson<HeartbeatResp>(
                    "/api/player/heartbeat",
                    mapOf("playerId" to myId),
                    HeartbeatResp::class.java
                )
            } catch (_: Exception) {
                null
            }
            val online = try {
                challengeClientController.getOnlinePlayers()
            } catch (_: Exception) {
                ArrayList()
            }
            val visiblePlayers = ArrayList(online.filter { it.id != myId })

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                listOnline = visiblePlayers
                lblTotalOnline.text = "TOTAL ONLINE PLAYERS: ${listOnline.size}"
                adapter?.notifyDataSetChanged()

                val incoming = heartbeat?.incomingChallenge
                if (incoming != null &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    activeChallengeDialog?.isShowing != true &&
                    incoming.id != lastShownIncomingMatchId
                ) {
                    val challenger = incoming.matchParticipant
                        .firstOrNull { it.player?.id != null && it.player?.id != player.id }
                        ?.player
                    if (challenger != null) {
                        lastShownIncomingMatchId = incoming.id
                        ApiClient.pendingIncomingChallenge = incoming
                        activeChallengeDialog = ChallengeDialog(this, player, challenger).also {
                            it.show()
                        }
                    }
                }
            }
        }.start()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnBack -> finish()
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (position !in listOnline.indices) return
        val selected = listOnline[position]
        if (selected.id != player.id && selected.onlineStatus.equals("AVAILABLE", ignoreCase = true)) {
            activeChallengeDialog?.dismiss()
            ApiClient.pendingIncomingChallenge = null
            activeChallengeDialog = ChallengeDialog(this, player, selected).also { it.show() }
        }
    }
}
