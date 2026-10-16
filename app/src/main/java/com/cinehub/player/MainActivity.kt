package com.cinehub.player

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private data class EpisodeQueuePayload(val json: String, val currentIndex: Int)

    private lateinit var status: TextView
    private lateinit var streamInput: EditText
    private lateinit var memoryProfileButton: Button
    private lateinit var sourceTimeoutButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CatalogClient.initialize(applicationContext)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val root = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(15, 15, 15))
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 32, 48, 32)
            isFocusableInTouchMode = true
        }
        root.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        content.addView(TextView(this).apply {
            text = "CineHUB PLAYER"
            textSize = 30f
            setTextColor(Color.WHITE)
        })
        content.addView(TextView(this).apply {
            text = "Player nativo de teste · Android TV / Google TV / TV Box"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, 8, 0, 24)
        })
        status = TextView(this).apply {
            text = "Consultando CineHUB-ACERVO…"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, 8, 0, 20)
        }
        content.addView(status)
        val categories = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf(
            "movies" to "Filmes",
            "series" to "Séries",
            "anime" to "Animes",
            "doramas" to "Doramas"
        ).forEach { (key, label) ->
            categories.addView(Button(this).apply {
                text = label
                setOnClickListener { showCategoryPreview(key, label) }
            })
        }
        val categoriesScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(categories, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        content.addView(categoriesScroll)
        content.addView(Button(this).apply {
            text = "CARREGAR CANAIS AO VIVO"
            contentDescription = "Carregar canais com fontes diretas disponíveis"
            setOnClickListener { showChannelPicker() }
        })
        content.addView(Button(this).apply {
            text = "CARREGAR RÁDIOS DO CINEHUB-ACERVO"
            setOnClickListener { showRadioPicker() }
        })
        memoryProfileButton = Button(this).apply {
            setOnClickListener {
                val prefs = getSharedPreferences("player-settings", MODE_PRIVATE)
                val current = prefs.getString("memory_profile", "auto") ?: "auto"
                val next = when (current) {
                    "auto" -> "low"
                    "low" -> "normal"
                    else -> "auto"
                }
                prefs.edit().putString("memory_profile", next).apply()
                updateMemoryProfileLabel()
            }
        }
        content.addView(memoryProfileButton)
        updateMemoryProfileLabel()
        sourceTimeoutButton = Button(this).apply {
            setOnClickListener {
                val prefs = getSharedPreferences("player-settings", MODE_PRIVATE)
                val current = prefs.getInt("source_timeout_seconds", 15).coerceIn(10, 45)
                val next = when (current) {
                    10 -> 15
                    15 -> 20
                    20 -> 30
                    30 -> 45
                    else -> 10
                }
                prefs.edit().putInt("source_timeout_seconds", next).apply()
                updateSourceTimeoutLabel()
            }
        }
        content.addView(sourceTimeoutButton)
        updateSourceTimeoutLabel()
        streamInput = EditText(this).apply {
            hint = "URL HTTP(S) de stream autorizado (HLS, DASH ou MP4)"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 16f
            contentDescription = "URL do stream para teste"
        }
        content.addView(streamInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(Button(this).apply {
            text = "REPRODUZIR VÍDEO"
            setOnClickListener { launchPlayer(live = false) }
        })
        content.addView(Button(this).apply {
            text = "TESTAR TRANSMISSÃO AO VIVO"
            setOnClickListener { launchPlayer(live = true) }
        })
        content.addView(Button(this).apply {
            text = "ATUALIZAR STATUS DO CATÁLOGO"
            setOnClickListener { loadCatalogStatus() }
        })
        setContentView(root)
        TvUi.style(root)
        loadCatalogStatus()
        intent.getStringExtra("open_detail_json")?.let { raw ->
            runCatching {
                val json = JSONObject(raw)
                val genreArray = json.optJSONArray("genres") ?: JSONArray()
                val genres = (0 until genreArray.length()).mapNotNull { genreArray.optString(it).takeIf(String::isNotBlank) }
                CatalogClient.CatalogEntry(
                    id = json.optString("id"),
                    title = json.optString("title"),
                    type = json.optString("type"),
                    detailPath = json.optString("detail_path"),
                    posterPath = json.optString("poster_url"),
                    year = json.optString("year"),
                    genres = genres,
                    subtype = json.optString("subtype")
                )
            }.getOrNull()?.let(::showContentDetails)
        }
    }

    private fun updateSourceTimeoutLabel() {
        val seconds = getSharedPreferences("player-settings", MODE_PRIVATE)
            .getInt("source_timeout_seconds", 15).coerceIn(10, 45)
        sourceTimeoutButton.text = "Troca automática de fonte: ${seconds}s (pressione para alterar)"
        sourceTimeoutButton.contentDescription = "Tempo de espera antes de trocar uma fonte: ${seconds} segundos"
    }

    private fun updateMemoryProfileLabel() {
        val profile = getSharedPreferences("player-settings", MODE_PRIVATE)
            .getString("memory_profile", "auto") ?: "auto"
        memoryProfileButton.text = when (profile) {
            "low" -> "Buffer: ECONOMIA DE MEMÓRIA (toque para alterar)"
            "normal" -> "Buffer: DESEMPENHO (toque para alterar)"
            else -> "Buffer: AUTOMÁTICO (toque para alterar)"
        }
        memoryProfileButton.contentDescription = "Perfil de buffer: $profile"
    }

    private fun launchPlayer(live: Boolean) {
        val url = streamInput.text?.toString()?.trim().orEmpty()
        if (!PlaybackUrlPolicy.isSafeHttpUrl(url)) {
            streamInput.error = "Informe uma URL HTTP(S) válida, com host e sem credenciais"
            return
        }
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("url", url)
            putExtra("live", live)
            putExtra("content_id", url.hashCode().toString())
        })
    }

    private fun showChannelPicker() {
        status.text = "Carregando canais com fontes diretas disponíveis…"
        CatalogClient.loadChannelPreview(80) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { channels ->
                        if (channels.isEmpty()) {
                            status.text = "O catálogo de canais respondeu, mas ainda não publicou fontes diretas reproduzíveis. As fontes pendentes não serão tratadas como streams."
                            status.setTextColor(Color.rgb(255, 190, 150))
                            return@fold
                        }
                        val labels = channels.map { channel ->
                            channel.name + (channel.category.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                                " · ${channel.sources.size} fonte(s)"
                        }.toTypedArray()
                        status.text = "Canais disponíveis: ${channels.size}. Escolha um canal para iniciar."
                        status.setTextColor(Color.rgb(180, 235, 180))
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Canais ao vivo · CineHUB")
                            .setItems(labels) { _, index ->
                                val channel = channels.getOrNull(index) ?: return@setItems
                                startActivity(Intent(this, PlayerActivity::class.java).apply {
                                    putStringArrayListExtra("sources", ArrayList(channel.sources))
                                    putExtra("live", true)
                                    putExtra("content_type", "live")
                                    putExtra("content_id", channel.id)
                                    putExtra("channel_name", channel.name)
                                })
                            }
                            .setNegativeButton("Fechar", null)
                            .show()
                    },
                    onFailure = { error ->
                        status.text = "Não foi possível carregar os canais: " + (error.message ?: "erro desconhecido")
                        status.setTextColor(Color.rgb(255, 190, 150))
                    }
                )
            }
        }
    }

    private fun showRadioPicker() {
        status.text = "Carregando rádios em modo econômico…"
        CatalogClient.loadRadioPreview(60) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { radios ->
                        if (radios.isEmpty()) {
                            status.text = "Nenhuma rádio com URL válida foi encontrada."
                            return@fold
                        }
                        val labels = radios.map { radio ->
                            if (radio.genre.isNotBlank()) radio.name + " · " + radio.genre else radio.name
                        }.toTypedArray()
                        status.text = "Rádios carregadas: ${radios.size}. Selecione uma para ouvir."
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Rádios CineHUB")
                            .setItems(labels) { _, index ->
                                val radio = radios[index]
                                startActivity(Intent(this, PlayerActivity::class.java).apply {
                                    putExtra("url", radio.streamUrl)
                                    putExtra("live", true)
                                    putExtra("radio", true)
                                    putExtra("content_type", "radio")
                                    putExtra("radio_name", radio.name)
                                    putExtra("radio_genre", radio.genre)
                                    putExtra("content_id", radio.id)
                                })
                            }
                            .setNegativeButton("Fechar", null)
                            .show()
                    },
                    onFailure = { error ->
                        status.text = "Falha ao carregar rádios: " + (error.message ?: "erro desconhecido")
                    }
                )
            }
        }
    }

    private fun showCategoryPreview(category: String, label: String) {
        status.text = "Carregando prévia de $label sem baixar o índice inteiro…"
        CatalogClient.loadCategoryPreview(category, 30) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { entries ->
                        if (entries.isEmpty()) {
                            status.text = "Nenhum item encontrado em $label."
                            return@fold
                        }
                        val labels = entries.map { entry ->
                            if (entry.year.isNotBlank()) entry.title + " (" + entry.year + ")" else entry.title
                        }.toTypedArray()
                        status.text = "$label · prévia de ${entries.size} itens"
                        android.app.AlertDialog.Builder(this)
                            .setTitle(label)
                            .setItems(labels) { _, index -> showContentDetails(entries[index]) }
                            .setNegativeButton("Fechar", null)
                            .show()
                    },
                    onFailure = { error ->
                        status.text = "Não foi possível carregar $label: " + (error.message ?: "erro desconhecido")
                        status.setTextColor(Color.rgb(255, 190, 150))
                    }
                )
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            CatalogClient.clearMemoryCache()
        }
    }

    private fun showContentDetails(entry: CatalogClient.CatalogEntry) {
        status.text = "Consultando fontes e episódios de ${entry.title}…"
        CatalogClient.loadContentDetails(entry) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { details ->
                        if (details.episodes.isNotEmpty()) {
                            showSeasonPicker(details)
                        } else {
                            val directSources = details.sources.filter { CatalogClient.isDirectPlaybackSource(it) }
                                .sortedBy { it.priority }.distinctBy { it.url }
                            choosePlaybackSources(
                                details.title,
                                directSources.map { it.url },
                                details.id,
                                tmdbId = details.tmdbId,
                                catalogCategory = categoryFor(entry.type, entry.subtype),
                                catalogGenres = entry.genres.ifEmpty { details.genres },
                                catalogItemId = entry.id,
                                sourceReferers = directSources.map { it.referer }
                            )
                        }
                    },
                    onFailure = { error ->
                        status.text = "Não foi possível abrir os detalhes: " + (error.message ?: "erro desconhecido")
                    }
                )
            }
        }
    }

    /** TV-friendly drill-down: season -> episode -> audio/version -> source failover. */
    private fun showSeasonPicker(details: CatalogClient.ContentDetails) {
        val seasons = details.episodes.map { it.season }.distinct().sorted()
        if (seasons.isEmpty()) {
            status.text = "Este título não possui episódios publicados."
            return
        }
        val labels = seasons.map { "Temporada $it · " + details.episodes.count { ep -> ep.season == it }.let { count -> "$count faixas de áudio/episódio" } }
        android.app.AlertDialog.Builder(this)
            .setTitle(details.title + " · Temporadas")
            .setItems(labels.toTypedArray()) { _, index ->
                showEpisodePicker(details, seasons[index])
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showEpisodePicker(details: CatalogClient.ContentDetails, season: Int) {
        val episodes = details.episodes.filter { it.season == season }
            .map { it.episode }.filter { it > 0 }.distinct().sorted()
        if (episodes.isEmpty()) {
            android.app.AlertDialog.Builder(this).setTitle("Temporada $season")
                .setMessage("Nenhum episódio listado nesta temporada.")
                .setPositiveButton("OK", null).show()
            return
        }
        val labels = episodes.map { number ->
            val variants = details.episodes.filter { it.season == season && it.episode == number }
                .map { audioLabel(it.audio) }.distinct()
            "Episódio $number" + if (variants.isNotEmpty()) " · " + variants.joinToString(" / ") else ""
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(details.title + " · Temporada $season")
            .setItems(labels.toTypedArray()) { _, index ->
                showEpisodeVersionPicker(details, season, episodes[index])
            }
            .setNegativeButton("Voltar", null)
            .show()
    }

    private fun showEpisodeVersionPicker(
        details: CatalogClient.ContentDetails,
        season: Int,
        episodeNumber: Int
    ) {
        val variants = details.episodes.filter {
            it.season == season && it.episode == episodeNumber
        }.sortedBy { it.sources.minOfOrNull { source -> source.priority } ?: Int.MAX_VALUE }

        if (variants.isEmpty()) return
        val labels = variants.map { variant ->
            val direct = variant.sources.filter { CatalogClient.isDirectPlaybackSource(it) }
            audioLabel(variant.audio) + " · " + direct.size + " fontes diretas"
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("T$season · E$episodeNumber · Áudio")
            .setItems(labels.toTypedArray()) { _, index ->
                val variant = variants[index]
                val directSources = variant.sources.filter { CatalogClient.isDirectPlaybackSource(it) }
                    .sortedBy { it.priority }.distinctBy { it.url }
                val direct = directSources.map { it.url }
                val directReferers = directSources.map { it.referer }
                val id = details.id + "-" + season + "-" + episodeNumber + "-" + variant.audio
                if (direct.isEmpty()) {
                    val references = variant.providerRefs.size
                    android.app.AlertDialog.Builder(this)
                        .setTitle(details.title + " · T$season E$episodeNumber")
                        .setMessage(
                            "Esta versão (${audioLabel(variant.audio)}) ainda não tem URL direta compatível. " +
                            if (references > 0) "O acervo preservou $references referências de provedor. Falta ligar o resolvedor correspondente; não vou tratá-las como vídeo direto."
                            else if (variant.sources.isNotEmpty()) "As URLs publicadas são páginas de provedor, não fluxos de vídeo diretos."
                            else "O acervo ainda não publicou fontes para esta versão."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    val queue = buildEpisodeQueue(details, season, episodeNumber, variant.audio)
                    choosePlaybackSources(
                        details.title + " · T$season E$episodeNumber · " + audioLabel(variant.audio),
                        direct,
                        id,
                        queue.json,
                        details.id,
                        details.tmdbId,
                        season,
                        episodeNumber,
                        queue.currentIndex,
                        catalogCategory = categoryFor(details.type, details.subtype),
                        catalogGenres = details.genres,
                        catalogItemId = details.id,
                        sourceReferers = directReferers
                    )
                }
            }
            .setNegativeButton("Voltar", null)
            .show()
    }

    private fun categoryFor(type: String, subtype: String = ""): String = when {
        subtype.equals("anime", ignoreCase = true) || type.equals("anime", ignoreCase = true) -> "anime"
        subtype.equals("dorama", ignoreCase = true) || type.equals("dorama", ignoreCase = true) -> "doramas"
        type.equals("movie", ignoreCase = true) -> "movies"
        else -> "series"
    }

    private fun audioLabel(audio: String): String = when (audio.lowercase()) {
        "dub", "dublado", "pt", "pt-br" -> "Dublado"
        "leg", "legendado", "sub" -> "Legendado"
        "", "null" -> "Áudio não informado"
        else -> audio.uppercase()
    }

    private fun choosePlaybackSources(
        title: String,
        urls: List<String>,
        contentId: String,
        episodeQueue: String? = null,
        seriesId: String? = null,
        tmdbId: Int = 0,
        season: Int = 0,
        episode: Int = 0,
        queueCurrentIndex: Int = -1,
        catalogCategory: String? = null,
        catalogGenres: List<String> = emptyList(),
        catalogItemId: String? = null,
        sourceReferers: List<String> = emptyList()
    ) {
        val uniqueUrls = urls.distinct()
        if (uniqueUrls.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("O acervo tem metadados para este item, mas ainda não publicou uma URL direta compatível com o player. Fontes do tipo embed precisam de um resolvedor específico.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putStringArrayListExtra("sources", ArrayList(uniqueUrls))
            if (sourceReferers.isNotEmpty()) {
                putStringArrayListExtra("source_referers", ArrayList(uniqueUrls.map { url ->
                    sourceReferers.getOrNull(urls.indexOf(url)).orEmpty()
                }))
            }
            putExtra("content_id", contentId)
            putExtra("live", false)
            episodeQueue?.let { putExtra("episode_queue", it) }
            if (queueCurrentIndex >= 0) putExtra("episode_queue_current_index", queueCurrentIndex)
            seriesId?.let { putExtra("series_id", it) }
            if (tmdbId > 0) putExtra("tmdb_id", tmdbId)
            if (season > 0) putExtra("season_number", season)
            if (episode > 0) putExtra("episode_number", episode)
            catalogCategory?.let { putExtra("catalog_category", it) }
            catalogItemId?.let { putExtra("catalog_item_id", it) }
            if (catalogGenres.isNotEmpty()) putStringArrayListExtra("catalog_genres", ArrayList(catalogGenres))
        })
    }

    private fun buildEpisodeQueue(
        details: CatalogClient.ContentDetails,
        season: Int,
        episodeNumber: Int,
        preferredAudio: String
    ): EpisodeQueuePayload {
        // Keep a bounded window around the selected episode: the player receives enough
        // history for navigation and upcoming episodes without an oversized Android Intent.
        val variantsByEpisode = details.episodes
            .filter { it.episode > 0 && it.season >= 0 }
            .groupBy { it.season to it.episode }
        val allEpisodes = details.episodes
            .filter { it.episode > 0 && it.season >= 0 }
            .distinctBy { it.season to it.episode }
            .sortedWith(compareBy<CatalogClient.EpisodeEntry> { it.season }.thenBy { it.episode })
        if (allEpisodes.none { it.season == season && it.episode == episodeNumber }) {
            return EpisodeQueuePayload("[]", -1)
        }

        data class QueueCandidate(
            val season: Int,
            val episode: Int,
            val audio: String,
            val urls: List<String>,
            val referers: List<String>
        )

        val candidates = allEpisodes.mapNotNull { episodeKey ->
            val variants = variantsByEpisode[episodeKey.season to episodeKey.episode].orEmpty()
            val selected = variants.firstOrNull { it.audio.equals(preferredAudio, ignoreCase = true) }
                ?.takeIf { variant -> variant.sources.any(CatalogClient::isDirectPlaybackSource) }
                ?: variants.firstOrNull { variant -> variant.sources.any(CatalogClient::isDirectPlaybackSource) }
            val directSources = selected?.sources.orEmpty()
                .filter(CatalogClient::isDirectPlaybackSource)
                .sortedBy { it.priority }
                .distinctBy { it.url }
                .take(3)
            val urls = directSources.map { it.url }
            val referers = directSources.map { it.referer }
            if (selected == null || urls.isEmpty()) null
            else QueueCandidate(episodeKey.season, episodeKey.episode, selected.audio, urls, referers)
        }

        val currentCandidateIndex = candidates.indexOfFirst {
            it.season == season && it.episode == episodeNumber
        }
        if (currentCandidateIndex < 0) return EpisodeQueuePayload("[]", -1)

        val maxWindow = EpisodeQueuePolicy.MAX_EPISODES
        val start = EpisodeQueuePolicy.windowStart(candidates.size, currentCandidateIndex)
            ?: return EpisodeQueuePayload("[]", -1)
        val window = candidates.drop(start).take(maxWindow)
        val queue = JSONArray()
        window.forEach { candidate ->
            queue.put(JSONObject().apply {
                put("title", details.title + " · T" + candidate.season + " E" + candidate.episode)
                put("content_id", details.id + "-" + candidate.season + "-" + candidate.episode + "-" + candidate.audio)
                put("sources", JSONArray(candidate.urls))
                put("source_referers", JSONArray(candidate.referers))
                put("tmdb_id", details.tmdbId)
                put("season", candidate.season)
                put("episode", candidate.episode)
                put("poster_url", details.posterUrl)
            })
        }
        val currentIndex = currentCandidateIndex - start
        return EpisodeQueuePayload(queue.toString(), currentIndex)
    }

    private fun loadCatalogStatus() {
        status.text = "Consultando CineHUB-ACERVO…"
        CatalogClient.loadVodIndex { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { index ->
                        val lines = index.categories.entries.joinToString("\n") { (key, value) ->
                            key + ": " + value.first + " itens"
                        }
                        status.text = "CineHUB-ACERVO conectado · schema " + index.schemaVersion + "\n" + lines
                        status.setTextColor(Color.rgb(180, 235, 180))
                    },
                    onFailure = { error ->
                        status.text = "Catálogo indisponível; o player aceita links diretos.\n" + (error.message ?: "Falha de rede")
                        status.setTextColor(Color.rgb(255, 190, 150))
                    }
                )
            }
        }
    }
}
