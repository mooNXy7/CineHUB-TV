package com.cinehub.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.sin

/**
 * Dedicated audio-only screen for CineHUB radio streams.
 * It intentionally contains no PlayerView/video surface or film-specific controls.
 */
class RadioExperienceView(
    context: Context,
    stationName: String,
    genre: String,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onChangeStation: () -> Unit
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val soundArt = RadioSoundArt(context)
    private val statusText = TextView(context)
    private val stationNameText = TextView(context)
    private val genreText = TextView(context)
    private val playPauseButton = Button(context)

    init {
        val white = 0xFFF7F7F7.toInt()
        val muted = 0xFFB8B8C2.toInt()
        val red = 0xFFE50914.toInt()
        setBackground(GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF08080B.toInt(), 0xFF1A080D.toInt(), 0xFF09090D.toInt())
        ))
        isFocusable = true
        contentDescription = "Tela de rádio CineHUB"

        val brand = TextView(context).apply {
            text = "CineHUB  /  RÁDIO"
            setTextColor(white)
            textSize = 16f
            letterSpacing = 0.12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        addView(brand, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            setMargins(dp(44), dp(28), 0, 0)
        })

        val backButton = Button(context).apply {
            text = "‹  Voltar"
            contentDescription = "Voltar para o CineHUB"
            setOnClickListener { onBack() }
        }
        addView(backButton, LayoutParams(dp(140), dp(52)).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(0, dp(22), dp(36), 0)
        })

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        soundArt.contentDescription = "Arte sonora animada da estação"
        content.addView(soundArt, LinearLayout.LayoutParams(dp(300), dp(300)).apply {
            marginEnd = dp(54)
        })

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val onAir = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0x3322C55E)
                setStroke(dp(1), 0x8850D890.toInt())
            }
            setPadding(dp(14), dp(7), dp(16), dp(7))
        }
        val dot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF50D890.toInt())
            }
        }
        onAir.addView(dot, LinearLayout.LayoutParams(dp(9), dp(9)).apply { marginEnd = dp(9) })
        onAir.addView(TextView(context).apply {
            text = "RÁDIO  •  CINEHUB"
            setTextColor(white)
            textSize = 13f
            letterSpacing = 0.08f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        info.addView(onAir, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        info.addView(stationNameText.apply {
            text = stationName.ifBlank { "Rádio CineHUB" }
            setTextColor(white)
            textSize = 34f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            contentDescription = "Estação: ${stationName.ifBlank { "Rádio CineHUB" }}"
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18)
        })

        info.addView(genreText.apply {
            text = genre.ifBlank { "Sua trilha sonora, ao vivo" }
            setTextColor(red)
            textSize = 19f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(7)
        })

        statusText.apply {
            text = "Preparando transmissão…"
            setTextColor(muted)
            textSize = 15f
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        info.addView(statusText, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18)
        })

        val equalizerLabel = TextView(context).apply {
            text = "SINTONIZADO PARA VOCÊ"
            setTextColor(muted)
            textSize = 11f
            letterSpacing = 0.16f
        }
        info.addView(equalizerLabel, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(26)
        })
        content.addView(info, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
            setMargins(dp(64), dp(74), dp(64), dp(88))
        })

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val volumeDown = Button(context).apply {
            text = "−  Volume"
            contentDescription = "Diminuir volume"
            setOnClickListener {
                (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                    ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
        }
        actions.addView(volumeDown, LinearLayout.LayoutParams(dp(130), dp(54)).apply { marginEnd = dp(10) })
        actions.addView(Button(context).apply {
            text = "Trocar estação"
            contentDescription = "Escolher outra estação de rádio"
            setOnClickListener { onChangeStation() }
        }, LinearLayout.LayoutParams(dp(150), dp(54)).apply { marginEnd = dp(10) })

        playPauseButton.apply {
            text = "▶  Reproduzir"
            contentDescription = "Reproduzir rádio"
            setOnClickListener { onPlayPause() }
        }
        actions.addView(playPauseButton, LinearLayout.LayoutParams(dp(190), dp(58)).apply { marginEnd = dp(10) })

        val volumeUp = Button(context).apply {
            text = "+  Volume"
            contentDescription = "Aumentar volume"
            setOnClickListener {
                (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                    ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
        }
        actions.addView(volumeUp, LinearLayout.LayoutParams(dp(130), dp(54)))
        addView(actions, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(30)
        })

        val footer = TextView(context).apply {
            text = "ÁUDIO AO VIVO  ·  SEM VÍDEO"
            setTextColor(0xFF777780.toInt())
            textSize = 10f
            letterSpacing = 0.14f
        }
        addView(footer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, dp(38), dp(16))
        })
    }

    fun setStation(name: String, genre: String) {
        stationNameText.text = name.ifBlank { "Rádio CineHUB" }
        stationNameText.contentDescription = "Estação: ${stationNameText.text}"
        genreText.text = genre.ifBlank { "Sua trilha sonora, ao vivo" }
        statusText.text = "Trocando estação…"
    }

    fun setPlaybackState(isPlaying: Boolean, isBuffering: Boolean = false, errorMessage: String? = null) {
        statusText.text = when {
            !errorMessage.isNullOrBlank() -> errorMessage
            isBuffering -> "Conectando à estação…"
            isPlaying -> "●  Reproduzindo agora"
            else -> "Transmissão pausada"
        }
        playPauseButton.text = if (isPlaying) "Ⅱ  Pausar" else "▶  Reproduzir"
        playPauseButton.contentDescription = if (isPlaying) "Pausar rádio" else "Reproduzir rádio"
        soundArt.active = isPlaying || isBuffering
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}

/** Lightweight, decorative equalizer animation; it never captures or analyses audio. */
private class RadioSoundArt(context: Context) : View(context) {
    var active: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bars = 32
    private val heights = FloatArray(bars) { 0.08f }
    private val startedAt = android.os.SystemClock.uptimeMillis()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = (android.os.SystemClock.uptimeMillis() - startedAt) / 1000.0
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.43f

        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(0x66E50914, 0x33E50914, 0x0008080B),
            floatArrayOf(0f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = 0x66E50914
        canvas.drawCircle(cx, cy, radius * 0.77f, paint)
        paint.color = 0x44FFFFFF
        canvas.drawCircle(cx, cy, radius * 0.58f, paint)
        paint.style = Paint.Style.FILL

        val step = w * 0.76f / bars
        val barWidth = maxOf(2f * density, step * 0.48f)
        val left = w * 0.12f
        val baseY = h * 0.79f
        for (i in 0 until bars) {
            val target = if (active) {
                (0.12 + 0.82 * abs(sin(t * (1.8 + (i % 7) * 0.24) + i * 0.63))).toFloat()
            } else 0.07f
            heights[i] += (target - heights[i]) * 0.18f
            val barHeight = h * 0.34f * heights[i]
            val x = left + i * step
            paint.color = if (i % 4 == 0) 0xFFFF5A62.toInt() else 0xFFE50914.toInt()
            canvas.drawRoundRect(x, baseY - barHeight, x + barWidth, baseY, barWidth / 2, barWidth / 2, paint)
        }
        // Keep the decorative equalizer at 20 FPS only while audio is active.
        // A paused station must not burn CPU on low-memory TV boxes.
        if (isShown && active) postInvalidateDelayed(50L)
    }
}
