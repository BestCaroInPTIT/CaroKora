package com.caro.client.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.caro.client.R
import com.caro.client.controller.ApiClient
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

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                findOpponentDialog?.dismiss()
            }
        })
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnPlayAgainstHuman -> {
                findOpponentDialog?.dismiss()
                findOpponentDialog = FindOpponentDialog(this, player).also { it.show() }
            }
            R.id.btnPlayAgainstAI -> {
                // Ngoai pham vi chuc nang ca nhan: bam vao khong thuc hien gi
            }
            R.id.btnBack -> finish()
        }
    }
}
