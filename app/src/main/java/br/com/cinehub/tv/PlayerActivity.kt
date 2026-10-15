package br.com.cinehub.tv

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var status: TextView
    private var sources: List<String> = emptyList()
    private var sourceIndex = 0
    private var channelTitle = "CineHUB TV"
    private val historyHandler = Handler(Looper.getMainLooper())
    private var historyResumeApplied = false
    private val historyTick = object : Runnable {
        override fun run() {
            savePlaybackHistory()
            if (player != null && !isFinishing && !isDestroyed) historyHandler.postDelayed(this, 15_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        channelTitle = intent.getStringExtra(EXTRA_PLAYBACK_TITLE) ?: intent.getStringExtra(EXTRA_TITLE) ?: "CineHUB TV"
        val suppliedSources = intent.getStringArrayListExtra(EXTRA_SOURCES)?.toList().orEmpty()
        val legacyUrl = intent.getStringExtra(EXTRA_URL)
        sources = (suppliedSources + listOfNotNull(legacyUrl))
            .filter { it.startsWith("https://") || it.startsWith("http://") }
            .distinct()

        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val video = PlayerView(this).apply {
            useController = true
            controllerAutoShow = true
        }
        status = TextView(this).apply {
            text = "Preparando reprodução…"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xCC101010.toInt())
            gravity = Gravity.CENTER
        }
        frame.addView(video, FrameLayout.LayoutParams(-1, -1))
        frame.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(frame)

        if (sources.isEmpty()) {
            status.text = "Este canal não possui uma fonte válida."
            return
        }

        player = ExoPlayer.Builder(this).build().also { exo ->
            video.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        status.visibility = View.GONE
                        getSharedPreferences("cinehub-tv", MODE_PRIVATE).edit()
                            .putString("continue_channel_name", channelTitle)
                            .apply()
                        savePlaybackHistory()
                        if (!historyResumeApplied) {
                            historyResumeApplied = true
                            val resumeAt = intent.getLongExtra(EXTRA_RESUME_POSITION, 0L)
                            if (resumeAt > 5_000L && resumeAt < (exo.duration.takeIf { it > 0 } ?: Long.MAX_VALUE) - 10_000L) {
                                exo.seekTo(resumeAt)
                            }
                        }
                        historyHandler.removeCallbacks(historyTick)
                        historyHandler.postDelayed(historyTick, 15_000L)
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        status.text = "Carregando " + channelTitle + "…"
                        status.visibility = View.VISIBLE
                    } else if (playbackState == Player.STATE_ENDED) {
                        status.text = "Transmissão encerrada"
                        status.visibility = View.VISIBLE
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    sourceIndex++
                    if (sourceIndex < sources.size) {
                        status.text = "A fonte falhou. Tentando alternativa " + (sourceIndex + 1) + "/" + sources.size + "…"
                        status.visibility = View.VISIBLE
                        exo.setMediaItem(MediaItem.fromUri(sources[sourceIndex]))
                        exo.prepare()
                        exo.playWhenReady = true
                    } else {
                        status.text = "Não foi possível reproduzir este canal. As fontes disponíveis falharam."
                        status.visibility = View.VISIBLE
                    }
                }
            })
            exo.setMediaItem(MediaItem.fromUri(sources[sourceIndex]))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    private fun savePlaybackHistory() {
        val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID)?.takeIf { it.isNotBlank() } ?: return
        val current = player ?: return
        val prefs = getSharedPreferences("cinehub-tv", MODE_PRIVATE)
        val history = try {
            JSONArray(prefs.getString("continue_media_items", "[]") ?: "[]")
        } catch (_: Exception) { JSONArray() }
        val record = JSONObject().apply {
            put("id", mediaId)
            put("title", intent.getStringExtra(EXTRA_TITLE) ?: channelTitle)
            put("playbackTitle", intent.getStringExtra(EXTRA_PLAYBACK_TITLE) ?: channelTitle)
            put("poster", intent.getStringExtra(EXTRA_POSTER) ?: "")
            put("backdrop", intent.getStringExtra(EXTRA_BACKDROP) ?: "")
            put("type", intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: "movie")
            put("year", intent.getStringExtra(EXTRA_YEAR) ?: "")
            put("rating", intent.getStringExtra(EXTRA_RATING) ?: "—")
            put("description", intent.getStringExtra(EXTRA_DESCRIPTION) ?: "")
            put("language", intent.getStringExtra(EXTRA_LANGUAGE) ?: "")
            put("countries", JSONArray(intent.getStringArrayListExtra(EXTRA_COUNTRIES) ?: arrayListOf()))
            put("genreIds", JSONArray(intent.getIntegerArrayListExtra(EXTRA_GENRES) ?: arrayListOf()))
            put("sources", JSONArray(sources))
            put("positionMs", current.currentPosition.coerceAtLeast(0L))
            put("updatedAt", System.currentTimeMillis())
        }
        val updated = JSONArray()
        updated.put(record)
        for (i in 0 until history.length()) {
            val old = history.optJSONObject(i) ?: continue
            if (old.optString("id") != mediaId) updated.put(old)
            if (updated.length() >= 20) break
        }
        prefs.edit().putString("continue_media_items", updated.toString()).apply()
    }

    override fun onStop() {
        historyHandler.removeCallbacks(historyTick)
        savePlaybackHistory()
        super.onStop()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URL = "br.com.cinehub.tv.STREAM_URL"
        const val EXTRA_SOURCES = "br.com.cinehub.tv.STREAM_SOURCES"
        const val EXTRA_TITLE = "br.com.cinehub.tv.STREAM_TITLE"
        const val EXTRA_MEDIA_ID = "br.com.cinehub.tv.MEDIA_ID"
        const val EXTRA_PLAYBACK_TITLE = "br.com.cinehub.tv.PLAYBACK_TITLE"
        const val EXTRA_POSTER = "br.com.cinehub.tv.MEDIA_POSTER"
        const val EXTRA_BACKDROP = "br.com.cinehub.tv.MEDIA_BACKDROP"
        const val EXTRA_MEDIA_TYPE = "br.com.cinehub.tv.MEDIA_TYPE"
        const val EXTRA_YEAR = "br.com.cinehub.tv.MEDIA_YEAR"
        const val EXTRA_RATING = "br.com.cinehub.tv.MEDIA_RATING"
        const val EXTRA_DESCRIPTION = "br.com.cinehub.tv.MEDIA_DESCRIPTION"
        const val EXTRA_LANGUAGE = "br.com.cinehub.tv.MEDIA_LANGUAGE"
        const val EXTRA_COUNTRIES = "br.com.cinehub.tv.MEDIA_COUNTRIES"
        const val EXTRA_GENRES = "br.com.cinehub.tv.MEDIA_GENRES"
        const val EXTRA_RESUME_POSITION = "br.com.cinehub.tv.RESUME_POSITION"
    }
}
