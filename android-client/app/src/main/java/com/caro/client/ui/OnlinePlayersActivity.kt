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
import com.caro.client.R
import com.caro.client.controller.ChallengeClientController
import com.caro.client.controller.PlayerClientController
import com.caro.client.entity.Match
import com.caro.client.entity.Player
import com.caro.client.entity.PlayerStatistic
import java.util.Locale
import java.util.concurrent.Executors

class OnlinePlayersActivity : AppCompatActivity(), View.OnClickListener, AdapterView.OnItemClickListener {

    private lateinit var lblTitle: TextView
    private lateinit var lblTotalOnline: TextView
    private lateinit var lwOnlinePlayers: ListView
    private lateinit var btnBack: Button

    private lateinit var player: Player
    private var listOnline: ArrayList<PlayerStatistic> = ArrayList()

    private val challengeClientController = ChallengeClientController()
    private val playerClientController = PlayerClientController()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var adapter: OnlinePlayerAdapter
    private var activeChallengeDialog: ChallengeDialog? = null
    private var lastShownIncomingMatchId: Int = -1

    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchOnlinePlayersAndIncoming()
            mainHandler.postDelayed(this, 2500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.onlineplayers)

        val resolvedPlayer = (intent.getSerializableExtra(EXTRA_PLAYER) as? Player)
            ?: SessionManager.getPlayer(this)
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

        adapter = OnlinePlayerAdapter()
        lwOnlinePlayers.adapter = adapter
        lwOnlinePlayers.onItemClickListener = this
    }

    override fun onResume() {
        super.onResume()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(refreshRunnable)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnBack -> finish()
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (position !in listOnline.indices) return
        val selected = listOnline[position]
        if (selected.onlineStatus.equals("AVAILABLE", ignoreCase = true)) {
            openChallengeConfirmDialog(selected)
        }
    }

    private fun openChallengeConfirmDialog(selected: PlayerStatistic) {
        if (selected.id == player.id) return
        if (!selected.onlineStatus.equals("AVAILABLE", ignoreCase = true)) return
        activeChallengeDialog?.dismiss()
        activeChallengeDialog = ChallengeDialog(this, player, selected).also { it.show() }
    }

    private fun fetchOnlinePlayersAndIncoming() {
        val myId = player.id
        ioExecutor.execute {
            val heartbeat = try {
                playerClientController.sendHeartbeat(myId)
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
                adapter.notifyDataSetChanged()

                val incoming: Match? = heartbeat?.incomingChallenge
                if (incoming != null &&
                    activeChallengeDialog?.isShowing != true &&
                    incoming.id != lastShownIncomingMatchId
                ) {
                    val challenger = incoming.matchParticipant
                        .firstOrNull { it.player?.id != null && it.player?.id != player.id }
                        ?.player
                    if (challenger != null) {
                        lastShownIncomingMatchId = incoming.id
                        activeChallengeDialog = ChallengeDialog(
                            context = this,
                            player = player,
                            opponent = challenger,
                            incomingMatch = incoming
                        ).also { it.show() }
                    }
                }
            }
        }
    }

    private inner class OnlinePlayerAdapter : BaseAdapter() {
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
                    openChallengeConfirmDialog(item)
                }
            } else {
                // Nguoi choi dang trong tran: dong nguoi choi khong active, bam vao khong hien thong bao
                lblStatus.text = "IN MATCH"
                lblStatus.setBackgroundResource(R.drawable.bg_status_inmatch)
                lblStatus.setTextColor(ContextCompat.getColor(this@OnlinePlayersActivity, R.color.kora_ink))
                cardPlayerBox.setOnClickListener(null)
                cardPlayerBox.isClickable = false
                cardPlayerBox.isEnabled = false
            }

            btnViewProfile.setOnClickListener {
                val intent = Intent(this@OnlinePlayersActivity, PlayerProfileActivity::class.java)
                intent.putExtra(PlayerProfileActivity.EXTRA_PLAYER, player)
                intent.putExtra(PlayerProfileActivity.EXTRA_TARGET_PLAYER_ID, item.id)
                intent.putExtra(PlayerProfileActivity.EXTRA_IS_PUBLIC, true)
                startActivity(intent)
            }

            return rowView
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(refreshRunnable)
        activeChallengeDialog?.dismiss()
        ioExecutor.shutdownNow()
    }

    companion object {
        const val EXTRA_PLAYER = "extra_player"
    }
}
