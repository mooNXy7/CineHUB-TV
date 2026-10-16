package com.cinehub.player

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.BitmapFactory
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Minimal native playback shell. Launch with:
 * ACTION_VIEW data=<stream URL>, or extras "url", "sources" (StringArrayList),
 * "live" (Boolean), "content_id" and optional "subtitle_url".
 */
@UnstableApi
class PlayerActivity : Activity() {
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var httpDataSourceFactory: DefaultHttpDataSource.Factory? = null
    private var sourceUrls: List<String> = emptyList()
    private var sourceReferers: List<String> = emptyList()
    private var attempted = mutableSetOf<String>()
    private var currentIndex = 0
    private var sourceRequestToken = 0
    private var backgrounding = false
    private var resumeAfterStop = false
    private var isLive = false
    private var isRadio = false
    private var radioName = "Rádio CineHUB"
    private var radioGenre = ""
    private var radioExperience: RadioExperienceView? = null
    private var contentId = "unknown"
    private var seriesId = ""
    private var catalogCategory = ""
    private var catalogItemId = ""
    private var catalogGenres: List<String> = emptyList()
    private var tmdbId = 0
    private var seasonNumber = 0
    private var episodeNumber = 0
    private var introMarkers: List<IntroMarker> = emptyList()
    private val promptedIntroMarkers = mutableSetOf<String>()
    private var introCheckRunnable: Runnable? = null
    private var introPrompt: android.app.AlertDialog? = null
    private var subtitleUrl: String? = null
    private var originalSubtitleUrl: String? = null
    private var internalSubtitleFile: File? = null
    private var subtitleDelayMs = 0L
    private var subtitleMimeTypeOverride: String? = null
    private val subtitleExecutor = Executors.newSingleThreadExecutor()
    private var startupCheck: Runnable? = null
    private var startupTimeoutMs = 15_000L
    private val handler = android.os.Handler(mainLooper)
    private val posterExecutor = Executors.newSingleThreadExecutor()
    private val providerResolverExecutor = Executors.newSingleThreadExecutor()
    private val resolvedProviderUrls = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()
    private val resolvedEmbedProviderUrls =
        java.util.concurrent.ConcurrentHashMap<String, Pair<Long, ProviderUrlResolver.ResolvedSource>>()
    private val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var speedIndex = 1
    private lateinit var speedButton: Button
    private var subtitleSizeSp = 22f
    private var subtitleBackground = true
    private var episodeQueue: List<QueuedEpisode> = emptyList()
    private var queueIndex = 0
    private var nextEpisodeDialog: android.app.AlertDialog? = null
    private var nextEpisodeRunnable: Runnable? = null
    private var nextPromptShown = false
    private var failureDialogShown = false
    private var stillWatchingRunnable: Runnable? = null
    private var stillWatchingDialog: android.app.AlertDialog? = null
    private var progressSaveRunnable: Runnable? = null
    private val progressSaveIntervalMs = 15_000L
    private val stillWatchingIntervalMs = 2L * 60L * 60L * 1_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CatalogClient.initialize(applicationContext)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        isRadio = intent.getBooleanExtra("radio", false) ||
            intent.getStringExtra("content_type").equals("radio", ignoreCase = true)
        isLive = intent.getBooleanExtra("live", false) && !isRadio
        startupTimeoutMs = getSharedPreferences("player-settings", MODE_PRIVATE)
            .getInt("source_timeout_seconds", 15).coerceIn(10, 45) * 1_000L
        radioName = intent.getStringExtra("radio_name")?.trim().orEmpty().ifBlank { "Rádio CineHUB" }
        radioGenre = intent.getStringExtra("radio_genre")?.trim().orEmpty()
        contentId = intent.getStringExtra("content_id") ?: "unknown"
        seriesId = intent.getStringExtra("series_id").orEmpty()
        catalogCategory = intent.getStringExtra("catalog_category").orEmpty()
        catalogItemId = intent.getStringExtra("catalog_item_id").orEmpty()
        catalogGenres = intent.getStringArrayListExtra("catalog_genres")?.toList().orEmpty()
        tmdbId = intent.getIntExtra("tmdb_id", 0).coerceAtLeast(0)
        seasonNumber = intent.getIntExtra("season_number", 0).coerceAtLeast(0)
        episodeNumber = intent.getIntExtra("episode_number", 0).coerceAtLeast(0)
        // Never pass an untrusted intent subtitle URI to Media3 (e.g. file:// or content://).
        subtitleUrl = intent.getStringExtra("subtitle_url")
            ?.trim()
            ?.takeIf(PlaybackUrlPolicy::isSafeHttpUrl)
        episodeQueue = parseEpisodeQueue(intent.getStringExtra("episode_queue").orEmpty())
        val suppliedCurrentQueueIndex = intent.getIntExtra("episode_queue_current_index", -1)
        queueIndex = if (suppliedCurrentQueueIndex in episodeQueue.indices) {
            suppliedCurrentQueueIndex + 1
        } else {
            0
        }
        val urls = intent.getStringArrayListExtra("sources")?.toList().orEmpty()
        val suppliedReferers = intent.getStringArrayListExtra("source_referers")?.toList().orEmpty()
        val singleUrl = intent.getStringExtra("url") ?: intent.dataString
        val sourcePairs = urls.mapIndexed { index, url -> url to suppliedReferers.getOrNull(index).orEmpty() } +
            listOfNotNull(singleUrl?.let { it to "" })
        val uniquePairs = sourcePairs.mapNotNull { (rawUrl, rawReferer) ->
            val url = rawUrl.trim()
            if (!PlaybackUrlPolicy.isSafeHttpUrl(url)) null
            else url to rawReferer.takeIf(PlaybackUrlPolicy::isSafeHttpUrl).orEmpty()
        }.distinctBy { it.first }
        sourceUrls = uniquePairs.map { it.first }
        sourceReferers = uniquePairs.map { it.second }

        playerView = PlayerView(this).apply {
            useController = !isRadio
            if (isRadio) visibility = View.GONE
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            keepScreenOn = true
        }
        val root = FrameLayout(this)
        root.addView(playerView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        if (!isRadio) {
            val controls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(12, 8, 12, 8)
                setBackgroundColor(Color.argb(190, 20, 20, 20))
            }
            controls.addView(Button(this).apply {
                text = "Áudio / Legendas"
                contentDescription = "Selecionar faixas de áudio e legendas"
                setOnClickListener { showTrackMenu() }
            })
            controls.addView(Button(this).apply {
                text = "Legendas online"
                contentDescription = "Buscar legendas online ou inserir uma URL externa"
                setOnClickListener { showSubtitleOptions() }
            })
            controls.addView(Button(this).apply {
                text = "Sincr. legenda"
                contentDescription = "Ajustar o atraso da legenda externa"
                setOnClickListener { showSubtitleSyncOptions() }
            })
            controls.addView(Button(this).apply {
                text = "Estilo legenda"
                contentDescription = "Alterar tamanho e fundo da legenda"
                setOnClickListener { showSubtitleStyleOptions() }
            })
            if (!isLive && episodeQueue.isNotEmpty()) {
                controls.addView(Button(this).apply {
                    text = "Temporadas / episódios"
                    contentDescription = "Abrir temporadas e episódios disponíveis"
                    setOnClickListener { showSeasonQueuePicker() }
                })
            }
            speedButton = Button(this).apply {
                text = "Velocidade 1.0×"
                contentDescription = "Alterar velocidade de reprodução"
                setOnClickListener { cycleSpeed() }
            }
            controls.addView(speedButton)
            // Keep all player actions reachable on narrow TV screens and 720p TV boxes.
            val controlsScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(controls, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ))
            }
            // Keep the custom action strip away from Media3's own timeline/controller at the bottom.
            root.addView(controlsScroll, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ))
    
        } else {
            val radioScreen = RadioExperienceView(
                context = this,
                stationName = radioName,
                genre = radioGenre,
                onBack = { finish() },
                onPlayPause = {
                    player?.let { exo ->
                        if (exo.isPlaying) exo.pause() else exo.play()
                    }
                },
                onChangeStation = { showRadioStationPicker() }
            )
            radioExperience = radioScreen
            root.addView(radioScreen, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        setContentView(root)
        TvUi.style(root)

        if (sourceUrls.isEmpty()) {
            Toast.makeText(this, "Nenhuma fonte de reprodução recebida.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val profile = getSharedPreferences("player-settings", MODE_PRIVATE)
            .getString("memory_profile", "auto") ?: "auto"
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        // Use device memory, not the app heap cap: a 1 GB TV box can have a larger heap.
        val lowMemory = when (profile) {
            "low" -> true
            "normal" -> false
            else -> activityManager.isLowRamDevice ||
                memoryInfo.totalMem <= 1_536L * 1024L * 1024L
        }
        val trackSelector = DefaultTrackSelector(this)
        // Conservative ceiling for low-memory TV boxes; normal devices keep source-native resolution.
        if (lowMemory) {
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setMaxVideoSize(1280, 720)
                .build()
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (lowMemory) 2_500 else 5_000,
                if (lowMemory) 12_000 else 30_000,
                if (lowMemory) 750 else 1_000,
                if (lowMemory) 1_500 else 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Media3's default HTTP client sends a browser-like Accept header and a
        // stable Android TV user-agent. Some HLS origins reject requests without Accept.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 CineHUB-PLAYER/0.2"
            )
            .setDefaultRequestProperties(mapOf("Accept" to "*/*"))
        httpDataSourceFactory = httpFactory
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory))
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build().also { exo ->
                if (!isRadio) playerView.player = exo
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        if (isRadio) radioExperience?.setPlaybackState(false, errorMessage = "Falha no sinal. Tentando outra fonte…")
                        tryNextSource()
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (isRadio) {
                            radioExperience?.setPlaybackState(
                                isPlaying = this@PlayerActivity.player?.isPlaying == true,
                                isBuffering = playbackState == Player.STATE_BUFFERING
                            )
                        }
                        if (playbackState == Player.STATE_BUFFERING && startupCheck == null) {
                            // Also protect against a stream that worked initially and then stalls.
                            startupCheck = Runnable {
                                if (player?.playbackState == Player.STATE_BUFFERING) tryNextSource()
                            }.also { handler.postDelayed(it, startupTimeoutMs) }
                        }
                        if (playbackState == Player.STATE_READY) {
                            startupCheck?.let(handler::removeCallbacks)
                            startupCheck = null
                            if (seriesId.isNotBlank()) {
                                val activeUrl = sourceUrls.getOrNull(currentIndex).orEmpty()
                                val host = runCatching { Uri.parse(activeUrl).host.orEmpty() }.getOrDefault("")
                                if (host.isNotBlank()) {
                                    getSharedPreferences("player-settings", MODE_PRIVATE).edit()
                                        .putString("preferred_source_host_$seriesId", host)
                                        .apply()
                                }
                            }
                            if (!isLive && !isRadio) {
                                val savedPosition = getSharedPreferences("playback-progress", MODE_PRIVATE)
                                    .getLong(contentId, 0L)
                                if (savedPosition > 0L && (this@PlayerActivity.player?.currentPosition ?: 0L) < 5_000L) {
                                    this@PlayerActivity.player?.seekTo(savedPosition)
                                }
                                if (this@PlayerActivity.player?.isPlaying == true) {
                                    scheduleStillWatchingPrompt()
                                    scheduleProgressSave()
                                }
                            }
                        } else if (playbackState == Player.STATE_ENDED && isRadio) {
                            handler.post { if (!isFinishing && isRadio) tryNextSource() }
                        } else if (playbackState == Player.STATE_ENDED && !isLive) {
                            handleVodPlaybackEnded()
                        } else if (playbackState == Player.STATE_ENDED && isLive) {
                            // LIVE feeds can end cleanly at protocol level while the source is dead.
                            // Treat that as a source failure and try the next known endpoint.
                            handler.post {
                                if (!isFinishing && isLive) tryNextSource()
                            }
                        }
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        // TV channels stay continuous; radio deliberately supports pause/resume.
                        if (isLive && !isRadio && !playWhenReady && !backgrounding) {
                            this@PlayerActivity.player?.play()
                        } else if (!isLive && !isRadio) {
                            if (playWhenReady) {
                                scheduleStillWatchingPrompt()
                                scheduleProgressSave()
                            } else {
                                cancelStillWatchingPrompt()
                                cancelProgressSave()
                                persistPlaybackProgress()
                            }
                        }
                        if (isRadio) {
                            radioExperience?.setPlaybackState(
                                isPlaying = this@PlayerActivity.player?.isPlaying == true,
                                isBuffering = this@PlayerActivity.player?.playbackState == Player.STATE_BUFFERING
                            )
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isRadio) {
                            radioExperience?.setPlaybackState(
                                isPlaying = isPlaying,
                                isBuffering = this@PlayerActivity.player?.playbackState == Player.STATE_BUFFERING
                            )
                        }
                        if (!isLive && !isRadio) {
                            if (isPlaying) scheduleProgressSave()
                            else {
                                cancelProgressSave()
                                persistPlaybackProgress()
                            }
                        }
                    }
                })
            }
        playSource(0)
        loadIntroMarkers()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        scheduleStillWatchingPrompt()
    }

    private fun scheduleProgressSave() {
        cancelProgressSave()
        if (isLive || isRadio || isFinishing || player?.isPlaying != true) return
        progressSaveRunnable = object : Runnable {
            override fun run() {
                progressSaveRunnable = null
                if (isFinishing || isLive) return
                persistPlaybackProgress()
                if (player?.isPlaying == true) {
                    progressSaveRunnable = this
                    handler.postDelayed(this, progressSaveIntervalMs)
                }
            }
        }.also { handler.postDelayed(it, progressSaveIntervalMs) }
    }

    private fun cancelProgressSave() {
        progressSaveRunnable?.let(handler::removeCallbacks)
        progressSaveRunnable = null
    }

    private fun persistPlaybackProgress() {
        if (isLive || isRadio) return
        val exo = player
        val savedPosition = PlaybackProgressPolicy.positionToPersist(
            positionMs = exo?.currentPosition ?: -1L,
            durationMs = exo?.duration ?: C.TIME_UNSET,
            isReady = exo?.playbackState == Player.STATE_READY,
            isEnded = exo?.playbackState == Player.STATE_ENDED
        )
        // If playback is buffering/failed, preserve the previous resume point.
        if (savedPosition != null) {
            getSharedPreferences("playback-progress", MODE_PRIVATE).edit()
                .putLong(contentId, savedPosition).apply()
        }
    }

    private fun scheduleStillWatchingPrompt() {
        cancelStillWatchingPrompt()
        if (isLive || isRadio || isFinishing || player?.isPlaying != true) return
        stillWatchingRunnable = Runnable {
            stillWatchingRunnable = null
            if (isFinishing || isLive || player?.isPlaying != true) return@Runnable
            player?.pause()
            val dialog = android.app.AlertDialog.Builder(this)
                .setTitle("Ainda está assistindo?")
                .setMessage("A reprodução está ativa há duas horas. Deseja continuar?")
                .setPositiveButton("Continuar assistindo") { _, _ -> player?.play() }
                .setNegativeButton("Pausar") { _, _ -> player?.pause() }
                .create()
            stillWatchingDialog = dialog
            dialog.setOnCancelListener {
                stillWatchingDialog = null
                player?.play()
            }
            dialog.setOnDismissListener { stillWatchingDialog = null }
            dialog.show()
        }.also { handler.postDelayed(it, stillWatchingIntervalMs) }
    }

    private fun cancelStillWatchingPrompt() {
        stillWatchingRunnable?.let(handler::removeCallbacks)
        stillWatchingRunnable = null
    }

    private fun loadIntroMarkers() {
        introCheckRunnable?.let(handler::removeCallbacks)
        introCheckRunnable = null
        introMarkers = emptyList()
        promptedIntroMarkers.clear()
        introPrompt?.dismiss()
        introPrompt = null
        if (isLive || tmdbId <= 0) return
        IntroSkipClient.load(tmdbId, seasonNumber, episodeNumber) { markers ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                introMarkers = markers
                scheduleIntroCheck()
            }
        }
    }

    private fun scheduleIntroCheck() {
        if (isLive || introMarkers.isEmpty() || isFinishing || introCheckRunnable != null) return
        introCheckRunnable = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed || isLive) {
                    introCheckRunnable = null
                    return
                }
                val exo = player
                if (exo?.playbackState == Player.STATE_READY && introPrompt?.isShowing != true) {
                    val marker = IntroSkipPolicy.activeMarker(
                        positionMs = exo.currentPosition,
                        durationMs = exo.duration.takeIf { it > 0L } ?: 0L,
                        markers = introMarkers,
                        alreadyPrompted = promptedIntroMarkers
                    )
                    if (marker != null) {
                        promptedIntroMarkers.add(marker.key)
                        showIntroSkipPrompt(marker)
                    }
                }
                handler.postDelayed(this, 1_000L)
            }
        }.also { handler.postDelayed(it, 1_000L) }
    }

    private fun showIntroSkipPrompt(marker: IntroMarker) {
        val endMs = marker.endMs ?: return
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Trecho detectado")
            .setMessage("O TheIntroDB marcou uma ${marker.type.label}. Deseja pular este trecho?")
            .setPositiveButton("Pular") { _, _ ->
                player?.seekTo(endMs.coerceAtLeast(0L))
                player?.playWhenReady = true
            }
            .setNegativeButton("Continuar", null)
            .create()
        introPrompt = dialog
        dialog.setOnDismissListener { introPrompt = null }
        dialog.show()
    }

    private fun parseEpisodeQueue(raw: String): List<QueuedEpisode> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val parsed = buildList {
                // A hard input bound avoids iterating a malformed or unexpectedly huge Intent payload.
                for (index in 0 until minOf(array.length(), EpisodeQueuePolicy.MAX_EPISODES * 4)) {
                    val item = array.optJSONObject(index) ?: continue
                    val urls = item.optJSONArray("sources") ?: continue
                    val sources = buildList {
                        for (i in 0 until minOf(urls.length(), EpisodeQueuePolicy.MAX_SOURCES_PER_EPISODE * 4)) {
                            add(urls.optString(i))
                        }
                    }
                    add(QueuedEpisode(
                        item.optString("title", "Próximo episódio"),
                        item.optString("content_id", "episode-$index"),
                        sources,
                        item.optInt("tmdb_id", 0).coerceAtLeast(0),
                        item.optInt("season", 0).coerceAtLeast(0),
                        item.optInt("episode", 0).coerceAtLeast(0),
                        item.optString("poster_url").takeIf(PlaybackUrlPolicy::isSafeHttpUrl).orEmpty(),
                        item.optJSONArray("source_referers")?.let { refs ->
                            (0 until refs.length()).map { refs.optString(it).takeIf(PlaybackUrlPolicy::isSafeHttpUrl).orEmpty() }
                        }.orEmpty()
                    ))
                }
            }
            EpisodeQueuePolicy.normalize(parsed)
        }.getOrDefault(emptyList())
    }

    private fun showSeasonQueuePicker() {
        if (episodeQueue.isEmpty()) {
            Toast.makeText(this, "Não há episódios disponíveis nesta fila.", Toast.LENGTH_SHORT).show()
            return
        }
        val seasons = episodeQueue.map { it.season }.distinct().sorted()
        if (seasons.isEmpty()) {
            showEpisodeQueuePanel(null)
            return
        }
        val labels = seasons.map { season ->
            "Temporada $season · " + episodeQueue.count { it.season == season } + " episódios"
        }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Temporadas disponíveis")
            .setItems(labels) { _, index ->
                seasons.getOrNull(index)?.let { showEpisodeQueuePanel(it) }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showEpisodeQueuePanel(season: Int?) {
        val entries = episodeQueue.mapIndexedNotNull { index, episode ->
            if (season == null || episode.season == season) index to episode else null
        }
        if (entries.isEmpty()) {
            Toast.makeText(this, "Nenhum episódio disponível nesta temporada.", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = entries.map { (_, episode) ->
            "Episódio ${episode.episode}" +
                if (episode.episode <= 0) " · ${episode.title}" else ""
        }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(if (season == null) "Episódios disponíveis" else "Temporada $season · Episódios")
            .setItems(labels) { _, selected ->
                val targetIndex = entries.getOrNull(selected)?.first ?: return@setItems
                queueIndex = targetIndex
                playQueuedEpisode()
            }
            .setNegativeButton("Voltar", if (season == null) null else { _, _ -> showSeasonQueuePicker() })
            .show()
    }

    private fun showRadioStationPicker() {
        CatalogClient.loadRadioPreview(80) { result ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.fold(
                    onSuccess = { stations ->
                        if (stations.isEmpty()) {
                            Toast.makeText(this, "O acervo não retornou rádios com URL direta.", Toast.LENGTH_LONG).show()
                            return@fold
                        }
                        val labels = stations.map { station ->
                            station.name + station.genre.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                        }.toTypedArray()
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Trocar estação · CineHUB")
                            .setItems(labels) { _, index ->
                                val station = stations.getOrNull(index) ?: return@setItems
                                sourceUrls = listOf(station.streamUrl)
                                sourceReferers = emptyList()
                                attempted.clear()
                                currentIndex = 0
                                contentId = station.id
                                radioName = station.name
                                radioGenre = station.genre
                                radioExperience?.setStation(station.name, station.genre)
                                playSource(0)
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this,
                            "Não foi possível carregar as rádios: " + (error.message ?: "erro desconhecido"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }

    private fun handleVodPlaybackEnded() {
        val exo = player
        val duration = exo?.duration ?: C.TIME_UNSET
        val position = exo?.currentPosition ?: 0L
        val endedPrematurely = when {
            duration != C.TIME_UNSET && duration > 0L -> position + 10_000L < duration
            else -> position < 60_000L
        }
        if (endedPrematurely && currentIndex + 1 < sourceUrls.size) {
            tryNextSource()
            return
        }
        if (queueIndex < episodeQueue.size) showNextEpisodePrompt() else showMoreLikeThis()
    }

    private fun showMoreLikeThis() {
        if (catalogCategory.isBlank() || catalogGenres.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Mais como este")
                .setMessage("Este item ainda não tem gêneros suficientes no catálogo para montar recomendações.")
                .setPositiveButton("Fechar player") { _, _ -> finish() }
                .setNegativeButton("Continuar no player", null)
                .show()
            return
        }
        CatalogClient.loadRelatedPreview(
            category = catalogCategory,
            currentId = seriesId.ifBlank { catalogItemId.ifBlank { contentId } },
            genres = catalogGenres,
            limit = 8
        ) { result ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.fold(
                    onSuccess = { entries ->
                        if (entries.isEmpty()) {
                            android.app.AlertDialog.Builder(this)
                                .setTitle("Mais como este")
                                .setMessage("Não encontrei títulos relacionados nesta prévia do catálogo.")
                                .setPositiveButton("Fechar player") { _, _ -> finish() }
                                .setNegativeButton("Continuar no player", null)
                                .show()
                            return@fold
                        }
                        val labels = entries.map { entry ->
                            buildString {
                                append(entry.title)
                                if (entry.year.isNotBlank()) append(" (").append(entry.year).append(')')
                                if (entry.genres.isNotEmpty()) append(" · ").append(entry.genres.take(2).joinToString(" / "))
                            }
                        }.toTypedArray()
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Mais como este · CineHUB")
                            .setItems(labels) { _, index ->
                                val entry = entries.getOrNull(index) ?: return@setItems
                                val payload = JSONObject().apply {
                                    put("id", entry.id)
                                    put("title", entry.title)
                                    put("type", entry.type)
                                    put("detail_path", entry.detailPath)
                                    put("poster_url", entry.posterPath)
                                    put("year", entry.year)
                                    put("subtype", entry.subtype)
                                    put("genres", JSONArray(entry.genres))
                                }
                                startActivity(Intent(this, MainActivity::class.java).putExtra("open_detail_json", payload.toString()))
                            }
                            .setNeutralButton("Fechar player") { _, _ -> finish() }
                            .setNegativeButton("Voltar", null)
                            .show()
                    },
                    onFailure = { error ->
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Mais como este")
                            .setMessage("O catálogo não respondeu: " + (error.message ?: "erro desconhecido"))
                            .setPositiveButton("OK", null)
                            .show()
                    }
                )
            }
        }
    }

    private fun loadPreviewPoster(target: ImageView, url: String) {
        if (!PlaybackUrlPolicy.isSafeHttpUrl(url)) {
            target.visibility = View.GONE
            return
        }
        posterExecutor.execute {
            val bitmap = runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4_000
                    readTimeout = 5_000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "CineHUB-PLAYER/0.2")
                }
                try {
                    if (connection.responseCode !in 200..299 ||
                        (connection.contentLength > 0 && connection.contentLength > 2 * 1024 * 1024)
                    ) {
                        null
                    } else {
                        connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
            if (bitmap != null) runOnUiThread {
                if (!isFinishing && !isDestroyed) target.setImageBitmap(bitmap)
            }
        }
    }

    private fun showNextEpisodePrompt() {
        if (nextPromptShown || queueIndex >= episodeQueue.size) return
        nextPromptShown = true
        val next = episodeQueue[queueIndex]
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 8, 28, 8)
        }
        val poster = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(28, 28, 32))
            contentDescription = "Capa de ${next.title}"
        }
        val posterWidth = (128 * resources.displayMetrics.density).toInt()
        val posterHeight = (184 * resources.displayMetrics.density).toInt()
        panel.addView(poster, LinearLayout.LayoutParams(posterWidth, posterHeight).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = (12 * resources.displayMetrics.density).toInt()
        })
        loadPreviewPoster(poster, next.posterUrl)
        panel.addView(android.widget.TextView(this).apply {
            text = next.title
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
        })
        panel.addView(android.widget.TextView(this).apply {
            text = if (next.season > 0 && next.episode > 0) {
                "Temporada ${next.season} · Episódio ${next.episode}"
            } else {
                "Próximo item da fila"
            }
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(0, 10, 0, 8)
        })
        val countdownText = android.widget.TextView(this).apply {
            text = "Reprodução automática em 10 segundos"
            textSize = 16f
            setTextColor(Color.rgb(255, 90, 98))
            setPadding(0, 8, 0, 8)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        panel.addView(countdownText)
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Continuar assistindo")
            .setView(panel)
            .setPositiveButton("Reproduzir agora") { _, _ -> playQueuedEpisode() }
            .setNegativeButton("Parar") { _, _ -> cancelNextEpisodeCountdown() }
            .create()
        nextEpisodeDialog = dialog
        dialog.setOnCancelListener { cancelNextEpisodeCountdown() }
        dialog.setOnDismissListener {
            if (nextEpisodeDialog === dialog) {
                nextEpisodeRunnable?.let(handler::removeCallbacks)
                nextEpisodeRunnable = null
                nextEpisodeDialog = null
            }
        }
        dialog.show()
        var secondsLeft = 10
        nextEpisodeRunnable = object : Runnable {
            override fun run() {
                if (isFinishing || nextEpisodeDialog?.isShowing != true) return
                secondsLeft -= 1
                if (secondsLeft <= 0) {
                    dialog.dismiss()
                    playQueuedEpisode()
                } else {
                    countdownText.text = "Reprodução automática em ${secondsLeft} segundo(s)"
                    handler.postDelayed(this, 1_000L)
                }
            }
        }.also { handler.postDelayed(it, 1_000L) }
    }

    private fun cancelNextEpisodeCountdown() {
        nextEpisodeRunnable?.let(handler::removeCallbacks)
        nextEpisodeRunnable = null
        nextEpisodeDialog = null
    }

    private fun playQueuedEpisode() {
        cancelNextEpisodeCountdown()
        if (queueIndex !in episodeQueue.indices) return
        val next = episodeQueue[queueIndex++]
        contentId = next.contentId
        // Subtitle URLs from a previous episode can point to a different timeline.
        val previousSubtitleFile = internalSubtitleFile
        internalSubtitleFile = null
        originalSubtitleUrl = null
        subtitleUrl = null
        subtitleDelayMs = 0L
        subtitleMimeTypeOverride = null
        tmdbId = next.tmdbId
        seasonNumber = next.season
        episodeNumber = next.episode
        loadIntroMarkers()
        val preferredHost = if (seriesId.isNotBlank()) {
            getSharedPreferences("player-settings", MODE_PRIVATE)
                .getString("preferred_source_host_$seriesId", null)
        } else null
        val sourcePairs = next.sources.mapIndexed { index, url ->
            url to next.sourceReferers.getOrNull(index).orEmpty()
        }
        val orderedPairs = if (preferredHost.isNullOrBlank()) sourcePairs else sourcePairs.sortedBy { (url, _) ->
            if (runCatching { Uri.parse(url).host }.getOrNull().equals(preferredHost, ignoreCase = true)) 0 else 1
        }
        sourceUrls = orderedPairs.map { it.first }
        sourceReferers = orderedPairs.map { it.second }
        attempted.clear()
        currentIndex = 0
        nextPromptShown = false
        failureDialogShown = false
        playSource(0)
        previousSubtitleFile?.delete()
    }

    private fun showTrackMenu() {
        val exo = player ?: return
        val options = listOf(
            Triple("Áudio", C.TRACK_TYPE_AUDIO, false),
            Triple("Legendas", C.TRACK_TYPE_TEXT, false)
        ).filter { (_, type, _) -> exo.currentTracks.groups.any { it.type == type && it.length > 0 } }
        if (options.isEmpty()) {
            Toast.makeText(this, "Não há faixas alternativas disponíveis nesta fonte.", Toast.LENGTH_SHORT).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Faixas de reprodução")
            .setItems(options.map { it.first }.toTypedArray()) { _, index ->
                val (title, type, adaptive) = options[index]
                TrackSelectionDialogBuilder(this, title, exo, type)
                    .setAllowAdaptiveSelections(adaptive)
                    .setShowDisableOption(type == C.TRACK_TYPE_TEXT)
                    .build()
                    .show()
            }
            .show()
    }

    private fun cycleSpeed() {
        if (isLive) {
            Toast.makeText(this, "Velocidade indisponível para transmissão ao vivo.", Toast.LENGTH_SHORT).show()
            return
        }
        speedIndex = (speedIndex + 1) % speeds.size
        val speed = speeds[speedIndex]
        player?.setPlaybackParameters(PlaybackParameters(speed))
        speedButton.text = "Velocidade " + speed + "×"
    }

    private fun playSource(index: Int) {
        if (index !in sourceUrls.indices) {
            Toast.makeText(this, "Todas as fontes disponíveis falharam.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        currentIndex = index
        if (isRadio) radioExperience?.setPlaybackState(false, isBuffering = true)
        val url = sourceUrls[index]
        if (!PlaybackUrlPolicy.isSafeHttpUrl(url)) {
            tryNextSource()
            return
        }
        if (!attempted.add(url)) {
            tryNextSource()
            return
        }
        val requestToken = ++sourceRequestToken

        if (ProviderUrlResolver.isResolvableProviderPage(url)) {
            // Resolve provider pages off the main thread. Keep a timeout active
            // during resolution so a slow embed cannot block source failover.
            player?.stop()
            player?.clearMediaItems()
            startupCheck?.let(handler::removeCallbacks)
            startupCheck = Runnable {
                if (player?.playbackState != Player.STATE_READY &&
                    player?.playbackState != Player.STATE_ENDED) tryNextSource()
            }.also { handler.postDelayed(it, startupTimeoutMs) }
            providerResolverExecutor.execute {
                val resolved = resolveProviderPage(url)
                runOnUiThread {
                    if (isFinishing || isDestroyed || currentIndex != index ||
                        sourceRequestToken != requestToken || url !in attempted
                    ) return@runOnUiThread
                    startSourcePlayback(
                        originalUrl = url,
                        playbackUrl = resolved.url,
                        resolvedReferer = resolved.referer,
                        resolvedMimeType = resolved.mimeType
                    )
                }
            }
        } else {
            startSourcePlayback(originalUrl = url, playbackUrl = url)
        }
    }

    private fun startSourcePlayback(
        originalUrl: String,
        playbackUrl: String,
        resolvedReferer: String? = null,
        resolvedMimeType: String? = null
    ) {
        startupCheck?.let(handler::removeCallbacks)
        startupCheck = null
        val host = runCatching { Uri.parse(originalUrl).host.orEmpty().lowercase() }.getOrDefault("")
        val requestHeaders = linkedMapOf("Accept" to "*/*")
        val declaredReferer = sourceReferers.getOrNull(currentIndex).orEmpty()
            .ifBlank { resolvedReferer.orEmpty() }
        if (PlaybackUrlPolicy.isSafeHttpUrl(declaredReferer)) {
            requestHeaders["Referer"] = declaredReferer
            val refererUri = runCatching { Uri.parse(declaredReferer) }.getOrNull()
            if (refererUri != null && !refererUri.host.isNullOrBlank()) {
                requestHeaders["Origin"] = "${refererUri.scheme}://${refererUri.host}"
            }
        }
        when {
            host == "embedplayer2.xyz" || host.endsWith(".embedplayer2.xyz") -> {
                if (requestHeaders["Referer"].isNullOrBlank()) requestHeaders["Referer"] = "https://embedplayer2.xyz/"
                if (requestHeaders["Origin"].isNullOrBlank()) requestHeaders["Origin"] = "https://embedplayer2.xyz"
            }
            host == "dadinhodetapioca.fun" || host.endsWith(".dadinhodetapioca.fun") -> {
                if (requestHeaders["Referer"].isNullOrBlank()) requestHeaders["Referer"] = "https://dadinhodetapioca.fun/"
                if (requestHeaders["Origin"].isNullOrBlank()) requestHeaders["Origin"] = "https://dadinhodetapioca.fun"
            }
        }
        httpDataSourceFactory?.setDefaultRequestProperties(requestHeaders)
        player?.apply {
            stop()
            clearMediaItems()
            setMediaItem(buildMediaItem(playbackUrl, resolvedMimeType))
            prepare()
            playWhenReady = true
        }
        startupCheck = Runnable {
            if (player?.playbackState != Player.STATE_READY &&
                player?.playbackState != Player.STATE_ENDED) tryNextSource()
        }.also { handler.postDelayed(it, startupTimeoutMs) }
    }

    private data class ProviderPageResponse(val body: String, val finalUrl: String)

    private fun resolveProviderPage(pageUrl: String): ProviderUrlResolver.ResolvedSource {
        if (pageUrl.contains("dadinhodetapioca.fun/play/", ignoreCase = true)) {
            val direct = resolveDadinhoUrl(pageUrl)
            return ProviderUrlResolver.ResolvedSource(direct, "https://dadinhodetapioca.fun/")
        }
        val now = System.currentTimeMillis()
        resolvedEmbedProviderUrls[pageUrl]?.let { (savedAt, result) ->
            if (now - savedAt < 10 * 60 * 1000L) return result
        }
        val pageHost = runCatching { URL(pageUrl).host.lowercase() }.getOrDefault("")
        val rootReferer = if (pageHost.contains("mgeb.top")) "https://mgeb.top/" else "https://nhdapi.com/"
        val queue = java.util.ArrayDeque<Pair<String, String>>()
        ProviderUrlResolver.candidatePages(pageUrl).forEach { queue.addLast(it to rootReferer) }
        val visited = linkedSetOf<String>()

        while (queue.isNotEmpty() && visited.size < 12) {
            val (candidate, referer) = queue.removeFirst()
            if (!PlaybackUrlPolicy.isSafeHttpUrl(candidate) || !visited.add(candidate)) continue
            val response = fetchProviderPage(candidate, referer, "text/html,*/*") ?: continue
            val finalUrl = response.finalUrl
            val finalHost = runCatching { URL(finalUrl).host.lowercase() }.getOrDefault("")

            if (finalHost == "nhdapi.com" || finalHost.endsWith(".nhdapi.com")) {
                val config = ProviderUrlResolver.parseNhdApiConfig(response.body, finalUrl)
                if (config != null) {
                    ProviderUrlResolver.nhdApiEndpoints(config).take(11).forEach { endpoint ->
                        val endpointHost = runCatching { URL(endpoint).host.lowercase() }.getOrDefault("")
                        if (endpointHost != "nhdapi.com" && !endpointHost.endsWith(".nhdapi.com")) return@forEach
                        val apiResponse = fetchProviderPage(endpoint, finalUrl, "application/json") ?: return@forEach
                        val direct = ProviderUrlResolver.extractNhdPlayUrl(apiResponse.body, finalUrl) ?: return@forEach
                        val kind = Regex("""(?i)"kind"\s*:\s*"([^"]+)""")
                            .find(apiResponse.body)?.groupValues?.getOrNull(1)?.lowercase().orEmpty()
                        val mimeType = when {
                            kind.contains("hls") || kind.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
                            kind.contains("dash") || kind.contains("mpd") -> MimeTypes.APPLICATION_MPD
                            kind.contains("mp4") || kind.contains("progressive") -> MimeTypes.VIDEO_MP4
                            else -> null
                        }
                        val resolved = ProviderUrlResolver.ResolvedSource(direct, finalUrl, mimeType)
                        resolvedEmbedProviderUrls[pageUrl] = System.currentTimeMillis() to resolved
                        return resolved
                    }
                }
            }

            val mediaUrl = ProviderUrlResolver.extractMediaUrls(response.body, finalUrl).firstOrNull()
            if (mediaUrl != null) {
                val resolved = ProviderUrlResolver.ResolvedSource(mediaUrl, finalUrl)
                resolvedEmbedProviderUrls[pageUrl] = System.currentTimeMillis() to resolved
                return resolved
            }

            ProviderUrlResolver.extractIframeUrls(response.body, finalUrl).forEach { nested ->
                val nestedHost = runCatching { URL(nested).host.lowercase() }.getOrDefault("")
                val nestedPath = runCatching { URL(nested).path.lowercase() }.getOrDefault("")
                val nestedLower = nested.lowercase()
                val nestedIsMedia = Regex("""(?i)\.(?:m3u8|mpd|mp4|mkv|webm|avi|mov|m4v|ts)(?:$|[?#&])""")
                    .containsMatchIn(nestedLower) ||
                    nestedLower.contains("playlist") || nestedLower.contains("manifest") ||
                    ((nestedHost == "embedplayer2.xyz" || nestedHost.endsWith(".embedplayer2.xyz")) &&
                        nestedPath.contains("/cdn/hls/"))
                if (nestedIsMedia) {
                    val resolved = ProviderUrlResolver.ResolvedSource(nested, finalUrl)
                    resolvedEmbedProviderUrls[pageUrl] = System.currentTimeMillis() to resolved
                    return resolved
                }
                if (ProviderUrlResolver.isResolvableProviderPage(nested)) {
                    ProviderUrlResolver.candidatePages(nested).forEach { queue.addLast(it to finalUrl) }
                }
            }
        }
        return ProviderUrlResolver.ResolvedSource(pageUrl, rootReferer)
    }

    private fun fetchProviderPage(
        pageUrl: String,
        referer: String,
        accept: String
    ): ProviderPageResponse? {
        val connection = runCatching {
            (URL(pageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 7_000
                requestMethod = "GET"
                setRequestProperty("Accept", accept)
                setRequestProperty("Referer", referer)
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 CineHUB-PLAYER/0.2"
                )
            }
        }.getOrNull() ?: return null
        return try {
            if (connection.responseCode !in 200..299 ||
                (connection.contentLength > 0 && connection.contentLength > 2 * 1024 * 1024)
            ) return null
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 2 * 1024 * 1024) return null
                    output.write(buffer, 0, count)
                }
            }
            ProviderPageResponse(
                body = output.toString("UTF-8"),
                finalUrl = connection.url?.toString()?.takeIf(PlaybackUrlPolicy::isSafeHttpUrl) ?: pageUrl
            )
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveDadinhoUrl(pageUrl: String): String {
        val now = System.currentTimeMillis()
        resolvedProviderUrls[pageUrl]?.let { (savedAt, resolved) ->
            if (now - savedAt < 90 * 60 * 1000L) return resolved
        }
        return runCatching {
            val connection = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 7_000
                requestMethod = "GET"
                setRequestProperty("Referer", "https://rdcanais.org/")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 CineHUB-PLAYER/0.2"
                )
            }
            val html = try {
                if (connection.responseCode !in 200..299) return@runCatching pageUrl
                val output = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 1_000_000) break
                        output.write(buffer, 0, count)
                    }
                }
                output.toString("UTF-8")
            } finally {
                connection.disconnect()
            }
            val resolved = ProviderUrlResolver.extractDadinhoStreamUrl(html) ?: return@runCatching pageUrl
            resolvedProviderUrls[pageUrl] = System.currentTimeMillis() to resolved
            resolved
        }.getOrDefault(pageUrl)
    }

    private fun buildMediaItem(url: String, mimeTypeOverride: String? = null): MediaItem {
        val itemBuilder = MediaItem.Builder().setUri(Uri.parse(url))
        val parsedUri = Uri.parse(url)
        val lowerUrl = url.substringBefore('#').lowercase()
        val nestedUrl = runCatching { parsedUri.getQueryParameter("url").orEmpty().lowercase() }
            .getOrDefault("")
        // SaimoTV's Android resolver identifies this provider as HLS even though
        // its master playlist is exposed as master.txt instead of .m3u8.
        val isEmbedPlayerHls = parsedUri.host.orEmpty().equals("embedplayer2.xyz", ignoreCase = true) &&
            parsedUri.path.orEmpty().contains("/cdn/hls/", ignoreCase = true)
        val inferredMime = mimeTypeOverride ?: when {
            lowerUrl.contains(".m3u8") || nestedUrl.contains(".m3u8") || isEmbedPlayerHls -> MimeTypes.APPLICATION_M3U8
            lowerUrl.contains(".mpd") || nestedUrl.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            lowerUrl.substringBefore('?').endsWith(".mp4") -> MimeTypes.VIDEO_MP4
            else -> null
        }
        if (inferredMime != null) itemBuilder.setMimeType(inferredMime)
        subtitleUrl?.takeIf(::isAllowedSubtitleLocation)?.let { subtitle ->
            val lower = subtitle.substringBefore('?').substringBefore('#').lowercase()
            val mime = subtitleMimeTypeOverride ?: when {
                lower.endsWith(".srt") -> "application/x-subrip"
                lower.endsWith(".vtt") -> "text/vtt"
                lower.endsWith(".ssa") || lower.endsWith(".ass") -> "text/x-ssa"
                else -> "text/vtt"
            }
            itemBuilder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle))
                        .setMimeType(mime)
                        .setLanguage("pt")
                        .setSelectionFlags(0)
                        .build()
                )
            )
        }
        return itemBuilder.build()
    }

    private fun showSubtitleStyleOptions() {
        val choices = arrayOf(
            "Tamanho pequeno · 18 sp",
            "Tamanho padrão · 22 sp",
            "Tamanho grande · 28 sp",
            "Fundo da legenda: ${if (subtitleBackground) "ligado" else "desligado"}"
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("Aparência das legendas")
            .setItems(choices) { _, index ->
                when (index) {
                    0 -> subtitleSizeSp = 18f
                    1 -> subtitleSizeSp = 22f
                    2 -> subtitleSizeSp = 28f
                    3 -> subtitleBackground = !subtitleBackground
                }
                applySubtitleStyle()
                Toast.makeText(
                    this,
                    "Legenda: ${subtitleSizeSp.toInt()} sp · fundo ${if (subtitleBackground) "ligado" else "desligado"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun applySubtitleStyle() {
        playerView.subtitleView?.apply {
            setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleSizeSp)
            setStyle(
                CaptionStyleCompat(
                    Color.WHITE,
                    if (subtitleBackground) Color.argb(190, 0, 0, 0) else Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    Color.BLACK,
                    null
                )
            )
        }
    }

    private fun isAllowedSubtitleLocation(candidate: String): Boolean {
        if (PlaybackUrlPolicy.isSafeHttpUrl(candidate)) return true
        val file = internalSubtitleFile ?: return false
        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return false
        return uri.scheme == "file" && uri.path == file.absolutePath
    }

    private fun showSubtitleSyncOptions() {
        val source = originalSubtitleUrl ?: subtitleUrl?.takeIf(PlaybackUrlPolicy::isSafeHttpUrl)
        if (source.isNullOrBlank()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Sincronizar legenda")
                .setMessage("Escolha uma legenda externa primeiro. A sincronização ajusta arquivos SRT/VTT sem alterar o vídeo.")
                .setPositiveButton("Adicionar legenda") { _, _ -> promptExternalSubtitle() }
                .setNegativeButton("Fechar", null)
                .show()
            return
        }
        val choices = arrayOf(
            "−5 segundos · mais cedo",
            "−2 segundos · mais cedo",
            "Sincronização original · 0 s",
            "+2 segundos · mais tarde",
            "+5 segundos · mais tarde"
        )
        val offsets = longArrayOf(-5_000L, -2_000L, 0L, 2_000L, 5_000L)
        android.app.AlertDialog.Builder(this)
            .setTitle("Sincronização da legenda · ${if (subtitleDelayMs > 0) "+" else ""}${subtitleDelayMs / 1_000}s")
            .setItems(choices) { _, index ->
                val offset = offsets.getOrElse(index) { 0L }
                if (offset == 0L) {
                    subtitleDelayMs = 0L
                    val previousFile = internalSubtitleFile
                    internalSubtitleFile = null
                    subtitleUrl = source
                    reloadCurrentMediaItemWithSubtitle()
                    previousFile?.delete()
                } else {
                    applySubtitleDelay(source, offset)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun applySubtitleDelay(source: String, offsetMs: Long) {
        originalSubtitleUrl = source
        val requestedContentId = contentId
        Toast.makeText(this, "Ajustando sincronização da legenda…", Toast.LENGTH_SHORT).show()
        subtitleExecutor.execute {
            val result = runCatching {
                val connection = (URL(source).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 7_000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "CineHUB-PLAYER/0.2")
                }
                val rawText = try {
                    if (connection.responseCode !in 200..299 ||
                        (connection.contentLength > 0 && connection.contentLength > 4 * 1024 * 1024)
                    ) error("Arquivo de legenda inválido ou maior que 4 MB")
                    val output = ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8 * 1024)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 4 * 1024 * 1024) error("Arquivo de legenda maior que 4 MB")
                            output.write(buffer, 0, count)
                        }
                    }
                    output.toString("UTF-8")
                } finally {
                    connection.disconnect()
                }
                val shifted = SubtitleSyncPolicy.shiftTimestamps(rawText, offsetMs)
                if (shifted == rawText) error("Não encontrei timestamps SRT/VTT para ajustar")
                val extension = if (
                    subtitleMimeTypeOverride == "application/x-subrip" ||
                    source.substringBefore('?').lowercase().endsWith(".srt")
                ) "srt" else "vtt"
                val file = File(cacheDir, "cinehub-subtitle-${requestedContentId.hashCode()}-${offsetMs}.${extension}")
                FileOutputStream(file).use { it.write(shifted.toByteArray(Charsets.UTF_8)) }
                file
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || contentId != requestedContentId) {
                    result.getOrNull()?.delete()
                    return@runOnUiThread
                }
                result.fold(
                    onSuccess = { file ->
                        val previousFile = internalSubtitleFile
                        internalSubtitleFile = file
                        subtitleUrl = Uri.fromFile(file).toString()
                        subtitleDelayMs = offsetMs
                        reloadCurrentMediaItemWithSubtitle()
                        if (previousFile != file) previousFile?.delete()
                        Toast.makeText(
                            this,
                            "Sincronização aplicada: ${if (offsetMs > 0) "+" else ""}${offsetMs / 1_000}s",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(this, error.message ?: "Não foi possível ajustar a legenda", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun reloadCurrentMediaItemWithSubtitle() {
        val url = sourceUrls.getOrNull(currentIndex) ?: return
        val position = player?.currentPosition ?: 0L
        player?.setMediaItem(buildMediaItem(url), position)
        player?.prepare()
        player?.playWhenReady = true
    }

    private fun showSubtitleOptions() {
        if (tmdbId <= 0) {
            promptExternalSubtitle()
            return
        }
        Toast.makeText(this, "Buscando legendas online…", Toast.LENGTH_SHORT).show()
        SubtitleSearchClient.search(
            tmdbId = tmdbId,
            isSeries = seasonNumber > 0,
            season = seasonNumber,
            episode = episodeNumber
        ) { options ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (options.isEmpty()) {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Legendas online")
                        .setMessage("Não encontrei legendas para este título agora. Você ainda pode inserir uma URL externa.")
                        .setPositiveButton("Inserir URL") { _, _ -> promptExternalSubtitle() }
                        .setNegativeButton("Fechar", null)
                        .show()
                    return@runOnUiThread
                }
                val labels = options.map { it.label }.toTypedArray()
                android.app.AlertDialog.Builder(this)
                    .setTitle("Legendas online · OpenSubtitles")
                    .setItems(labels) { _, index ->
                        options.getOrNull(index)?.let { applyExternalSubtitle(it.url, "application/x-subrip") }
                    }
                    .setNeutralButton("Inserir URL") { _, _ -> promptExternalSubtitle() }
                    .setNegativeButton("Fechar", null)
                    .show()
            }
        }
    }

    private fun promptExternalSubtitle() {
        val input = EditText(this).apply {
            hint = "https://.../legenda.vtt ou .srt"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(subtitleUrl.orEmpty())
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Adicionar legenda externa")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aplicar") { _, _ ->
                applyExternalSubtitle(input.text?.toString()?.trim().orEmpty())
            }
            .show()
    }

    private fun applyExternalSubtitle(candidate: String, mimeTypeOverride: String? = null) {
        if (!PlaybackUrlPolicy.isSafeHttpUrl(candidate)) {
            Toast.makeText(this, "Informe uma URL HTTP(S) válida, com host e sem credenciais.", Toast.LENGTH_SHORT).show()
            return
        }
        if (sourceUrls.getOrNull(currentIndex) == null) return
        originalSubtitleUrl = candidate
        val previousFile = internalSubtitleFile
        internalSubtitleFile = null
        subtitleUrl = candidate
        subtitleDelayMs = 0L
        subtitleMimeTypeOverride = mimeTypeOverride
        reloadCurrentMediaItemWithSubtitle()
        previousFile?.delete()
    }

    private fun tryNextSource() {
        startupCheck?.let(handler::removeCallbacks)
        startupCheck = null
        val next = PlaybackSourcePolicy.nextUnattemptedIndex(sourceUrls, currentIndex, attempted)
        if (next != null) {
            playSource(next)
            return
        }

        if (isRadio && !isFinishing) {
            radioExperience?.setPlaybackState(false, errorMessage = "Esta estação falhou. Escolha outra.")
            showRadioStationPicker()
            return
        }

        // A broken episode should not strand the user when a later episode is available.
        if (!isLive && !isRadio && queueIndex < episodeQueue.size && !isFinishing && !failureDialogShown) {
            failureDialogShown = true
            val nextEpisode = episodeQueue[queueIndex]
            android.app.AlertDialog.Builder(this)
                .setTitle("Fontes indisponíveis")
                .setMessage(
                    "Não foi possível iniciar este conteúdo. Deseja pular para " +
                        nextEpisode.title + "?"
                )
                .setPositiveButton("Tentar próximo episódio") { _, _ -> playQueuedEpisode() }
                .setNegativeButton("Fechar player") { _, _ -> finish() }
                .setOnCancelListener { if (!isFinishing) finish() }
                .show()
            return
        }

        Toast.makeText(this, "Não foi possível reproduzir este conteúdo.", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isLive && !isRadio && (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE)) {
            return true
        }
        if ((keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                    (isRadio && (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE))) &&
            player != null) {
            if (isRadio) {
                if (player!!.isPlaying) player!!.pause() else player!!.play()
            } else {
                player!!.playWhenReady = !player!!.playWhenReady
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        introCheckRunnable?.let(handler::removeCallbacks)
        introCheckRunnable = null
        introPrompt?.dismiss()
        introPrompt = null
        cancelStillWatchingPrompt()
        stillWatchingDialog?.dismiss()
        stillWatchingDialog = null
        backgrounding = true
        resumeAfterStop = !isFinishing && player?.playWhenReady == true
        super.onStop()
        cancelProgressSave()
        persistPlaybackProgress()
        if (!isFinishing) player?.pause()
        playerView.player = null
        radioExperience?.setPlaybackState(false)
    }

    override fun onStart() {
        super.onStart()
        playerView.player = if (isRadio) null else player
        backgrounding = false
        if (resumeAfterStop && !isFinishing) player?.play()
        resumeAfterStop = false
        scheduleIntroCheck()
        scheduleStillWatchingPrompt()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            CatalogClient.clearMemoryCache()
        }
    }

    override fun onDestroy() {
        startupCheck?.let(handler::removeCallbacks)
        introCheckRunnable?.let(handler::removeCallbacks)
        introCheckRunnable = null
        introPrompt?.dismiss()
        introPrompt = null
        cancelStillWatchingPrompt()
        stillWatchingDialog?.dismiss()
        stillWatchingDialog = null
        cancelProgressSave()
        cancelNextEpisodeCountdown()
        posterExecutor.shutdownNow()
        providerResolverExecutor.shutdownNow()
        subtitleExecutor.shutdownNow()
        playerView.player = null
        player?.release()
        player = null
        internalSubtitleFile?.delete()
        internalSubtitleFile = null
        super.onDestroy()
    }
}
