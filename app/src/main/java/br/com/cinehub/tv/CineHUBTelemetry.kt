package br.com.cinehub.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Optional, privacy-first diagnostics. No titles, search terms, stream URLs,
 * raw exceptions, device IDs, or account data are collected.
 */
object CineHUBTelemetry {
    private const val PREFS = "cinehub-privacy"
    private const val ENABLED_KEY = "diagnostics_enabled"
    private const val INSTALLATION_KEY = "diagnostics_installation_id"
    private const val ENDPOINT = "https://qdheeiitzlvynshvkwcs.supabase.co/functions/v1/cinehub-telemetry"
    private const val PUBLISHABLE_KEY = "sb_publishable_AzpaAW6_qoeiBxy4Pywa4g_XDn2up0z"
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var sessionId: String? = null
    @Volatile private var heartbeatRunning = false

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED_KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(ENABLED_KEY, enabled).apply()
        if (enabled) startSession(context.applicationContext) else stopSession()
    }

    fun onAppStart(context: Context) {
        if (isEnabled(context)) startSession(context.applicationContext)
    }

    fun onAppEnd(context: Context) {
        if (isEnabled(context)) send(context.applicationContext, "session_end")
        stopSession()
    }

    private fun startSession(context: Context) {
        if (!isEnabled(context)) return
        if (sessionId == null) sessionId = UUID.randomUUID().toString()
        send(context, "app_open")
        send(context, "session_start")
        if (heartbeatRunning) return
        heartbeatRunning = true
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!heartbeatRunning || !isEnabled(context)) return
                send(context, "heartbeat")
                handler.postDelayed(this, 5 * 60 * 1000L)
            }
        }, 5 * 60 * 1000L)
    }

    private fun stopSession() {
        heartbeatRunning = false
        sessionId = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun send(context: Context, event: String) {
        if (!isEnabled(context)) return
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            var installationId = prefs.getString(INSTALLATION_KEY, null)
            if (installationId.isNullOrBlank()) {
                installationId = UUID.randomUUID().toString()
                prefs.edit().putString(INSTALLATION_KEY, installationId).apply()
            }
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            val payload = JSONObject()
                .put("event_id", UUID.randomUUID().toString())
                .put("event", event)
                .put("app_id", "cinehub-tv")
                .put("platform", "android_tv")
                .put("app_version", version)
                .put("installation_id", installationId)
                .put("session_id", sessionId ?: UUID.randomUUID().toString())
                .put("properties", JSONObject())
            val request = Request.Builder()
                .url(ENDPOINT)
                .header("apikey", PUBLISHABLE_KEY)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { /* Best effort only. */ }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.close()
                }
            })
        } catch (_: Exception) {
            // Diagnostics must never affect startup, navigation, catalog loading, or playback.
        }
    }
}
