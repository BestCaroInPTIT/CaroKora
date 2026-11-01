package com.caro.client.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.caro.client.R

object KoraNoticeDialog {

    enum class Tone {
        SUCCESS,
        WARNING,
        ERROR,
        BRICK,
        INFO
    }

    fun show(
        context: Context,
        title: String,
        message: String,
        tone: Tone = Tone.INFO,
        primaryText: String = "OK",
        primaryBgRes: Int = R.drawable.bg_btn_mint,
        onPrimary: (() -> Unit)? = null,
        secondaryText: String? = null,
        secondaryBgRes: Int = R.drawable.bg_btn_blush,
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
            Tone.BRICK -> R.color.kora_brick
            Tone.INFO -> R.color.kora_sky
        }
        lblTitle.setBackgroundColor(ContextCompat.getColor(context, headerColorRes))
        lblTitle.setTextColor(ContextCompat.getColor(context, R.color.kora_ink))

        btnPrimary.text = primaryText
        btnPrimary.setBackgroundResource(primaryBgRes)
        btnPrimary.setOnClickListener {
            dialog.dismiss()
            onPrimary?.invoke()
        }

        val dm = context.resources.displayMetrics
        if (!secondaryText.isNullOrBlank()) {
            val primaryLp = (btnPrimary.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(0, dpToPx(context, 44), 1f)
            primaryLp.width = 0
            primaryLp.weight = 1f
            btnPrimary.layoutParams = primaryLp

            btnSecondary.visibility = View.VISIBLE
            btnSecondary.text = secondaryText
            btnSecondary.setBackgroundResource(secondaryBgRes)
            btnSecondary.setOnClickListener {
                dialog.dismiss()
                onSecondary?.invoke()
            }
        } else {
            val primaryLp = (btnPrimary.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(dpToPx(context, 176), dpToPx(context, 44))
            primaryLp.width = dpToPx(context, 176)
            primaryLp.weight = 0f
            btnPrimary.layoutParams = primaryLp

            btnSecondary.visibility = View.GONE
        }

        dialog.show()
        val widthPx = (dm.widthPixels * 0.90f).toInt()
        dialog.window?.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
