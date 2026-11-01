package com.caro.client.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
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
    private lateinit var txtSearch: EditText
    private lateinit var lwOnlinePlayers: ListView
    private lateinit var btnBack: Button
    private lateinit var btnRefreshOnline: Button

    private lateinit var player: Player
    private var listOnline: ArrayList<PlayerStatistic> = ArrayList()
    private var filteredOnline: ArrayList<PlayerStatistic> = ArrayList()

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
            mainHandler.postDelayed(this, 3000L)
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
        txtSearch = findViewById(R.id.txtSearch)
        lwOnlinePlayers = findViewById(R.id.lwOnlinePlayers)
        btnBack = findViewById(R.id.btnBack)
        btnRefreshOnline = findViewById(R.id.btnRefreshOnline)

        btnBack.setOnClickListener(this)
        btnRefreshOnline.setOnClickListener(this)

        adapter = OnlinePlayerAdapter()
        lwOnlinePlayers.adapter = adapter
        lwOnlinePlayers.onItemClickListener = this

        txtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applySearchFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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
            R.id.btnRefreshOnline -> fetchOnlinePlayersAndIncoming()
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (position !in filteredOnline.indices) return
        val selected = filteredOnline[position]

        if (selected.id == player.id) {
            KoraNoticeDialog.show(
                context = this,
                title = "NOTICE",
                message = "This is your account (${selected.gameID}). Choose another online player to challenge!",
                tone = KoraNoticeDialog.Tone.INFO
            )
            return
        }

        if (selected.onlineStatus.equals("AVAILABLE", ignoreCase = true)) {
            activeChallengeDialog?.dismiss()
            activeChallengeDialog = ChallengeDialog(this, player, selected).also { it.show() }
        } else {
            KoraNoticeDialog.show(
                context = this,
                title = "PLAYER UNAVAILABLE",
                message = "Player '${selected.gameID}' is currently in another match. Please challenge an AVAILABLE player.",
                tone = KoraNoticeDialog.Tone.WARNING
            )
        }
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

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                listOnline = online
                lblTotalOnline.text = "TOTAL ONLINE PLAYERS: ${listOnline.size}"
                applySearchFilter()

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

    private fun applySearchFilter() {
        val query = txtSearch.text.toString().trim().lowercase(Locale.US)
        filteredOnline = if (query.isEmpty()) {
            ArrayList(listOnline)
        } else {
            ArrayList(
                listOnline.filter {
                    it.gameID.lowercase(Locale.US).contains(query) ||
                        it.fullName.lowercase(Locale.US).contains(query)
                }
            )
        }
        adapter.notifyDataSetChanged()
    }

    private inner class OnlinePlayerAdapter : BaseAdapter() {
        override fun getCount(): Int = filteredOnline.size
        override fun getItem(position: Int): PlayerStatistic = filteredOnline[position]
        override fun getItemId(position: Int): Long = filteredOnline[position].id.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val rowView = convertView ?: LayoutInflater.from(this@OnlinePlayersActivity)
                .inflate(R.layout.item_online_player, parent, false)

            val item = getItem(position)
            rowView.setBackgroundResource(
                if (position % 2 == 1) R.drawable.bg_box_zebra else R.drawable.bg_box_outline
            )
            val lblIndex = rowView.findViewById<TextView>(R.id.lblIndex)
            val lblGameID = rowView.findViewById<TextView>(R.id.lblItemGameID)
            val lblStats = rowView.findViewById<TextView>(R.id.lblItemStats)
            val lblStatus = rowView.findViewById<TextView>(R.id.lblItemStatus)
            val btnViewProfile = rowView.findViewById<Button>(R.id.btnViewProfile)

            lblIndex.text = "ID ${position + 1}"
            val youTag = if (item.id == player.id) " (YOU)" else ""
            lblGameID.text = "${item.gameID}$youTag"

            val rankText = if (item.rank > 0) "RANK #${item.rank}" else "RANK #-"
            val ptsText = String.format(Locale.US, "POINTS %.1f", item.totalScore)
            lblStats.text = "$ptsText  ·  $rankText"

            val isAvailable = item.onlineStatus.equals("AVAILABLE", ignoreCase = true)
            if (isAvailable) {
                lblStatus.text = "AVAILABLE"
                lblStatus.setBackgroundColor(ContextCompat.getColor(this@OnlinePlayersActivity, R.color.kora_mint))
            } else {
                lblStatus.text = "IN MATCH"
                lblStatus.setBackgroundColor(ContextCompat.getColor(this@OnlinePlayersActivity, R.color.kora_card))
            }

            btnViewProfile.setOnClickListener {
                val intent = Intent(this@OnlinePlayersActivity, PlayerProfileActivity::class.java)
                intent.putExtra(PlayerProfileActivity.EXTRA_PLAYER, player)
                intent.putExtra(PlayerProfileActivity.EXTRA_TARGET_PLAYER_ID, item.id)
                intent.putExtra(PlayerProfileActivity.EXTRA_IS_PUBLIC, item.id != player.id)
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
