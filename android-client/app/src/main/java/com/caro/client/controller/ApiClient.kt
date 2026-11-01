package com.caro.client.controller

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
import com.caro.client.BuildConfig
import com.caro.client.R
import com.caro.client.entity.Match
import com.caro.client.entity.Player
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

open class ApiClient {
    protected var baseUrl: String = (customBaseUrl ?: BuildConfig.DEFAULT_BASE_URL).trimEnd('/')

    protected val gson: Gson
        get() = sharedGson

    fun <T> getJson(path: String, type: Type): T? {
        val activeBase = (customBaseUrl ?: baseUrl).trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val request = Request.Builder()
            .url(activeBase + cleanPath)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null
            if (bodyStr.isBlank()) return null
            return gson.fromJson(bodyStr, type)
        }
    }

    fun <T> postJson(path: String, payload: Any, responseType: Type): T? {
        val activeBase = (customBaseUrl ?: baseUrl).trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val jsonBody = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(activeBase + cleanPath)
            .post(jsonBody)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code != 200 && response.code != 201) return null
            val bodyStr = response.body?.string() ?: return null
            if (bodyStr.isBlank()) return null
            return gson.fromJson(bodyStr, responseType)
        }
    }

    companion object {
        private const val PREF_NAME = "kora_caro_prefs"
        private const val KEY_PLAYER_JSON = "logged_in_player"
        private const val KEY_SERVER_URL = "custom_server_url"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val sharedGson = Gson()

        private val httpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        @Volatile
        var customBaseUrl: String? = null

        @Volatile
        private var cachedPlayer: Player? = null

        @Volatile
        var pendingIncomingChallenge: Match? = null

        fun consumeIncomingChallenge(): Match? {
            val m = pendingIncomingChallenge
            pendingIncomingChallenge = null
            return m
        }

        fun initServerUrl(context: Context) {
            val defaultUrl = BuildConfig.DEFAULT_BASE_URL.trim().trimEnd('/')
            customBaseUrl = defaultUrl
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_SERVER_URL)
                .apply()
        }

        fun savePlayer(context: Context, player: Player) {
            cachedPlayer = player
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PLAYER_JSON, sharedGson.toJson(player))
                .apply()
        }

        fun getPlayer(context: Context): Player? {
            cachedPlayer?.let { return it }
            val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PLAYER_JSON, null) ?: return null
            return try {
                sharedGson.fromJson(json, Player::class.java).also { cachedPlayer = it }
            } catch (_: Exception) {
                null
            }
        }

        fun clearSession(context: Context) {
            cachedPlayer = null
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PLAYER_JSON)
                .apply()
        }

        fun showNotice(
            context: Context,
            title: String,
            message: String,
            headerColorRes: Int = R.color.kora_sky,
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
            lblTitle.setBackgroundColor(ContextCompat.getColor(context, headerColorRes))
            lblTitle.setTextColor(ContextCompat.getColor(context, R.color.kora_ink))

            btnPrimary.text = primaryText
            btnPrimary.setBackgroundResource(primaryBgRes)
            btnPrimary.setOnClickListener {
                dialog.dismiss()
                onPrimary?.invoke()
            }

            val dm = context.resources.displayMetrics
            val h44 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 44f, dm).toInt()
            val w176 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 176f, dm).toInt()

            if (!secondaryText.isNullOrBlank()) {
                val primaryLp = (btnPrimary.layoutParams as? LinearLayout.LayoutParams)
                    ?: LinearLayout.LayoutParams(0, h44, 1f)
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
                    ?: LinearLayout.LayoutParams(w176, h44)
                primaryLp.width = w176
                primaryLp.weight = 0f
                btnPrimary.layoutParams = primaryLp
                btnSecondary.visibility = View.GONE
            }

            dialog.show()
            val widthPx = (dm.widthPixels * 0.90f).toInt()
            dialog.window?.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            return dialog
        }
    }
}
