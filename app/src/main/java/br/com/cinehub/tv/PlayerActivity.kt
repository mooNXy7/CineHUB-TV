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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val url = intent.getStringExtra(EXTRA_URL)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "CineHUB TV"
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

        if (url.isNullOrBlank() || !(url.startsWith("https://") || url.startsWith("http://"))) {
            status.text = "Endereço de reprodução inválido"
            return
        }

        player = ExoPlayer.Builder(this).build().also { exo ->
            video.player = exo
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    status.visibility = if (playbackState == Player.STATE_READY) View.GONE else View.VISIBLE
                    if (playbackState == Player.STATE_BUFFERING) status.text = "Carregando: $title"
                    if (playbackState == Player.STATE_ENDED) status.text = "Transmissão encerrada"
                }
                override fun onPlayerError(error: PlaybackException) {
                    status.visibility = View.VISIBLE
                    status.text = "Não foi possível reproduzir esta fonte. Tente outra fonte do canal."
                }
            })
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
        const val EXTRA_TITLE = "br.com.cinehub.tv.STREAM_TITLE"
    }
}
