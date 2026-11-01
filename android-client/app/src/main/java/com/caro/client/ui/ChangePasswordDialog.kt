package com.caro.client.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
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
    private var isSubmitting = false

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

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateConfirmButtonState()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        txtOldPassword.addTextChangedListener(watcher)
        txtNewPassword.addTextChangedListener(watcher)
        txtConfirmPassword.addTextChangedListener(watcher)

        updateConfirmButtonState()
    }

    override fun onStart() {
        super.onStart()
        val widthPx = (context.resources.displayMetrics.widthPixels * 0.90f).toInt()
        window?.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /**
     * Dung luong nghiep vu Chuc nang 1 (Muc 1.2):
     * Nut CONFIRM chi active khi: da nhap mat khau cu, mat khau moi >= 6 ky tu va khac mat khau cu,
     * xac nhan mat khau moi khop voi mat khau moi.
     */
    private fun updateConfirmButtonState() {
        if (isSubmitting) return
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

        if (oldPass.isEmpty() || newPass.length < 6 || newPass == oldPass || confirmPass != newPass) {
            updateConfirmButtonState()
            return
        }

        isSubmitting = true
        btnConfirm.isEnabled = false
        btnConfirm.alpha = 0.45f

        ioExecutor.execute {
            val success = try {
                playerClientController.changePassword(player.id, oldPass, newPass)
            } catch (_: Exception) {
                false
            }

            mainHandler.post {
                isSubmitting = false
                updateConfirmButtonState()
                if (success) {
                    // Cap nhat lai thuoc tinh mat khau cua thuc the Player
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
                        title = "WRONG PASSWORD",
                        message = "Wrong old password! Enter again.",
                        tone = KoraNoticeDialog.Tone.BRICK,
                        primaryText = "OK"
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
