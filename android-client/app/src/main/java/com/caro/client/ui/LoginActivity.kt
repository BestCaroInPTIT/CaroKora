package com.caro.client.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.caro.client.R
import com.caro.client.controller.PlayerClientController
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var txtUsername: EditText
    private lateinit var txtPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var btnServerConfig: TextView

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
        btnServerConfig = findViewById(R.id.btnServerConfig)

        btnLogin.setOnClickListener(this)
        btnRegister.setOnClickListener(this)
        btnServerConfig.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnLogin -> handleLoginClick()
            R.id.btnRegister -> showRegisterDialog()
            R.id.btnServerConfig -> showServerUrlDialog()
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
                tone = KoraNoticeDialog.Tone.ERROR
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
                        message = "Invalid username or password, or cannot reach server (${SessionManager.getServerUrl(this)}).",
                        tone = KoraNoticeDialog.Tone.ERROR
                    )
                }
            }
        }
    }

    private fun showRegisterDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_register)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val txtGameId = dialog.findViewById<EditText>(R.id.txtRegGameID)
        val txtFullName = dialog.findViewById<EditText>(R.id.txtRegFullName)
        val txtEmail = dialog.findViewById<EditText>(R.id.txtRegEmail)
        val txtPass = dialog.findViewById<EditText>(R.id.txtRegPassword)
        val txtConfirm = dialog.findViewById<EditText>(R.id.txtRegConfirmPassword)
        val btnSubmit = dialog.findViewById<Button>(R.id.btnRegSubmit)
        val btnCancel = dialog.findViewById<Button>(R.id.btnRegCancel)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSubmit.setOnClickListener {
            val gameId = txtGameId.text.toString().trim()
            val fullName = txtFullName.text.toString().trim()
            val email = txtEmail.text.toString().trim()
            val pass = txtPass.text.toString()
            val confirm = txtConfirm.text.toString()

            if (gameId.isEmpty() || fullName.isEmpty() || email.isEmpty()) {
                KoraNoticeDialog.show(
                    context = this,
                    title = "INVALID INPUT",
                    message = "Please fill in Game ID, Full Name, and Email.",
                    tone = KoraNoticeDialog.Tone.ERROR
                )
                return@setOnClickListener
            }
            if (pass.length < 6) {
                KoraNoticeDialog.show(
                    context = this,
                    title = "INVALID PASSWORD",
                    message = "Password must be at least 6 characters long.",
                    tone = KoraNoticeDialog.Tone.ERROR
                )
                return@setOnClickListener
            }
            if (pass != confirm) {
                KoraNoticeDialog.show(
                    context = this,
                    title = "PASSWORD MISMATCH",
                    message = "Confirm password does not match!",
                    tone = KoraNoticeDialog.Tone.ERROR
                )
                return@setOnClickListener
            }

            btnSubmit.isEnabled = false
            ioExecutor.execute {
                val created = try {
                    playerClientController.registerPlayer(gameId, pass, fullName, email)
                } catch (_: Exception) {
                    null
                }
                runOnUiThread {
                    btnSubmit.isEnabled = true
                    if (created != null) {
                        dialog.dismiss()
                        txtUsername.setText(gameId)
                        txtPassword.setText(pass)
                        KoraNoticeDialog.show(
                            context = this,
                            title = "SUCCESS",
                            message = "Account '$gameId' created successfully! Click LOGIN to continue.",
                            tone = KoraNoticeDialog.Tone.SUCCESS
                        )
                    } else {
                        KoraNoticeDialog.show(
                            context = this,
                            title = "REGISTER FAILED",
                            message = "Game ID '$gameId' already exists or server error.",
                            tone = KoraNoticeDialog.Tone.ERROR
                        )
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showServerUrlDialog() {
        val input = EditText(this).apply {
            setText(SessionManager.getServerUrl(this@LoginActivity))
            hint = "https://caro-game-server.onrender.com"
            setPadding(36, 28, 36, 28)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Configure Server URL")
            .setMessage("Emulator Local: http://10.0.2.2:8080\nRender Cloud: https://<your-service>.onrender.com")
            .setView(container)
            .setPositiveButton("SAVE") { _, _ ->
                val url = input.text.toString().trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    SessionManager.setServerUrl(this, url)
                    KoraNoticeDialog.show(
                        context = this,
                        title = "SERVER UPDATED",
                        message = "Active API URL:\n${SessionManager.getServerUrl(this)}",
                        tone = KoraNoticeDialog.Tone.SUCCESS
                    )
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdownNow()
    }
}
