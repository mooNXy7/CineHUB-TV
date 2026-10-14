package br.com.cinehub.tv

import android.graphics.Color
import android.os.Bundle
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

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var status: TextView
    private var sources: List<String> = emptyList()
    private var sourceIndex = 0
    private var channelTitle = "CineHUB TV"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        channelTitle = intent.getStringExtra(EXTRA_TITLE) ?: "CineHUB TV"
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

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URL = "br.com.cinehub.tv.STREAM_URL"
        const val EXTRA_SOURCES = "br.com.cinehub.tv.STREAM_SOURCES"
        const val EXTRA_TITLE = "br.com.cinehub.tv.STREAM_TITLE"
    }
}
