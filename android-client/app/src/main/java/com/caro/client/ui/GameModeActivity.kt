package com.caro.client.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.caro.client.R
import com.caro.client.controller.ApiClient
import com.caro.client.entity.Player
import android.content.Intent

class GameModeActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var lblTitle: TextView
    private lateinit var btnPlayAgainstHuman: Button
    private lateinit var btnPlayAgainstAI: Button
    private lateinit var btnBack: Button

    private lateinit var player: Player

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gamemode)

        val resolvedPlayer = (intent.getSerializableExtra("extra_player") as? Player)
            ?: ApiClient.getPlayer(this)
        if (resolvedPlayer == null) {
            finish()
            return
        }
        player = resolvedPlayer

        lblTitle = findViewById(R.id.lblTitle)
        btnPlayAgainstHuman = findViewById(R.id.btnPlayAgainstHuman)
        btnPlayAgainstAI = findViewById(R.id.btnPlayAgainstAI)
        btnBack = findViewById(R.id.btnBack)

        btnPlayAgainstHuman.setOnClickListener(this)
        btnPlayAgainstAI.setOnClickListener(this)
        btnBack.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnPlayAgainstHuman -> {
                FindOpponentDialog(this, player).show()
            }
            R.id.btnPlayAgainstAI -> {
                startActivity(Intent(this, SelectDifficultyActivity::class.java).apply {
                    putExtra("extra_player", player)
                })
            }
            R.id.btnBack -> finish()
        }
    }
}
