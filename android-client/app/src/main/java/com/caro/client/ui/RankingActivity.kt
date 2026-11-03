package com.caro.client.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.caro.client.R
import com.caro.client.controller.ApiClient
import com.caro.client.controller.RankingClientController
import com.caro.client.entity.Player
import com.caro.client.entity.PlayerStatistic
import java.util.Locale

class RankingActivity : AppCompatActivity(), View.OnClickListener, AdapterView.OnItemClickListener {

    private lateinit var lblTotalPlayers: TextView
    private lateinit var lblRankingEmpty: TextView
    private lateinit var lwRanking: ListView
    private lateinit var btnBack: Button
    private lateinit var player: Player
    private var ranking: ArrayList<PlayerStatistic> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ranking)

        val resolvedPlayer = (intent.getSerializableExtra("extra_player") as? Player)
            ?: ApiClient.getPlayer(this)
        if (resolvedPlayer == null) {
            finish()
            return
        }
        player = resolvedPlayer

        lblTotalPlayers = findViewById(R.id.lblTotalPlayers)
        lblRankingEmpty = findViewById(R.id.lblRankingEmpty)
        lwRanking = findViewById(R.id.lwRanking)
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener(this)
        lwRanking.onItemClickListener = this

        lwRanking.adapter = object : BaseAdapter() {
            override fun getCount(): Int = ranking.size
            override fun getItem(position: Int): PlayerStatistic = ranking[position]
            override fun getItemId(position: Int): Long = getItem(position).id.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = convertView ?: LayoutInflater.from(this@RankingActivity)
                    .inflate(R.layout.item_ranking, parent, false)
                val item = getItem(position)

                row.findViewById<TextView>(R.id.lblRankingRank).text = "#${item.rank}"
                row.findViewById<TextView>(R.id.lblRankingPlayer).text =
                    item.fullName.ifBlank { item.gameID }.ifBlank { "PLAYER #${item.id}" }

                val scoreText = if (item.totalScore % 1.0 == 0.0) {
                    item.totalScore.toLong().toString()
                } else {
                    String.format(Locale.US, "%.1f", item.totalScore)
                }
                row.findViewById<TextView>(R.id.lblRankingScore).text = "$scoreText PT"
                row.findViewById<TextView>(R.id.lblRankingStats).text =
                    "W ${item.wins}  D ${item.draws}  L ${item.losses}  MATCHES ${item.totalMatches}"
                return row
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Thread {
            val fetched = try {
                RankingClientController().getRanking()
            } catch (_: Exception) {
                ArrayList()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                ranking = fetched
                lblTotalPlayers.text = "TOTAL PLAYERS: ${ranking.size}"
                lwRanking.visibility = if (ranking.isEmpty()) View.GONE else View.VISIBLE
                lblRankingEmpty.visibility = if (ranking.isEmpty()) View.VISIBLE else View.GONE
                (lwRanking.adapter as? BaseAdapter)?.notifyDataSetChanged()
            }
        }.start()
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val selected = ranking.getOrNull(position) ?: return
        val intent = Intent(this, PlayerProfileActivity::class.java)
        intent.putExtra("extra_player", player)
        intent.putExtra("extra_target_player_id", selected.id)
        intent.putExtra("extra_is_public", true)
        startActivity(intent)
    }

    override fun onClick(v: View?) {
        if (v?.id == R.id.btnBack) finish()
    }
}
