package com.caro.client.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import com.caro.client.R
import com.caro.client.controller.PlayerClientController
import com.caro.client.entity.Player
import java.util.concurrent.Executors

class ChangePasswordDialog @JvmOverloads constructor(
    context: Context,
    private val player: Player,
    private val onPasswordUpdated: (() -> Unit)? = null
) : Dialog(context), View.OnClickListener {

    private lateinit var txtOldPassword: EditText
    private lateinit var txtNewPassword: EditText
    private lateinit var txtConfirmPassword: EditText
    private lateinit var btnConfirm: Button
    private lateinit var btnCancel: Button

    private val playerClientController = PlayerClientController()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnConfirm -> handleConfirmChangePassword()
            R.id.btnCancel -> dismiss()
        }
    }

    private fun handleConfirmChangePassword() {
        val oldPass = txtOldPassword.text.toString()
        val newPass = txtNewPassword.text.toString()
        val confirmPass = txtConfirmPassword.text.toString()

        // Kiem tra dinh dang mat khau moi >= 6 ky tu theo dung thiet ke giao dien 4.1.1
        if (newPass.length < 6) {
            KoraNoticeDialog.show(
                context = context,
                title = "CHANGE PASSWORD FAILED",
                message = "Invalid new password format! Password must be at least 6 characters long.",
                tone = KoraNoticeDialog.Tone.ERROR
            )
            return
        }

        // Kiem tra mat khau moi trung mat khau cu
        if (newPass == oldPass) {
            KoraNoticeDialog.show(
                context = context,
                title = "CHANGE PASSWORD FAILED",
                message = "New password can not be similar to old password! Enter again.",
                tone = KoraNoticeDialog.Tone.ERROR
            )
            return
        }

        // Kiem tra xac nhan mat khau moi
        if (confirmPass != newPass) {
            KoraNoticeDialog.show(
                context = context,
                title = "CHANGE PASSWORD FAILED",
                message = "Wrong confirm password! Enter again.",
                tone = KoraNoticeDialog.Tone.ERROR
            )
            return
        }

        btnConfirm.isEnabled = false
        ioExecutor.execute {
            val success = try {
                playerClientController.changePassword(player.id, oldPass, newPass)
            } catch (_: Exception) {
                false
            }

            mainHandler.post {
                btnConfirm.isEnabled = true
                if (success) {
                    // Buoc 54-55 trong bieu do tuan tu: cap nhat lai thuoc tinh mat khau cua thuc the Player
                    player.setPassword(newPass)
                    KoraNoticeDialog.show(
                        context = context,
                        title = "PASSWORD UPDATED",
                        message = "You have successfully changed your password!",
                        tone = KoraNoticeDialog.Tone.SUCCESS,
                        primaryText = "OK",
                        cancelable = false,
                        onPrimary = {
                            dismiss()
                            onPasswordUpdated?.invoke()
                        }
                    )
                } else {
                    KoraNoticeDialog.show(
                        context = context,
                        title = "CHANGE PASSWORD FAILED",
                        message = "Wrong old password! Enter again.",
                        tone = KoraNoticeDialog.Tone.ERROR
                    )
                }
            }
        }
    }

    override fun dismiss() {
        super.dismiss()
        ioExecutor.shutdownNow()
    }
}
