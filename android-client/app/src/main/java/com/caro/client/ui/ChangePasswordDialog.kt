package com.caro.client.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
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
    private var player: Player
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

        btnConfirm.isEnabled = true
        btnConfirm.alpha = 1.0f

        setOnShowListener {
            val widthPx = (context.resources.displayMetrics.widthPixels * 0.90f).toInt()
            window?.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnConfirm -> {
                val oldPass = txtOldPassword.text?.toString() ?: ""
                val newPass = txtNewPassword.text?.toString() ?: ""
                val confirmPass = txtConfirmPassword.text?.toString() ?: ""

                // 6.4. Player A nhap sai dinh dang mat khau moi (< 6 ky tu)
                if (newPass.length < 6) {
                    ApiClient.showNotice(
                        context = context,
                        title = "NOTICE",
                        message = "Invalid new password format! Password must be at least 6 characters long.",
                        headerColorRes = R.color.kora_coral,
                        primaryText = "OK"
                    )
                    return
                }

                // 6.3. Player A nhap mat khau moi trung voi mat khau cu
                if (newPass == oldPass) {
                    ApiClient.showNotice(
                        context = context,
                        title = "NOTICE",
                        message = "New password can not be similar to old password! Enter again.",
                        headerColorRes = R.color.kora_coral,
                        primaryText = "OK"
                    )
                    return
                }

                // 6.2. Player A nhap sai xac nhan mat khau
                if (confirmPass != newPass) {
                    ApiClient.showNotice(
                        context = context,
                        title = "NOTICE",
                        message = "Wrong confirm password! Enter again.",
                        headerColorRes = R.color.kora_coral,
                        primaryText = "OK"
                    )
                    return
                }

                // 6.1. Kiem tra mat khau cu trong
                if (oldPass.isEmpty()) {
                    ApiClient.showNotice(
                        context = context,
                        title = "NOTICE",
                        message = "Wrong old password! Enter again.",
                        headerColorRes = R.color.kora_coral,
                        primaryText = "OK"
                    )
                    return
                }

                btnConfirm.isEnabled = false
                val playerClientController = PlayerClientController()
                val mainHandler = Handler(Looper.getMainLooper())

                Thread {
                    val success = try {
                        playerClientController.changePassword(player.id, oldPass, newPass)
                    } catch (_: Exception) {
                        false
                    }

                    mainHandler.post {
                        btnConfirm.isEnabled = true
                        if (!isShowing) return@post

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
                            // 6.1. Player A nhap sai mat khau cu tren Server
                            ApiClient.showNotice(
                                context = context,
                                title = "NOTICE",
                                message = "Wrong old password! Enter again.",
                                headerColorRes = R.color.kora_coral,
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
