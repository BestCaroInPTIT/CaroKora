package com.caro.client.controller

import com.caro.client.BuildConfig
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

    protected fun getEndpoint(path: String): String {
        val activeBase = (customBaseUrl ?: baseUrl).trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return activeBase + cleanPath
    }

    protected fun <T> getJson(path: String, type: Type): T? {
        val request = Request.Builder()
            .url(getEndpoint(path))
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null
            if (bodyStr.isBlank()) return null
            return gson.fromJson(bodyStr, type)
        }
    }

    protected fun <T> postJson(path: String, payload: Any, responseType: Type): T? {
        val jsonBody = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(getEndpoint(path))
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
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val sharedGson = Gson()

        // Timeout 45s de chiu duoc cold-start cua Render Free neu server vua ngu
        private val httpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        @Volatile
        var customBaseUrl: String? = null
    }
}
