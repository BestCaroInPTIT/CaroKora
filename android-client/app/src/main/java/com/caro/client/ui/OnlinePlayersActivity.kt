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
import android.widget.EditText
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
    private var txtSearch: EditText? = null
    private lateinit var lwOnlinePlayers: ListView
    private lateinit var btnBack: Button

    private lateinit var player: Player
    private var listOnline: ArrayList<PlayerStatistic> = ArrayList()

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

                rowView.findViewById<TextView>(R.id.lblIndex).text = (position + 1).toString()
                rowView.findViewById<TextView>(R.id.lblItemGameID).text = item.gameID

                val pts = item.totalScore
                val ptsStr = if (pts % 1.0 == 0.0) pts.toLong().toString() else String.format(Locale.US, "%.1f", pts)
                val rankStr = if (item.rank > 0) "#${item.rank}" else "#-"
                rowView.findViewById<TextView>(R.id.lblItemStats).text = "POINTS $ptsStr  ·  RANK $rankStr"

                val isAvail = item.onlineStatus.equals("AVAILABLE", ignoreCase = true)
                val cardPlayerBox = rowView.findViewById<LinearLayout>(R.id.cardPlayerBox)
                val lblStatus = rowView.findViewById<TextView>(R.id.lblItemStatus)
                val btnViewProfile = rowView.findViewById<Button>(R.id.btnViewProfile)

                if (isAvail) {
                    cardPlayerBox.alpha = 1.0f
                    cardPlayerBox.isClickable = true
                    cardPlayerBox.setOnClickListener {
                        onItemClick(lwOnlinePlayers, rowView, position, getItemId(position))
                    }
                    lblStatus.text = "AVAILABLE"
                    lblStatus.setBackgroundResource(R.drawable.bg_status_available)
                    lblStatus.setTextColor(ContextCompat.getColor(this@OnlinePlayersActivity, R.color.kora_ink))
                } else {
                    cardPlayerBox.alpha = 0.55f
                    cardPlayerBox.isClickable = false
                    cardPlayerBox.setOnClickListener(null)
                    lblStatus.text = "IN MATCH"
                    lblStatus.setBackgroundResource(R.drawable.bg_status_inmatch)
                    lblStatus.setTextColor(ContextCompat.getColor(this@OnlinePlayersActivity, R.color.kora_ink))
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
        lwOnlinePlayers.adapter = onlineAdapter
        lwOnlinePlayers.onItemClickListener = this

        val mainHandler = Handler(Looper.getMainLooper())
        val refreshRunnable = object : Runnable {
            override fun run() {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
                onResume()
                mainHandler.postDelayed(this, 3000L)
            }
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mainHandler.removeCallbacks(refreshRunnable)
                    mainHandler.post(refreshRunnable)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mainHandler.removeCallbacks(refreshRunnable)
                }
                Lifecycle.Event.ON_DESTROY -> {
                    mainHandler.removeCallbacks(refreshRunnable)
                    (lwOnlinePlayers.getTag(R.id.lwOnlinePlayers) as? ChallengeDialog)?.dismiss()
                }
                else -> {}
            }
        })
    }

    override fun onResume() {
        super.onResume()
        val challengeClientController = ChallengeClientController()
        val myId = player.id
        if (myId <= 0) return

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
                (lwOnlinePlayers.adapter as? BaseAdapter)?.notifyDataSetChanged()

                val incoming = heartbeat?.incomingChallenge
                val activeDialog = lwOnlinePlayers.getTag(R.id.lwOnlinePlayers) as? ChallengeDialog
                val lastId = (lwOnlinePlayers.getTag(R.id.lblTotalOnline) as? Int) ?: -1
                if (incoming != null &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    activeDialog?.isShowing != true &&
                    incoming.id != lastId
                ) {
                    val challenger = incoming.matchParticipant
                        .firstOrNull { it.player?.id != null && it.player?.id != player.id }
                        ?.player
                    if (challenger != null) {
                        lwOnlinePlayers.setTag(R.id.lblTotalOnline, incoming.id)
                        ApiClient.pendingIncomingChallenge = incoming
                        val dlg = ChallengeDialog(this, player, challenger)
                        lwOnlinePlayers.setTag(R.id.lwOnlinePlayers, dlg)
                        dlg.show()
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
            (lwOnlinePlayers.getTag(R.id.lwOnlinePlayers) as? ChallengeDialog)?.dismiss()
            ApiClient.pendingIncomingChallenge = null
            val dlg = ChallengeDialog(this, player, selected)
            lwOnlinePlayers.setTag(R.id.lwOnlinePlayers, dlg)
            dlg.show()
        }
    }
}
