package com.caro.client.ui

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
import com.caro.client.entity.Move
import com.caro.client.entity.Player
import java.util.Locale

class MatchDetailActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var lblMatchTitle: TextView
    private lateinit var lblOpponentName: TextView
    private lateinit var lblMatchResult: TextView
    private lateinit var lblMatchDate: TextView
    private lateinit var lwMoveHistory: ListView
    private lateinit var btnBack: Button
    private var player: Player? = null
    private var match: Match? = null
    private var moves: ArrayList<Move> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.matchdetail)

        player = intent.getSerializableExtra("extra_player") as? Player
            ?: com.caro.client.controller.ApiClient.getPlayer(this)
        lblMatchTitle = findViewById(R.id.lblMatchTitle)
        lblOpponentName = findViewById(R.id.lblOpponentName)
        lblMatchResult = findViewById(R.id.lblMatchResult)
        lblMatchDate = findViewById(R.id.lblMatchDate)
        lwMoveHistory = findViewById(R.id.lwMoveHistory)
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener(this)

        lwMoveHistory.adapter = object : BaseAdapter() {
            override fun getCount(): Int = moves.size
            override fun getItem(position: Int): Move = moves[position]
            override fun getItemId(position: Int): Long = getItem(position).id.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = convertView ?: LayoutInflater.from(this@MatchDetailActivity)
                    .inflate(R.layout.item_move_history, parent, false)
                val move = getItem(position)
                row.setBackgroundResource(
                    if (position % 2 == 1) R.drawable.bg_move_row_zebra else R.drawable.bg_move_row_white
                )
                row.findViewById<TextView>(R.id.lblMoveOrder).text = move.moveOrder.toString()
                row.findViewById<TextView>(R.id.lblMoveTime).text = move.createdAt ?: "--:--:--"
                row.findViewById<TextView>(R.id.lblMoveTurn).text =
                    move.matchParticipant?.player?.gameID ?: (move.matchParticipant?.side ?: "-")
                row.findViewById<TextView>(R.id.lblMoveCoord).text =
                    "${move.row.uppercase(Locale.US)}${move.column}"
                return row
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val matchId = intent.getIntExtra("extra_match_id", 0)
        val playerId = player?.id ?: 0
        if (matchId <= 0 || playerId <= 0) return
        Thread {
            val controller = MatchHistoryClientController()
            val detail = controller.getMatchDetail(playerId, matchId)
            val moveHistory = controller.getMoveHistory(playerId, matchId)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                match = detail ?: match
                moves = if (detail != null) detail.move else moveHistory
                val current = match ?: return@runOnUiThread
                val mine = current.matchParticipant.firstOrNull { it.player?.id == playerId }
                val opponent = current.matchParticipant.firstOrNull {
                    it.player?.id != playerId && (it.player != null || it.participantType == "AI")
                }
                val opponentName = when {
                    opponent?.player != null -> opponent.player!!.fullName.ifBlank { opponent.player!!.gameID }
                    opponent?.participantType == "AI" -> "AI (${opponent.aiDifficulty ?: "MEDIUM"})"
                    else -> "UNKNOWN"
                }

                lblMatchTitle.text = "${current.mode.uppercase(Locale.US)} MATCH #${current.id}"
                lblOpponentName.text = "OPPONENT: $opponentName"
                lblMatchResult.text = "RESULT: ${mine?.outcome?.uppercase(Locale.US) ?: current.status}"
                lblMatchDate.text = "ENDED: ${current.endedAt ?: current.startedAt ?: "--"}"
                (lwMoveHistory.adapter as? BaseAdapter)?.notifyDataSetChanged()
            }
        }.start()
    }

    override fun onClick(v: View?) {
        if (v?.id == R.id.btnBack) finish()
    }
}
