package com.caro.client.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.caro.client.R

object KoraNoticeDialog {

    enum class Tone {
        SUCCESS,
        WARNING,
        ERROR,
        INFO
    }

    fun show(
        context: Context,
        title: String,
        message: String,
        tone: Tone = Tone.INFO,
        primaryText: String = "OK",
        onPrimary: (() -> Unit)? = null,
        secondaryText: String? = null,
        onSecondary: (() -> Unit)? = null,
        cancelable: Boolean = true
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_notice)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(cancelable)

        val lblTitle = dialog.findViewById<TextView>(R.id.lblNoticeTitle)
        val lblMessage = dialog.findViewById<TextView>(R.id.lblNoticeMessage)
        val btnPrimary = dialog.findViewById<Button>(R.id.btnNoticePrimary)
        val btnSecondary = dialog.findViewById<Button>(R.id.btnNoticeSecondary)

        lblTitle.text = title
        lblMessage.text = message

        val headerColorRes = when (tone) {
            Tone.SUCCESS -> R.color.kora_mint
            Tone.WARNING -> R.color.kora_amber
            Tone.ERROR -> R.color.kora_coral
            Tone.INFO -> R.color.kora_sky
        }
        lblTitle.setBackgroundColor(ContextCompat.getColor(context, headerColorRes))
        lblTitle.setTextColor(ContextCompat.getColor(context, R.color.kora_ink))

        btnPrimary.text = primaryText
        btnPrimary.setOnClickListener {
            dialog.dismiss()
            onPrimary?.invoke()
        }

        if (!secondaryText.isNullOrBlank()) {
            btnSecondary.visibility = View.VISIBLE
            btnSecondary.text = secondaryText
            btnSecondary.setOnClickListener {
                dialog.dismiss()
                onSecondary?.invoke()
            }
        } else {
            btnSecondary.visibility = View.GONE
        }

        dialog.show()
        return dialog
    }
}
