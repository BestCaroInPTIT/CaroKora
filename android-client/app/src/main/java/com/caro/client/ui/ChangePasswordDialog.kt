package com.caro.client.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import com.caro.client.R
import com.caro.client.controller.ApiClient
import com.caro.client.controller.PlayerClientController
import com.caro.client.entity.Player

class ChangePasswordDialog(
    context: Context,
    private val player: Player
) : Dialog(context), View.OnClickListener {

    private var txtOldPassword: EditText
    private var txtNewPassword: EditText
    private var txtConfirmPassword: EditText
    private var btnConfirm: Button
    private var btnCancel: Button

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.changepassword)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        txtOldPassword = findViewById(R.id.txtOldPassword)
        txtNewPassword = findViewById(R.id.txtNewPassword)
        txtConfirmPassword = findViewById(R.id.txtConfirmPassword)
        btnConfirm = findViewById(R.id.btnConfirm)
        btnCancel = findViewById(R.id.btnCancel)

        btnConfirm.setOnClickListener(this)
        btnCancel.setOnClickListener(this)

        btnConfirm.isEnabled = false
        btnConfirm.alpha = 0.45f

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val oldPass = txtOldPassword.text?.toString() ?: ""
                val newPass = txtNewPassword.text?.toString() ?: ""
                val confirmPass = txtConfirmPassword.text?.toString() ?: ""
                val isReady = oldPass.isNotEmpty() &&
                    newPass.length >= 6 &&
                    newPass != oldPass &&
                    confirmPass == newPass
                btnConfirm.isEnabled = isReady
                btnConfirm.alpha = if (isReady) 1.0f else 0.45f
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        txtOldPassword.addTextChangedListener(watcher)
        txtNewPassword.addTextChangedListener(watcher)
        txtConfirmPassword.addTextChangedListener(watcher)

        setOnShowListener {
            val widthPx = (context.resources.displayMetrics.widthPixels * 0.90f).toInt()
            window?.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnConfirm -> {
                val oldPass = txtOldPassword.text.toString()
                val newPass = txtNewPassword.text.toString()
                val confirmPass = txtConfirmPassword.text.toString()
                if (oldPass.isEmpty() || newPass.length < 6 || newPass == oldPass || confirmPass != newPass) {
                    return
                }

                btnConfirm.isEnabled = false
                btnConfirm.alpha = 0.45f
                val playerClientController = PlayerClientController()
                val mainHandler = Handler(Looper.getMainLooper())

                Thread {
                    val success = try {
                        playerClientController.changePassword(player.id, oldPass, newPass)
                    } catch (_: Exception) {
                        false
                    }

                    mainHandler.post {
                        if (!isShowing) return@post
                        val readyNow = txtOldPassword.text.isNotEmpty() &&
                            txtNewPassword.text.length >= 6 &&
                            txtNewPassword.text.toString() != txtOldPassword.text.toString() &&
                            txtConfirmPassword.text.toString() == txtNewPassword.text.toString()
                        btnConfirm.isEnabled = readyNow
                        btnConfirm.alpha = if (readyNow) 1.0f else 0.45f

                        if (success) {
                            player.setPassword(newPass)
                            ApiClient.savePlayer(context, player)
                            ApiClient.showNotice(
                                context = context,
                                title = "PASSWORD UPDATED",
                                message = "You have successfully changed your password!",
                                headerColorRes = R.color.kora_mint,
                                primaryText = "OK",
                                cancelable = false,
                                onPrimary = {
                                    dismiss()
                                    (context as? PlayerProfileActivity)?.onResume()
                                }
                            )
                        } else {
                            ApiClient.showNotice(
                                context = context,
                                title = "WRONG PASSWORD",
                                message = "Wrong old password! Enter again.",
                                headerColorRes = R.color.kora_brick,
                                primaryText = "OK"
                            )
                        }
                    }
                }.start()
            }
            R.id.btnCancel -> dismiss()
        }
    }
}
