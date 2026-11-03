package com.caro.client.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.caro.client.R
import com.caro.client.controller.AIMatchClientController
import com.caro.client.controller.ApiClient
import com.caro.client.entity.Match
import com.caro.client.entity.Player

class SelectDifficultyActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var player: Player
    private lateinit var client: AIMatchClientController
    private lateinit var btnBeginner: Button
    private lateinit var btnIntermediate: Button
    private lateinit var btnExpert: Button
    private lateinit var btnBack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.selectdifficulty)

        player = (intent.getSerializableExtra("extra_player") as? Player)
            ?: ApiClient.getPlayer(this)
            ?: return finish()
        client = AIMatchClientController()

        btnBeginner = findViewById(R.id.btnBeginner)
        btnIntermediate = findViewById(R.id.btnIntermediate)
        btnExpert = findViewById(R.id.btnExpert)
        btnBack = findViewById(R.id.btnBack)
        listOf(btnBeginner, btnIntermediate, btnExpert, btnBack).forEach {
            it.setOnClickListener(this)
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnBack -> finish()
            R.id.btnBeginner -> createMatch("beginner")
            R.id.btnIntermediate -> createMatch("intermediate")
            R.id.btnExpert -> createMatch("expert")
        }
    }

    private fun createMatch(difficulty: String) {
        listOf(btnBeginner, btnIntermediate, btnExpert).forEach { it.isEnabled = false }
        Thread {
            val match = client.createMatch(player.id, difficulty)
            runOnUiThread {
                listOf(btnBeginner, btnIntermediate, btnExpert).forEach { it.isEnabled = true }
                if (match == null || isFinishing || isDestroyed) {
                    ApiClient.showNotice(
                        this,
                        "AI MATCH",
                        "Could not create the match. Please try again.",
                        primaryText = "OK"
                    )
                    return@runOnUiThread
                }
                startActivity(Intent(this, MatchActivity::class.java).apply {
                    putExtra("extra_player", player)
                    putExtra("extra_match", match)
                })
                finish()
            }
        }.start()
    }
}
