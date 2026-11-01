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
import com.caro.client.controller.PlayerClientController
import com.caro.client.entity.Player
import com.caro.client.entity.PlayerStatistic
import java.util.Locale
import java.util.concurrent.Executors

class PlayerProfileActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var lblTitle: TextView
    private lateinit var imgAvatar: ImageView
    private lateinit var lblGameID: TextView
    private lateinit var lblFullName: TextView
    private lateinit var lblEmail: TextView
    private lateinit var lblPassword: TextView
    private lateinit var rowPassword: LinearLayout
    private lateinit var txtBio: EditText
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

    private var player: Player? = null
    private var ps: PlayerStatistic? = null
    private var targetPlayerId: Int = 0
    private var isPublicView: Boolean = false

    private val playerClientController = PlayerClientController()
    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.playerprofile)

        player = (intent.getSerializableExtra(EXTRA_PLAYER) as? Player)
            ?: SessionManager.getPlayer(this)
        isPublicView = intent.getBooleanExtra(EXTRA_IS_PUBLIC, false)
        targetPlayerId = intent.getIntExtra(EXTRA_TARGET_PLAYER_ID, player?.id ?: 0)

        lblTitle = findViewById(R.id.lblTitle)
        imgAvatar = findViewById(R.id.imgAvatar)
        lblGameID = findViewById(R.id.lblGameID)
        lblFullName = findViewById(R.id.lblFullName)
        lblEmail = findViewById(R.id.lblEmail)
        lblPassword = findViewById(R.id.lblPassword)
        rowPassword = findViewById(R.id.rowPassword)
        txtBio = findViewById(R.id.txtBio)
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

        if (isPublicView) {
            lblTitle.text = "PUBLIC PROFILE"
            rowPassword.visibility = View.GONE
            btnChangeAvatar.visibility = View.GONE
            btnChangeBio.visibility = View.GONE
            btnViewMatchHistory.visibility = View.GONE
            txtBio.isEnabled = false
        } else {
            lblTitle.text = "PLAYER PROFILE"
        }

        loadPlayerProfile()
    }

    override fun onResume() {
        super.onResume()
        if (!isPublicView) {
            SessionManager.getPlayer(this)?.let { player = it }
        }
        loadPlayerProfile()
    }

    fun loadPlayerProfile() {
        val idToLoad = if (targetPlayerId > 0) targetPlayerId else (player?.id ?: 0)
        if (idToLoad <= 0) return

        ioExecutor.execute {
            val fetched = try {
                playerClientController.getPlayerProfile(idToLoad)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (fetched != null) {
                    ps = fetched
                    bindProfile(fetched)
                }
            }
        }
    }

    private fun bindProfile(stat: PlayerStatistic) {
        val rankSuffix = if (stat.rank > 0) "  (RANK #${stat.rank})" else ""
        lblGameID.text = "GAME ID: ${stat.gameID}$rankSuffix"
        lblFullName.text = "Full Name: ${stat.fullName}"
        lblEmail.text = "Email: ${stat.email}"
        lblPassword.text = "Password: ******"
        txtBio.setText(stat.bio ?: "")

        lblTotalScore.text = formatDecimal(stat.totalScore)
        lblTotalMatches.text = stat.totalMatches.toString()
        lblWins.text = stat.wins.toString()
        lblDraws.text = stat.draws.toString()
        lblLosses.text = stat.losses.toString()
        lblAvgOpponentScore.text = formatDecimal(stat.averageOpponentScore)
        lblAvgMovesWin.text = formatDecimal(stat.averageMovesInWin)
        lblAvgMovesLoss.text = formatDecimal(stat.averageMovesInLoss)
    }

    private fun formatDecimal(value: Double): String {
        return if (value % 1.0 == 0.0) {
            String.format(Locale.US, "%.1f", value)
        } else {
            String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnChangePassword -> {
                val activePlayer = player ?: return
                ChangePasswordDialog(this, activePlayer) {
                    SessionManager.savePlayer(this, activePlayer)
                    loadPlayerProfile()
                }.show()
            }
            R.id.btnChangeBio -> handleChangeBio()
            R.id.btnChangeAvatar -> {
                KoraNoticeDialog.show(
                    context = this,
                    title = "AVATAR",
                    message = "Default KORA pixel avatar is active for your account.",
                    tone = KoraNoticeDialog.Tone.INFO
                )
            }
            R.id.btnViewMatchHistory -> {
                // Diem tich hop cho thanh vien cung nhom (Nguyen Manh Duc - Chuc nang Lich su dau)
                KoraNoticeDialog.show(
                    context = this,
                    title = "MATCH HISTORY",
                    message = "Chức năng Xem lịch sử đấu cá nhân (MatchHistoryActivity) là điểm ghép nối của thành viên Nguyễn Mạnh Đức.",
                    tone = KoraNoticeDialog.Tone.INFO
                )
            }
            R.id.btnBack -> finish()
        }
    }

    private fun handleChangeBio() {
        val activePlayer = player ?: return
        val newBio = txtBio.text.toString().trim()
        btnChangeBio.isEnabled = false
        ioExecutor.execute {
            val ok = try {
                playerClientController.updateProfile(activePlayer.id, newBio, activePlayer.avatar)
            } catch (_: Exception) {
                false
            }
            runOnUiThread {
                btnChangeBio.isEnabled = true
                if (ok) {
                    activePlayer.bio = newBio
                    SessionManager.savePlayer(this, activePlayer)
                    KoraNoticeDialog.show(
                        context = this,
                        title = "SUCCESS",
                        message = "Your bio has been updated successfully!",
                        tone = KoraNoticeDialog.Tone.SUCCESS
                    )
                } else {
                    KoraNoticeDialog.show(
                        context = this,
                        title = "UPDATE FAILED",
                        message = "Could not update bio. Please try again.",
                        tone = KoraNoticeDialog.Tone.ERROR
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdownNow()
    }

    companion object {
        const val EXTRA_PLAYER = "extra_player"
        const val EXTRA_TARGET_PLAYER_ID = "extra_target_player_id"
        const val EXTRA_IS_PUBLIC = "extra_is_public"
    }
}
