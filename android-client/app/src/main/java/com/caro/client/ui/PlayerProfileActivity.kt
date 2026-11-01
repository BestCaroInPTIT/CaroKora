package com.caro.client.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.caro.client.R
import com.caro.client.controller.ApiClient
import com.caro.client.controller.PlayerClientController
import com.caro.client.entity.PlayerStatistic
import java.util.Locale

class PlayerProfileActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var imgAvatar: ImageView
    private lateinit var lblGameID: TextView
    private lateinit var lblFullName: TextView
    private lateinit var lblEmail: TextView
    private lateinit var lblPassword: TextView
    private lateinit var lblBio: EditText
    private lateinit var lblTotalScore: TextView
    private lateinit var lblTotalMatches: TextView
    private lateinit var lblWins: TextView
    private lateinit var lblDraws: TextView
    private lateinit var lblLosses: TextView
    private lateinit var lblAvgOpponentScore: TextView
    private lateinit var lblAvgMovesWin: TextView
    private lateinit var lblAvgMovesLoss: TextView

    private lateinit var btnChangeAvatar: Button
    private lateinit var btnChangePassword: Button
    private lateinit var btnChangeBio: Button
    private lateinit var btnViewMatchHistory: Button
    private lateinit var btnBack: Button

    private var ps: PlayerStatistic? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.playerprofile)

        val isPublicView = intent.getBooleanExtra("extra_is_public", false)
        val lblTitle = findViewById<TextView>(R.id.lblTitle)
        imgAvatar = findViewById(R.id.imgAvatar)
        lblGameID = findViewById(R.id.lblGameID)
        lblFullName = findViewById(R.id.lblFullName)
        lblEmail = findViewById(R.id.lblEmail)
        lblPassword = findViewById(R.id.lblPassword)
        lblBio = findViewById(R.id.txtBio)
        lblTotalScore = findViewById(R.id.lblTotalScore)
        lblTotalMatches = findViewById(R.id.lblTotalMatches)
        lblWins = findViewById(R.id.lblWins)
        lblDraws = findViewById(R.id.lblDraws)
        lblLosses = findViewById(R.id.lblLosses)
        lblAvgOpponentScore = findViewById(R.id.lblAvgOpponentScore)
        lblAvgMovesWin = findViewById(R.id.lblAvgMovesWin)
        lblAvgMovesLoss = findViewById(R.id.lblAvgMovesLoss)

        btnChangeAvatar = findViewById(R.id.btnChangeAvatar)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        btnChangeBio = findViewById(R.id.btnChangeBio)
        btnViewMatchHistory = findViewById(R.id.btnViewMatchHistory)
        btnBack = findViewById(R.id.btnBack)

        btnChangeAvatar.setOnClickListener(this)
        btnChangePassword.setOnClickListener(this)
        btnChangeBio.setOnClickListener(this)
        btnViewMatchHistory.setOnClickListener(this)
        btnBack.setOnClickListener(this)

        lblTitle.text = "PLAYER PROFILE"
        val rowPassword = findViewById<LinearLayout>(R.id.rowPassword)
        if (isPublicView) {
            rowPassword.visibility = View.GONE
            btnChangeAvatar.visibility = View.GONE
            btnChangeBio.visibility = View.GONE
            btnViewMatchHistory.visibility = View.GONE
            btnBack.text = "BACK"
        } else {
            btnBack.text = "< Back"
        }
    }

    public override fun onResume() {
        super.onResume()
        val currentPlayer = ApiClient.getPlayer(this)
        val targetPlayerId = intent.getIntExtra("extra_target_player_id", currentPlayer?.id ?: 0)
        val idToLoad = if (targetPlayerId > 0) targetPlayerId else (currentPlayer?.id ?: 0)
        if (idToLoad <= 0) return

        val playerClientController = PlayerClientController()
        Thread {
            val fetched = try {
                playerClientController.getPlayerProfile(idToLoad)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || fetched == null) return@runOnUiThread
                ps = fetched

                fun fmt(v: Double): String {
                    return if (v % 1.0 == 0.0) {
                        v.toLong().toString()
                    } else {
                        String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
                    }
                }

                lblGameID.text = "GAME ID: ${fetched.gameID}"
                lblFullName.text = "Full Name: ${fetched.fullName}"
                lblEmail.text = "Email: ${fetched.email}"
                lblPassword.text = "Password: ******"
                lblBio.setText(fetched.bio ?: "")

                lblTotalScore.text = fmt(fetched.totalScore)
                lblTotalMatches.text = fetched.totalMatches.toString()
                lblWins.text = fetched.wins.toString()
                lblDraws.text = fetched.draws.toString()
                lblLosses.text = fetched.losses.toString()
                lblAvgOpponentScore.text = fmt(fetched.averageOpponentScore)
                lblAvgMovesWin.text = fmt(fetched.averageMovesInWin)
                lblAvgMovesLoss.text = fmt(fetched.averageMovesInLoss)
            }
        }.start()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnChangePassword -> {
                val activePlayer = ps ?: ApiClient.getPlayer(this) ?: return
                ChangePasswordDialog(this, activePlayer).show()
            }
            R.id.btnChangeBio -> {
                // Ngoai pham vi chuc nang ca nhan: bam vao khong thuc hien gi
            }
            R.id.btnChangeAvatar -> {
                // Ngoai pham vi chuc nang ca nhan: bam vao khong thuc hien gi
            }
            R.id.btnViewMatchHistory -> {
                // Ngoai pham vi chuc nang ca nhan: bam vao khong thuc hien gi
            }
            R.id.btnBack -> finish()
        }
    }
}
