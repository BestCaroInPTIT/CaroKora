package com.caro.client.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.caro.client.R
import com.caro.client.controller.PlayerClientController
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var txtUsername: EditText
    private lateinit var txtPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button

    private val playerClientController = PlayerClientController()
    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.initServerUrl(this)
        setContentView(R.layout.login)

        txtUsername = findViewById(R.id.txtUsername)
        txtPassword = findViewById(R.id.txtPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)

        btnLogin.setOnClickListener(this)
        btnRegister.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnLogin -> handleLoginClick()
            R.id.btnRegister -> {
                // Chuc nang Dang ky khong thuoc pham vi module ca nhan: bam vao khong thuc hien gi va khong hien thong bao
            }
        }
    }

    private fun handleLoginClick() {
        val username = txtUsername.text.toString().trim()
        val password = txtPassword.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            KoraNoticeDialog.show(
                context = this,
                title = "LOGIN FAILED",
                message = "Please enter both username and password!",
                tone = KoraNoticeDialog.Tone.BRICK
            )
            return
        }

        btnLogin.isEnabled = false
        btnLogin.text = "SIGNING IN..."

        ioExecutor.execute {
            val player = try {
                playerClientController.checkLogin(username, password)
            } catch (_: Exception) {
                null
            }

            runOnUiThread {
                btnLogin.isEnabled = true
                btnLogin.text = "LOGIN"

                if (player != null) {
                    // Luu mat khau phien hien tai de dong bo voi thuc the Player khi doi mat khau
                    player.setPassword(password)
                    SessionManager.savePlayer(this, player)

                    val intent = Intent(this, PlayerHomeActivity::class.java)
                    intent.putExtra(PlayerHomeActivity.EXTRA_PLAYER, player)
                    startActivity(intent)
                    finish()
                } else {
                    KoraNoticeDialog.show(
                        context = this,
                        title = "LOGIN FAILED",
                        message = "Invalid username or password!",
                        tone = KoraNoticeDialog.Tone.BRICK
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdownNow()
    }
}
