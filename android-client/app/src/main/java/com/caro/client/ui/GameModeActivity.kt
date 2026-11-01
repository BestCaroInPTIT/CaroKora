package com.caro.client.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.caro.client.R
import com.caro.client.entity.Player

class GameModeActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var lblTitle: TextView
    private lateinit var btnPlayAgainstHuman: Button
    private lateinit var btnPlayAgainstAI: Button
    private lateinit var btnBack: Button

    private lateinit var player: Player
    private var findOpponentDialog: FindOpponentDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gamemode)

        val resolvedPlayer = (intent.getSerializableExtra(EXTRA_PLAYER) as? Player)
            ?: SessionManager.getPlayer(this)
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
                findOpponentDialog?.dismiss()
                findOpponentDialog = FindOpponentDialog(this, player).also { it.show() }
            }
            R.id.btnPlayAgainstAI -> {
                // Diem tich hop cho thanh vien Nguyen Manh Duc (Choi voi may / AI): bam vao khong hien thong bao
            }
            R.id.btnBack -> finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        findOpponentDialog?.dismiss()
    }

    companion object {
        const val EXTRA_PLAYER = "extra_player"
    }
}
