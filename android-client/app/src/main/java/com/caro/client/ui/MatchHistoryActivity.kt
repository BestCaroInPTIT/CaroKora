package com.caro.client.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.caro.client.R
import com.caro.client.controller.MatchHistoryClientController
import com.caro.client.entity.Match
import com.caro.client.entity.Player
import java.util.Locale

class MatchHistoryActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var lblTotalMatches: TextView
    private lateinit var lwMatchHistory: ListView
    private lateinit var btnBack: Button
    private var player: Player? = null
    private var history: ArrayList<Match> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.matchhistory)

        player = intent.getSerializableExtra("extra_player") as? Player
        lblTotalMatches = findViewById(R.id.lblTotalMatches)
        lwMatchHistory = findViewById(R.id.lwMatchHistory)
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener(this)

        lwMatchHistory.adapter = object : BaseAdapter() {
            override fun getCount(): Int = history.size
            override fun getItem(position: Int): Match = history[position]
            override fun getItemId(position: Int): Long = getItem(position).id.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = convertView ?: LayoutInflater.from(this@MatchHistoryActivity)
                    .inflate(R.layout.item_match_history, parent, false)
                val match = getItem(position)
                val currentId = player?.id ?: 0
                val mine = match.matchParticipant.firstOrNull { it.player?.id == currentId }
                val opponent = match.matchParticipant.firstOrNull { it.player?.id != currentId && it.player != null }
                    ?: match.matchParticipant.firstOrNull { it.participantType == "AI" }

                val opponentName = when {
                    opponent?.player != null -> opponent.player!!.fullName.ifBlank { opponent.player!!.gameID }
                    opponent?.participantType == "AI" -> "AI (${opponent.aiDifficulty ?: "MEDIUM"})"
                    else -> "UNKNOWN"
                }
                row.findViewById<TextView>(R.id.lblHistoryMatchId).text = "MATCH #${match.id}"
                row.findViewById<TextView>(R.id.lblHistoryOpponent).text = "VS $opponentName"
                row.findViewById<TextView>(R.id.lblHistoryMode).text = match.mode.uppercase(Locale.US)
                row.findViewById<TextView>(R.id.lblHistoryResult).text = mine?.outcome?.uppercase(Locale.US) ?: match.status
                row.findViewById<TextView>(R.id.lblHistoryMoves).text = "${match.move.size} MOVES"
                row.findViewById<TextView>(R.id.lblHistoryDate).text = match.endedAt ?: match.startedAt ?: "--"

                row.setOnClickListener {
                    val intent = Intent(this@MatchHistoryActivity, MatchDetailActivity::class.java)
                    intent.putExtra("extra_player", player)
                    intent.putExtra("extra_match_id", match.id)
                    startActivity(intent)
                }
                return row
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val playerId = player?.id ?: com.caro.client.controller.ApiClient.getPlayer(this)?.id ?: 0
        if (playerId <= 0) return
        Thread {
            val controller = MatchHistoryClientController()
            val fetched = controller.getMatchHistory(playerId)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                history = fetched
                lblTotalMatches.text = "TOTAL MATCHES: ${history.size}"
                (lwMatchHistory.adapter as? BaseAdapter)?.notifyDataSetChanged()
            }
        }.start()
    }

    override fun onClick(v: View?) {
        if (v?.id == R.id.btnBack) finish()
    }
}
