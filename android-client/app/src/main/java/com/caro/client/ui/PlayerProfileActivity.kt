package com.caro.client.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
    private var isEditingBio: Boolean = false

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

        lockBioEditing()
        txtBio.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitBioSilently()
                true
            } else {
                false
            }
        }

        lblTitle.text = "PLAYER PROFILE"
        if (isPublicView) {
            rowPassword.visibility = View.GONE
            btnChangeAvatar.visibility = View.GONE
            btnChangeBio.visibility = View.GONE
            btnViewMatchHistory.visibility = View.GONE
            btnBack.text = "BACK"
        } else {
            btnBack.text = "< Back"
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

    override fun onPause() {
        if (isEditingBio) {
            commitBioSilently()
        }
        super.onPause()
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
        lblGameID.text = "GAME ID: ${stat.gameID}"
        lblFullName.text = "Full Name: ${stat.fullName}"
        lblEmail.text = "Email: ${stat.email}"
        lblPassword.text = "Password: ******"
        if (!isEditingBio) {
            txtBio.setText(stat.bio ?: "")
        }

        lblTotalScore.text = formatStatNumber(stat.totalScore)
        lblTotalMatches.text = stat.totalMatches.toString()
        lblWins.text = stat.wins.toString()
        lblDraws.text = stat.draws.toString()
        lblLosses.text = stat.losses.toString()
        lblAvgOpponentScore.text = formatStatNumber(stat.averageOpponentScore)
        lblAvgMovesWin.text = formatStatNumber(stat.averageMovesInWin)
        lblAvgMovesLoss.text = formatStatNumber(stat.averageMovesInLoss)
    }

    private fun formatStatNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnChangePassword -> {
                if (isEditingBio) commitBioSilently()
                val activePlayer = player ?: return
                ChangePasswordDialog(this, activePlayer) {
                    SessionManager.savePlayer(this, activePlayer)
                    loadPlayerProfile()
                }.show()
            }
            R.id.btnChangeBio -> toggleBioEditing()
            R.id.btnChangeAvatar -> {
                // Khong thuoc pham vi chuc nang ca nhan: bam vao khong thuc hien gi va khong hien thong bao
            }
            R.id.btnViewMatchHistory -> {
                // Diem tich hop cho thanh vien Nguyen Manh Duc (MatchHistoryActivity): bam vao khong hien thong bao
            }
            R.id.btnBack -> {
                if (isEditingBio) commitBioSilently()
                finish()
            }
        }
    }

    private fun lockBioEditing() {
        isEditingBio = false
        txtBio.isCursorVisible = false
        txtBio.isFocusable = false
        txtBio.isFocusableInTouchMode = false
    }

    private fun toggleBioEditing() {
        if (isPublicView) return
        if (!isEditingBio) {
            isEditingBio = true
            txtBio.isFocusableInTouchMode = true
            txtBio.isFocusable = true
            txtBio.isCursorVisible = true
            txtBio.requestFocus()
            txtBio.setSelection(txtBio.text?.length ?: 0)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(txtBio, InputMethodManager.SHOW_IMPLICIT)
        } else {
            commitBioSilently()
        }
    }

    private fun commitBioSilently() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(txtBio.windowToken, 0)
        txtBio.clearFocus()
        lockBioEditing()

        val activePlayer = player ?: return
        val newBio = txtBio.text.toString().trim()
        if (newBio == (activePlayer.bio ?: "")) return
        activePlayer.bio = newBio
        SessionManager.savePlayer(this, activePlayer)

        Thread {
            try {
                playerClientController.updateProfile(activePlayer.id, newBio, activePlayer.avatar)
            } catch (_: Exception) {
            }
        }.start()
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
