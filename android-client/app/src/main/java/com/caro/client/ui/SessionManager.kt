package com.caro.client.ui

import android.content.Context
import com.caro.client.BuildConfig
import com.caro.client.controller.ApiClient
import com.caro.client.entity.Player
import com.google.gson.Gson

object SessionManager {
    private const val PREF_NAME = "kora_caro_prefs"
    private const val KEY_PLAYER_JSON = "logged_in_player"
    private const val KEY_SERVER_URL = "custom_server_url"
    private val gson = Gson()

    @Volatile
    private var cachedPlayer: Player? = null

    fun initServerUrl(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_SERVER_URL, null)
        if (!savedUrl.isNullOrBlank()) {
            ApiClient.customBaseUrl = savedUrl.trimEnd('/')
        }
    }

    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?: ApiClient.customBaseUrl
            ?: BuildConfig.DEFAULT_BASE_URL
    }

    fun setServerUrl(context: Context, url: String) {
        val cleaned = url.trim().trimEnd('/')
        ApiClient.customBaseUrl = cleaned
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, cleaned)
            .apply()
    }

    fun savePlayer(context: Context, player: Player) {
        cachedPlayer = player
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLAYER_JSON, gson.toJson(player))
            .apply()
    }

    fun getPlayer(context: Context): Player? {
        cachedPlayer?.let { return it }
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PLAYER_JSON, null) ?: return null
        return try {
            gson.fromJson(json, Player::class.java).also { cachedPlayer = it }
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
}
