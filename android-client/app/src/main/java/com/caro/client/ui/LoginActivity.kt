package com.caro.client.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.caro.client.R
import com.caro.client.controller.ApiClient
import com.caro.client.controller.PlayerClientController

class LoginActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var txtUsername: EditText
    private lateinit var txtPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.initServerUrl(this)
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
            R.id.btnLogin -> {
                val username = txtUsername.text.toString().trim()
                val password = txtPassword.text.toString()

                if (username.isEmpty() || password.isEmpty()) {
                    ApiClient.showNotice(
                        context = this,
                        title = "LOGIN FAILED",
                        message = "Please enter both username and password!",
                        headerColorRes = R.color.kora_brick
                    )
                    return
                }

                btnLogin.isEnabled = false
                val playerClientController = PlayerClientController()

                Thread {
                    val player = try {
                        playerClientController.checkLogin(username, password)
                    } catch (_: Exception) {
                        null
                    }

                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        btnLogin.isEnabled = true

                        if (player != null) {
                            player.setPassword(password)
                            ApiClient.savePlayer(this, player)

                            val intent = Intent(this, PlayerHomeActivity::class.java)
                            intent.putExtra("extra_player", player)
                            startActivity(intent)
                            finish()
                        } else {
                            ApiClient.showNotice(
                                context = this,
                                title = "LOGIN FAILED",
                                message = "Invalid username or password!",
                                headerColorRes = R.color.kora_brick
                            )
                        }
                    }
                }.start()
            }
            R.id.btnRegister -> {
                // Ngoai pham vi chuc nang ca nhan: bam vao khong thuc hien gi
            }
        }
    }
}
