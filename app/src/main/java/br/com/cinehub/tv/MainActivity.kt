package br.com.cinehub.tv

import android.content.Intent
import java.util.concurrent.Executors

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import java.text.SimpleDateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.ImageView
import coil.load
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

private const val BG = 0xFF080808.toInt()
private const val PANEL = 0xFF171717.toInt()
private const val RED = 0xFFE50914.toInt()
private const val WHITE = 0xFFF7F7F7.toInt()
private const val MUTED = 0xFFAAAAAA.toInt()

class MainActivity : AppCompatActivity() {
    private fun d(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt().coerceAtLeast(if (value > 0) 1 else value)

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private val sections = listOf("Início", "Ao vivo", "Filmes", "Séries", "Animes", "Doramas", "Rádio", "Minha lista", "Buscar", "Ajustes")
    private val io = Executors.newSingleThreadExecutor()
    private val catalogIo = Executors.newSingleThreadExecutor()
    private val metadataIo = Executors.newFixedThreadPool(3)
    private var channels: List<TvChannel> = emptyList()
    private var currentSection = "Início"
    private var showingMediaDetails = false
    private val favoriteNames = linkedSetOf<String>()
    private val mainHandler = Handler(android.os.Looper.getMainLooper())
    private var clockView: TextView? = null
    private var otaUpdateManager: OtaUpdateManager? = null
    private val clockTick = object : Runnable { override fun run() { clockView?.text = SimpleDateFormat("HH:mm", Locale("pt", "BR")).apply { timeZone = TimeZone.getTimeZone("America/Sao_Paulo") }.format(Date()); mainHandler.postDelayed(this, 30_000L) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        favoriteNames.addAll(getSharedPreferences("cinehub-tv", MODE_PRIVATE).getStringSet("favorite_channels", emptySet()).orEmpty())
        otaUpdateManager = OtaUpdateManager(this)
        buildHome()
        mainHandler.postDelayed({ otaUpdateManager?.checkForUpdate() }, 2500L)
    }

    private fun buildHome() {
        root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(0xFF050507.toInt())
            clipChildren = false
        }
        val scroll = ScrollView(this).apply {
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(0xFF050507.toInt())
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, 0, 0, d(28))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF050507.toInt(), 0xFF10070B.toInt(), 0xFF070609.toInt(), 0xFF13070C.toInt())
            )
        }
        scroll.addView(content)
        root.addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1))
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xD9101014.toInt(), 0xB80C0B10.toInt(), 0x55100B10.toInt(), 0x00100B10)
            )
            elevation = d(10).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(d(34), d(10), d(34), d(2))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = d(20).toFloat()
                setColor(0x66202026)
                setStroke(d(1), 0x337F7F88)
            }
        }
        val brand = ImageView(this).apply {
            setImageResource(R.drawable.cinehub_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Marca CineHUB"
        }
        header.addView(brand, LinearLayout.LayoutParams(d(42), d(42)).apply { marginEnd = d(10) })
        header.addView(label("CINEHUB", 19, WHITE, true).apply { letterSpacing = 0.13f })
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        clockView = label("--:--", 16, WHITE, true)
        header.addView(clockView!!)
        clockTick.run()
        overlay.addView(header, LinearLayout.LayoutParams(-1, d(58)).apply { setMargins(d(18), d(8), d(18), 0) })
        val navScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(d(22), d(3), d(22), d(7))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x66101014, 0x33101014, 0x00101014)
            )
        }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        sections.forEach { title ->
            val item = label(title, 14, if (title == currentSection) WHITE else MUTED, title == currentSection).apply {
                setPadding(d(15), d(10), d(15), d(10))
                isFocusable = true
                isFocusableInTouchMode = true
                background = navBackground(title == currentSection, false)
                setOnClickListener { showSection(title) }
                setOnFocusChangeListener { view, focused ->
                    view.background = navBackground(title == currentSection, focused)
                    setTextColor(if (title == currentSection || focused) WHITE else MUTED)
                    setTypeface(typeface, if (title == currentSection || focused) Typeface.BOLD else Typeface.NORMAL)
                }
            }
            nav.addView(item)
        }
        navScroll.addView(nav)
        overlay.addView(navScroll, LinearLayout.LayoutParams(-1, d(48)))
        root.addView(overlay, android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(root)
        showSection("Início")
    }

    private fun showSection(section: String) {
        showingMediaDetails = false
        currentSection = section
        refreshNavigationSelection()
        content.removeAllViews()
        when (section) {
            "Ao vivo" -> { showLiveChannels(); return }
            "Buscar" -> { showCatalogSearch(); return }
            "Minha lista" -> { showMyList(); return }
            "Ajustes" -> { showSettings(); return }
            "Rádio" -> { showRadioStations(); return }
            "Filmes", "Séries", "Animes", "Doramas" -> { showMediaSection(section); return }
        }
        showHomeLanding()
    }

    private fun navBackground(selected: Boolean, focused: Boolean): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = d(12).toFloat()
            setColor(if (selected) 0xFF4A1118.toInt() else Color.TRANSPARENT)
            if (focused) setStroke(d(2), 0xFFBBBBBB.toInt()) else if (selected) setStroke(d(1), 0xFF7A2932.toInt())
        }
    }

    private fun refreshNavigationSelection() {
        val navScroll = root.getChildAt(1) as? HorizontalScrollView ?: return
        val nav = navScroll.getChildAt(0) as? LinearLayout ?: return
        sections.forEachIndexed { index, title ->
            val item = nav.getChildAt(index) as? TextView ?: return@forEachIndexed
            val selected = title == currentSection
            item.background = navBackground(selected, item.hasFocus())
            item.setTextColor(if (selected || item.hasFocus()) WHITE else MUTED)
            item.setTypeface(item.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun showHomeLanding() {
        mainHandler.removeCallbacks(homeHeroTick)
        content.removeAllViews()
        content.setPadding(d(0), d(0), d(0), d(28))

        // Full-width cinematic hero: no floating poster card. The backdrop fills the
        // hero and a dark bottom gradient keeps title and metadata readable.
        val hero = android.widget.FrameLayout(this).apply {
            setBackgroundColor(0xFF171717.toInt())
            clipChildren = true
            clipToPadding = true
            isFocusable = true
            isFocusableInTouchMode = true
        }
        homeHeroPoster = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Imagem de destaque CineHUB TV"
        }
        hero.addView(homeHeroPoster, android.widget.FrameLayout.LayoutParams(-1, -1))
        val shade = View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x11000000, 0x44000000, 0xF7050507.toInt())
            )
        }
        hero.addView(shade, android.widget.FrameLayout.LayoutParams(-1, -1))
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(d(42), d(24), d(42), d(30))
            isFocusable = false
        }
        info.addView(label("EM DESTAQUE", 13, RED, true).apply { letterSpacing = 0.16f })
        homeHeroTitle = label("Descubra sua próxima história", 32, WHITE, true).apply {
            setPadding(0, d(5), 0, d(5)); maxLines = 2
        }
        info.addView(homeHeroTitle!!)
        homeHeroMeta = label("FILMES  •  SÉRIES  •  NOVIDADES", 13, WHITE, true)
        info.addView(homeHeroMeta!!)
        homeHeroDescription = label("Grandes histórias para assistir hoje.", 15, WHITE, false).apply {
            setPadding(0, d(8), 0, 0); maxLines = 3
        }
        info.addView(homeHeroDescription!!)
        hero.addView(info, android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        hero.setOnClickListener { homeHeroItems.getOrNull(homeHeroIndex)?.let { showMediaDetails(it) } }
        content.addView(hero, LinearLayout.LayoutParams(-1, d(430)))

        val prefs = getSharedPreferences("cinehub-tv", MODE_PRIVATE)
        showContinueWatching()
        val recentChannelName = prefs.getString("continue_channel_name", null)
        if (!recentChannelName.isNullOrBlank()) {
            content.addView(label("Continuar assistindo", 20, WHITE, true).apply {
                setPadding(d(42), d(14), 0, d(7))
            })
            val recent = label("▶   $recentChannelName     •     Retomar canal", 15, WHITE, true).apply {
                setPadding(d(16), d(12), d(16), d(12))
                background = focusBackground(PANEL)
                isFocusable = true
                isFocusableInTouchMode = true
                setOnClickListener {
                    val channel = (channels + readSavedFavoriteChannels()).firstOrNull { it.name == recentChannelName }
                    if (channel != null && channel.sources.isNotEmpty()) {
                        startActivity(Intent(this@MainActivity, PlayerActivity::class.java).apply {
                            putStringArrayListExtra(PlayerActivity.EXTRA_SOURCES, ArrayList(channel.sources))
                            putExtra(PlayerActivity.EXTRA_URL, channel.primarySource)
                            putExtra(PlayerActivity.EXTRA_TITLE, channel.name)
                        })
                    } else {
                        showInfo("Continuar assistindo", "O canal salvo no histórico não está disponível na lista carregada.")
                    }
                }
            }
            content.addView(recent, LinearLayout.LayoutParams(-1, d(56)).apply {
                setMargins(d(42), 0, d(42), 0)
            })
        }

        // The home now prioritizes content rows instead of large category shortcut tiles.
        val movieRow = createHomeMediaRow("Filmes em Alta")
        val seriesRow = createHomeMediaRow("Séries para Maratonar")
        val channelRow = createHomeChannelRow("Canais para Você")
        val doramaRow = createHomeMediaRow("Doramas")
        val animeRow = createHomeMediaRow("Animes")

        io.execute {
            try {
                val movies = OnlineCatalogRepository.previewFeed("Filmes")
                    .filter { !it.poster.isNullOrBlank() }.distinctBy { it.id }.take(18)
                val series = OnlineCatalogRepository.previewFeed("Séries")
                    .filter { !it.poster.isNullOrBlank() }.distinctBy { it.id }.take(18)
                val doramas = (OnlineCatalogRepository.previewFeed("Doramas") +
                    OnlineCatalogRepository.previewFeed("Séries"))
                    .filter { !it.poster.isNullOrBlank() &&
                        (it.title.contains("dorama", true) || it.language.contains("ko", true) ||
                         it.countries.any { country -> country.contains("Korea", true) }) }
                    .distinctBy { it.id }.take(18)
                val animes = OnlineCatalogRepository.previewFeed("Animes")
                    .filter { !it.poster.isNullOrBlank() }.distinctBy { it.id }.take(18)
                val heroItems = (movies + series).distinctBy { it.id }.take(12)
                runOnUiThread {
                    if (currentSection != "Início") return@runOnUiThread
                    homeHeroItems = heroItems
                    homeHeroIndex = 0
                    heroItems.firstOrNull()?.let { bindHomeHero(it) }
                    mainHandler.removeCallbacks(homeHeroTick)
                    if (heroItems.size > 1) mainHandler.postDelayed(homeHeroTick, 8_000L)
                    renderMediaCards(movies, movieRow, "Filmes em Alta", onItemClick = { item -> showMediaDetails(item) })
                    renderMediaCards(series, seriesRow, "Séries para Maratonar", onItemClick = { item -> showMediaDetails(item) })
                    renderMediaCards(doramas, doramaRow, "Doramas", onItemClick = { item -> showMediaDetails(item) })
                    renderMediaCards(animes, animeRow, "Animes", onItemClick = { item -> showMediaDetails(item) })
                    populateHomeChannelRow(channelRow)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (currentSection == "Início") {
                        homeHeroTitle?.text = "Sua próxima história começa aqui"
                        homeHeroMeta?.text = "CINEHUB TV"
                        homeHeroDescription?.text = "Não foi possível atualizar o catálogo agora."
                        populateHomeChannelRow(channelRow)
                    }
                }
            }
        }
        populateHomeChannelRow(channelRow)
    }

    private fun createHomeMediaRow(title: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(42), d(8), d(42), d(4))
        }
        row.addView(label(title, 20, WHITE, true).apply {
            setPadding(0, d(8), 0, d(8))
        })
        row.addView(label("Carregando catálogo…", 13, MUTED, false).apply {
            setPadding(0, d(4), 0, d(12))
        })
        content.addView(row, LinearLayout.LayoutParams(-1, -2))
        return row
    }

    private fun createHomeChannelRow(title: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(42), d(8), d(42), d(4))
        }
        row.addView(label(title, 20, WHITE, true).apply {
            setPadding(0, d(8), 0, d(8))
        })
        row.addView(label("Canais disponíveis", 13, MUTED, false).apply {
            setPadding(0, d(4), 0, d(12))
        })
        content.addView(row, LinearLayout.LayoutParams(-1, -2))
        return row
    }

    private fun populateHomeChannelRow(container: LinearLayout) {
        if (currentSection != "Início" || container.parent == null) return
        if (container.childCount > 1) container.removeViewAt(1)
        val requested = listOf("Masterchef Brasil", "Gloob", "24 Chaves", "Cartoon Network", "Todo Mundo Odeia o Chris")
        val sourceChannels = (channels + readSavedFavoriteChannels()).distinctBy { it.name }
        val allChannels = requested.mapNotNull { wanted ->
            sourceChannels.firstOrNull { it.name.contains(wanted, true) ||
                (wanted == "24 Chaves" && it.name.contains("Chaves", true)) ||
                (wanted == "Todo Mundo Odeia o Chris" && (it.name.contains("Chris", true) || it.name.contains("Todo Mundo Odeia", true))) }
        }.distinctBy { it.name }
        if (allChannels.isEmpty()) {
            container.addView(label("Seus canais aparecerão aqui quando a lista terminar de carregar.", 13, MUTED, false)
                .apply { setPadding(0, d(4), 0, d(12)) })
            return
        }
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val cards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        allChannels.forEach { channel ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(d(14), d(12), d(14), d(12))
                background = focusBackground(PANEL)
                isFocusable = true
                isFocusableInTouchMode = true
                addView(label("▶", 22, RED, true))
                addView(label(channel.name, 14, WHITE, true).apply { maxLines = 2; gravity = Gravity.CENTER })
                setOnClickListener {
                    if (channel.sources.isNotEmpty()) startActivity(Intent(this@MainActivity, PlayerActivity::class.java).apply {
                        putStringArrayListExtra(PlayerActivity.EXTRA_SOURCES, ArrayList(channel.sources))
                        putExtra(PlayerActivity.EXTRA_URL, channel.primarySource)
                        putExtra(PlayerActivity.EXTRA_TITLE, channel.name)
                    }) else showInfo("Canal indisponível", "Não há uma fonte de reprodução carregada para este canal.")
                }
            }
            cards.addView(card, LinearLayout.LayoutParams(d(190), d(92)).apply {
                setMargins(0, 0, d(12), d(8))
            })
        }
        scroll.addView(cards)
        container.addView(scroll, LinearLayout.LayoutParams(-1, -2))
    }

    private var homeHeroPoster: ImageView? = null
    private var homeHeroTitle: TextView? = null
    private var homeHeroMeta: TextView? = null
    private var homeHeroDescription: TextView? = null
    private var homeHeroItems: List<MediaItem> = emptyList()
    private var homeHeroIndex = 0
    private val homeHeroTick = object : Runnable {
        override fun run() {
            if (currentSection == "Início" && homeHeroItems.isNotEmpty()) {
                homeHeroIndex = (homeHeroIndex + 1) % homeHeroItems.size
                bindHomeHero(homeHeroItems[homeHeroIndex])
                mainHandler.postDelayed(this, 8_000L)
            }
        }
    }

    private fun bindHomeHero(item: MediaItem) {
        homeHeroTitle?.text = item.title
        homeHeroMeta?.text = listOf(
            item.year,
            if (item.rating != "—") "★ ${item.rating}" else "",
            if (item.type == "movie") "FILME" else "SÉRIE"
        ).filter { it.isNotBlank() }.joinToString("   •   ")
        homeHeroDescription?.text = item.description.ifBlank {
            "Confira as informações deste título no catálogo CineHUB."
        }
        val image = homeHeroPoster
        val url = item.backdrop ?: item.poster
        if (image != null && !url.isNullOrBlank()) {
            image.load(url) {
                crossfade(true)
                placeholder(android.graphics.drawable.ColorDrawable(0xFF202020.toInt()))
                error(android.graphics.drawable.ColorDrawable(0xFF202020.toInt()))
            }
        }
    }

    private fun showContinueWatching() {
        val raw = getSharedPreferences("cinehub-tv", MODE_PRIVATE).getString("continue_media_items", "[]") ?: "[]"
        val records = linkedMapOf<String, org.json.JSONObject>()
        val items = try {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val item = mediaItemFromJson(obj) ?: return@mapNotNull null
                records[item.id] = obj
                val playbackTitle = obj.optString("playbackTitle").ifBlank { item.title }
                if (playbackTitle != item.title) item.copy(title = playbackTitle) else item
            }.sortedByDescending { records[it.id]?.optLong("updatedAt", 0L) ?: 0L }.take(20)
        } catch (_: Exception) { emptyList() }
        if (items.isEmpty()) return
        val continueRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(42), d(8), d(42), d(4))
        }
        renderMediaCards(items, continueRow, "Continuar assistindo", onItemClick = { item ->
            val record = records[item.id] ?: return@renderMediaCards
            val sourcesJson = record.optJSONArray("sources") ?: org.json.JSONArray()
            val sources = (0 until sourcesJson.length()).mapNotNull { sourcesJson.optString(it).takeIf { url -> url.startsWith("http://") || url.startsWith("https://") } }
            if (sources.isEmpty()) {
                showMediaDetails(item)
            } else {
                val originalItem = mediaItemFromJson(record) ?: item
                launchMediaPlayback(originalItem, sources, record.optString("playbackTitle", originalItem.title),
                    item.id, record.optLong("positionMs", 0L))
            }
        })
        // Keep the hero as the first item; insert Continue Watching directly below it.
        content.addView(continueRow, 1.coerceAtMost(content.childCount), LinearLayout.LayoutParams(-1, -2))
    }

    private fun launchMediaPlayback(
        item: MediaItem,
        sources: List<String>,
        playbackTitle: String = item.title,
        historyId: String = item.id,
        resumePosition: Long = 0L
    ) {
        if (sources.isEmpty()) {
            showInfo("Fonte não encontrada", "Não encontramos uma fonte de reprodução para este conteúdo.")
            return
        }
        startActivity(Intent(this@MainActivity, PlayerActivity::class.java).apply {
            putStringArrayListExtra(PlayerActivity.EXTRA_SOURCES, ArrayList(sources))
            putExtra(PlayerActivity.EXTRA_URL, sources.first())
            putExtra(PlayerActivity.EXTRA_TITLE, item.title)
            putExtra(PlayerActivity.EXTRA_PLAYBACK_TITLE, playbackTitle)
            putExtra(PlayerActivity.EXTRA_MEDIA_ID, historyId)
            putExtra(PlayerActivity.EXTRA_POSTER, item.poster ?: "")
            putExtra(PlayerActivity.EXTRA_BACKDROP, item.backdrop ?: "")
            putExtra(PlayerActivity.EXTRA_MEDIA_TYPE, item.type)
            putExtra(PlayerActivity.EXTRA_YEAR, item.year)
            putExtra(PlayerActivity.EXTRA_RATING, item.rating)
            putExtra(PlayerActivity.EXTRA_DESCRIPTION, item.description)
            putExtra(PlayerActivity.EXTRA_LANGUAGE, item.language)
            putStringArrayListExtra(PlayerActivity.EXTRA_COUNTRIES, ArrayList(item.countries))
            putIntegerArrayListExtra(PlayerActivity.EXTRA_GENRES, ArrayList(item.genreIds))
            putExtra(PlayerActivity.EXTRA_RESUME_POSITION, resumePosition)
        })
    }

    private fun showMediaSection(section: String) {
        content.removeAllViews()
        content.setPadding(d(42), d(118), d(42), d(40))
        val headline = when (section) {
            "Filmes" -> "Qual é o filme de hoje?"
            "Séries" -> "Sua próxima maratona começa aqui"
            "Animes" -> "Entre em um novo mundo"
            else -> "Histórias para acompanhar"
        }
        content.addView(label(section.uppercase(), 12, RED, true).apply { setPadding(d(0), d(8), d(0), d(4)) })
        content.addView(label(headline, 28, WHITE, true).apply { setPadding(d(0), d(0), d(0), d(8)) })
        content.addView(label("Catálogo CineHUB TV com capas e informações online, organizados em prateleiras.", 14, MUTED, false)
            .apply { setPadding(d(0), d(0), d(0), d(14)) })

        val searchLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val search = EditText(this).apply {
            hint = when (section) {
                "Filmes" -> "Buscar filme…"
                "Séries" -> "Buscar série…"
                "Animes" -> "Buscar anime…"
                else -> "Buscar dorama…"
            }
            setSingleLine(true); textSize = 15f; setTextColor(WHITE); setHintTextColor(MUTED)
            setPadding(d(16), d(8), d(16), d(8)); imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = focusBackground(PANEL); isFocusable = true; isFocusableInTouchMode = true
        }
        searchLine.addView(search, LinearLayout.LayoutParams(0, d(50), 1f))
        val searchButton = label("BUSCAR", 13, WHITE, true).apply {
            gravity = Gravity.CENTER; setPadding(d(18), d(12), d(18), d(12))
            background = focusBackground(RED); isFocusable = true; isFocusableInTouchMode = true
        }
        searchLine.addView(searchButton, LinearLayout.LayoutParams(-2, d(50)).apply { marginStart = d(10) })
        content.addView(searchLine)
        val status = label("Carregando catálogo e categorias…", 14, MUTED, false)
            .apply { setPadding(d(2), d(12), d(0), d(10)) }
        content.addView(status)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val shelves = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results)
        content.addView(shelves)

        fun runSearch(query: String) {
            status.text = if (query.isBlank()) "Carregando catálogo online…" else "Buscando “$query”…"
            searchButton.isEnabled = false
            io.execute {
                try {
                    val online = if (query.isBlank()) OnlineCatalogRepository.feed(section)
                        else OnlineCatalogRepository.search(query, section)
                    if (query.isNotBlank()) {
                        runOnUiThread {
                            if (currentSection != section) return@runOnUiThread
                            searchButton.isEnabled = true
                            shelves.removeAllViews()
                            shelves.visibility = View.GONE
                            results.visibility = View.VISIBLE
                            status.text = if (online.isEmpty()) "Nenhum resultado encontrado para “$query”." else "${online.size} resultados encontrados"
                            renderMediaCards(online, results, "Resultados da busca")
                        }
                        return@execute
                    }

                    // Renderiza as capas online imediatamente; o índice complementar
                    // é carregado em uma segunda tarefa para não segurar a interface.
                    runOnUiThread {
                        if (currentSection != section) return@runOnUiThread
                        searchButton.isEnabled = true
                        results.removeAllViews()
                        results.visibility = View.GONE
                        shelves.visibility = View.VISIBLE
                        status.text = "Carregando títulos do catálogo CineHUB TV em segundo plano…"
                        renderMediaShelves(section, online, emptyList(), shelves)
                    }
                    catalogIo.execute {
                        val local = runCatching {
                            SaimoVodRepository.catalogTitles(this@MainActivity, section, 80)
                        }.getOrDefault(emptyList())
                        val normalized = { value: String ->
                            Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                                .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
                        }
                        val onlineByTitle = online.associateBy { normalized(it.title) }
                        val localItems = local.map { entry ->
                            val match = onlineByTitle[normalized(entry.title)]
                            match ?: MediaItem(
                                id = "saimo-${section.lowercase(Locale.ROOT)}-${normalized(entry.title).replace(' ', '-')}",
                                title = entry.title,
                                type = if (section == "Filmes") "movie" else "tv",
                                poster = null, backdrop = null, year = "", rating = "—", description = "",
                                genreIds = emptySet(), language = "", countries = emptySet()
                            )
                        }.distinctBy { normalized(it.title) }
                        runOnUiThread {
                            if (currentSection != section || search.text.toString().isNotBlank() || isFinishing || isDestroyed) return@runOnUiThread
                            status.text = "${localItems.size} títulos no catálogo CineHUB TV · ${online.size} títulos com capas e metadados online"
                            renderMediaShelves(section, online, localItems, shelves)
                            enrichCatalogMetadata(section, localItems, online, shelves, search)
                        }
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        if (currentSection != section) return@runOnUiThread
                        searchButton.isEnabled = true
                        status.text = "Não foi possível conectar ao catálogo online. Carregando o índice local…"
                    }
                    if (query.isBlank()) {
                        catalogIo.execute {
                            val local = runCatching { SaimoVodRepository.catalogTitles(this@MainActivity, section, 80) }.getOrDefault(emptyList())
                            val localItems = local.map { entry ->
                                MediaItem("saimo-${section.lowercase(Locale.ROOT)}-${entry.title.hashCode()}",
                                    entry.title, if (section == "Filmes") "movie" else "tv", null, null, "", "—", "",
                                    emptySet(), "", emptySet())
                            }
                            runOnUiThread {
                                if (currentSection == section && search.text.toString().isBlank()) {
                                    renderMediaShelves(section, emptyList(), localItems, shelves)
                                    status.text = "${localItems.size} títulos do catálogo CineHUB TV carregados"
                                }
                            }
                        }
                    }
                }
            }
        }
        searchButton.setOnClickListener { runSearch(search.text.toString().trim()) }
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { runSearch(search.text.toString().trim()); true } else false
        }
        runSearch("")
    }

    private fun renderMediaShelves(section: String, online: List<MediaItem>, local: List<MediaItem>, container: LinearLayout) {
        container.removeAllViews()
        val normalized = { value: String ->
            Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
        }
        fun shelf(title: String, items: List<MediaItem>) {
            val unique = items.filter { it.title.isNotBlank() }.distinctBy { normalized(it.title) }.take(36)
            if (unique.isNotEmpty()) renderMediaCards(unique, container, title, append = true)
        }
        fun hasGenre(item: MediaItem, ids: Set<Int>, names: Set<String>): Boolean =
            item.genreIds.any { it in ids } || item.genreNames.any { name -> names.any { name.contains(it, true) } }
        val combined = (local + online).distinctBy { normalized(it.title) }
        shelf("Do catálogo CineHUB", combined)
        shelf("Lançamentos e em destaque", online.sortedByDescending { it.year.toIntOrNull() ?: 0 })
        when (section) {
            "Filmes" -> {
                shelf("Ação e aventura", combined.filter { hasGenre(it, setOf(28, 12), setOf("ação", "aventura")) })
                shelf("Comédia para relaxar", combined.filter { hasGenre(it, setOf(35), setOf("comédia", "comedy")) })
                shelf("Drama e histórias marcantes", combined.filter { hasGenre(it, setOf(18), setOf("drama")) })
                shelf("Suspense e mistério", combined.filter { hasGenre(it, setOf(53, 9648, 80), setOf("suspense", "mistério", "crime", "thriller")) })
                shelf("Fantasia e ficção científica", combined.filter { hasGenre(it, setOf(14, 878), setOf("fantasia", "ficção científica", "science fiction")) })
                shelf("Animações", combined.filter { hasGenre(it, setOf(16), setOf("animação", "animation")) })
            }
            "Séries" -> {
                shelf("Séries para maratonar", online)
                shelf("Ação e aventura", combined.filter { hasGenre(it, setOf(10759, 28, 12), setOf("ação", "aventura")) })
                shelf("Drama", combined.filter { hasGenre(it, setOf(18), setOf("drama")) })
                shelf("Comédia", combined.filter { hasGenre(it, setOf(35), setOf("comédia", "comedy")) })
                shelf("Fantasia e ficção", combined.filter { hasGenre(it, setOf(10765, 14, 878), setOf("fantasia", "ficção científica")) })
                shelf("Mistério e investigação", combined.filter { hasGenre(it, setOf(9648, 80), setOf("mistério", "crime", "investigação")) })
                shelf("Animações", combined.filter { hasGenre(it, setOf(16), setOf("animação", "animation")) })
            }
            "Animes" -> {
                shelf("Animes em destaque", combined)
                shelf("Ação e aventura", combined.filter { hasGenre(it, setOf(10759, 28, 12), setOf("ação", "aventura")) })
                shelf("Fantasia e mundos novos", combined.filter { hasGenre(it, setOf(10765, 14, 878), setOf("fantasia", "ficção científica")) })
                shelf("Drama e romance", combined.filter { hasGenre(it, setOf(18, 10749), setOf("drama", "romance")) })
                shelf("Animação", combined.filter { hasGenre(it, setOf(16), setOf("animação", "animation")) })
            }
            else -> {
                shelf("Doramas em destaque", combined)
                shelf("Romance e drama", combined.filter { hasGenre(it, setOf(18, 10749), setOf("drama", "romance")) })
                shelf("Suspense e mistério", combined.filter { hasGenre(it, setOf(53, 9648, 80), setOf("suspense", "mistério", "crime")) })
                shelf("Doramas coreanos", combined.filter { it.language == "ko" || it.countries.contains("KR") })
                shelf("Comédia", combined.filter { hasGenre(it, setOf(35), setOf("comédia", "comedy")) })
            }
        }
        if (container.childCount == 0) container.addView(label("Nenhum título foi encontrado nesta categoria. Tente atualizar mais tarde.", 14, MUTED, false))
    }

private fun normalizeMediaTitle(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\b(19|20)\\d{2}\\b"), "")
            .replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun enrichCatalogMetadata(
        section: String,
        localItems: List<MediaItem>,
        onlineItems: List<MediaItem>,
        container: LinearLayout,
        searchField: EditText
    ) {
        val onlineTitles = onlineItems.map { normalizeMediaTitle(it.title) }.toSet()
        val candidates = localItems.filter {
            it.poster.isNullOrBlank() && normalizeMediaTitle(it.title) !in onlineTitles
        }.take(8)
        if (candidates.isEmpty()) return
        val resolved = java.util.concurrent.ConcurrentHashMap<String, MediaItem>()
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        candidates.forEach { item ->
            metadataIo.execute {
                try {
                    val wanted = normalizeMediaTitle(item.title)
                    val match = OnlineCatalogRepository.search(item.title, section)
                        .filter { !it.poster.isNullOrBlank() }
                        .firstOrNull { candidate ->
                            val title = normalizeMediaTitle(candidate.title)
                            title == wanted || title.startsWith(wanted) || wanted.startsWith(title)
                        }
                    if (match != null) resolved[item.id] = match.copy(id = item.id)
                } catch (_: Exception) {
                    // A title can stay in the local catalog even when metadata is unavailable.
                } finally {
                    if (completed.incrementAndGet() == candidates.size) {
                        runOnUiThread {
                            if (currentSection != section || searchField.text.toString().isNotBlank() || isFinishing || isDestroyed) return@runOnUiThread
                            val enriched = localItems.map { resolved[it.id] ?: it }
                            renderMediaShelves(section, onlineItems, enriched, container)
                        }
                    }
                }
            }
        }
    }

    private fun renderMediaCards(
        items: List<MediaItem>,
        container: LinearLayout,
        heading: String? = null,
        onItemClick: ((MediaItem) -> Unit)? = null,
        append: Boolean = false
    ) {
        if (!append) container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(label("Quando o catálogo responder, os títulos aparecerão aqui com suas capas.", 14, MUTED, false).apply {
                setPadding(d(2), d(12), d(0), d(18))
            })
            return
        }
        container.addView(label(heading ?: if (currentSection == "Filmes") "Em destaque" else "Descobertas para você", 20, WHITE, true).apply {
            setPadding(d(0), d(10), d(0), d(8))
        })
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        items.take(24).forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(PANEL); cornerRadius = d(14).toFloat()
                    setStroke(d(1), 0xFF414141.toInt())
                }
                clipToOutline = true
                isFocusable = true
                isFocusableInTouchMode = true
                val posterFrame = android.widget.FrameLayout(this@MainActivity).apply {
                    setBackgroundColor(0xFF242424.toInt())
                    val poster = ImageView(this@MainActivity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        contentDescription = "Capa de ${item.title}"
                        item.poster?.let { url -> load(url) {
                            crossfade(true)
                            placeholder(android.graphics.drawable.ColorDrawable(0xFF242424.toInt()))
                            error(android.graphics.drawable.ColorDrawable(0xFF242424.toInt()))
                        } }
                    }
                    addView(poster, android.widget.FrameLayout.LayoutParams(-1, -1))
                    if (item.poster.isNullOrBlank()) {
                        val fallback = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(d(10), d(12), d(10), d(12))
                            background = android.graphics.drawable.GradientDrawable(
                                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                                intArrayOf(0xFF351016.toInt(), 0xFF151518.toInt(), 0xFF080809.toInt())
                            ).apply { cornerRadius = d(12).toFloat() }
                            addView(label(if (item.type == "movie") "CINEHUB FILMES" else "CINEHUB SÉRIES", 10, RED, true).apply {
                                gravity = Gravity.CENTER
                            })
                            addView(label(item.title, 17, WHITE, true).apply {
                                gravity = Gravity.CENTER
                                maxLines = 4
                                setPadding(0, d(10), 0, d(4))
                            })
                            addView(label("CINEHUB TV", 9, MUTED, true).apply {
                                gravity = Gravity.CENTER
                                setPadding(0, d(6), 0, 0)
                            })
                        }
                        addView(fallback, android.widget.FrameLayout.LayoutParams(-1, -1))
                    }
                    val favorite = label(if (isMediaFavorite(item)) "♥" else "♡", 22,
                        if (isMediaFavorite(item)) RED else WHITE, true).apply {
                        gravity = Gravity.CENTER
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(0xD9000000.toInt())
                            setStroke(d(1), 0x66FFFFFF)
                        }
                        isFocusable = true
                        isFocusableInTouchMode = true
                        contentDescription = if (isMediaFavorite(item)) "Remover da Minha Lista" else "Adicionar à Minha Lista"
                        setOnClickListener {
                            toggleMediaFavorite(item)
                            text = if (isMediaFavorite(item)) "♥" else "♡"
                            setTextColor(if (isMediaFavorite(item)) RED else WHITE)
                            contentDescription = if (isMediaFavorite(item)) "Remover da Minha Lista" else "Adicionar à Minha Lista"
                            if (currentSection == "Minha lista") mainHandler.post { showMyList() }
                        }
                    }
                    addView(favorite, android.widget.FrameLayout.LayoutParams(d(40), d(40), Gravity.TOP or Gravity.END).apply {
                        topMargin = d(8); marginEnd = d(8)
                    })
                }
                addView(posterFrame, LinearLayout.LayoutParams(-1, d(190)))
                addView(label(item.title, 14, WHITE, true).apply {
                    maxLines = 2
                    setPadding(d(9), d(9), d(9), d(3))
                })
                val meta = listOf(item.year, if (item.rating != "—") "★ ${item.rating}" else "").filter { it.isNotBlank() }.joinToString("  •  ")
                addView(label(meta.ifBlank { "Catálogo online" }, 11, MUTED, false).apply {
                    setPadding(d(9), d(3), d(9), d(10))
                })
                setOnFocusChangeListener { view, focused ->
                    view.scaleX = if (focused) 1.015f else 1f
                    view.scaleY = if (focused) 1.015f else 1f
                    view.elevation = if (focused) d(8).toFloat() else 0f
                }
                setOnClickListener { if (onItemClick != null) onItemClick.invoke(item) else showMediaDetails(item) }
            }
            row.addView(card, LinearLayout.LayoutParams(d(164), d(278)).apply { marginEnd = d(14) })
        }
        scroll.addView(row)
        container.addView(scroll, LinearLayout.LayoutParams(-1, d(306)))
    }

    private fun isMediaFavorite(item: MediaItem): Boolean =
        getSharedPreferences("cinehub-tv", MODE_PRIVATE).getStringSet("favorite_media", emptySet()).orEmpty().contains(item.id)

    private fun mediaItemToJson(item: MediaItem): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", item.id); put("title", item.title); put("type", item.type)
        put("poster", item.poster ?: ""); put("backdrop", item.backdrop ?: "")
        put("year", item.year); put("rating", item.rating); put("description", item.description)
        put("genreIds", org.json.JSONArray(item.genreIds.toList()))
        put("language", item.language); put("countries", org.json.JSONArray(item.countries.toList()))
    }

    private fun mediaItemFromJson(obj: org.json.JSONObject): MediaItem? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = obj.optString("title").takeIf { it.isNotBlank() } ?: return null
        val genresJson = obj.optJSONArray("genreIds")
        val genres = if (genresJson == null) emptySet() else (0 until genresJson.length()).map { genresJson.optInt(it) }.toSet()
        val countriesJson = obj.optJSONArray("countries")
        val countries = if (countriesJson == null) emptySet() else (0 until countriesJson.length()).map { countriesJson.optString(it) }.toSet()
        return MediaItem(id, title, obj.optString("type", "movie"),
            obj.optString("poster").takeIf { it.startsWith("http") },
            obj.optString("backdrop").takeIf { it.startsWith("http") },
            obj.optString("year"), obj.optString("rating", "—"), obj.optString("description"),
            genres, obj.optString("language"), countries)
    }

    private fun readSavedFavoriteMedia(): List<MediaItem> {
        val raw = getSharedPreferences("cinehub-tv", MODE_PRIVATE).getString("favorite_media_items", "[]") ?: "[]"
        return try {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).mapNotNull { mediaItemFromJson(array.optJSONObject(it) ?: return@mapNotNull null) }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveMediaFavoriteSnapshot(item: MediaItem, favorite: Boolean) {
        val prefs = getSharedPreferences("cinehub-tv", MODE_PRIVATE)
        val saved = readSavedFavoriteMedia().filterNot { it.id == item.id }.toMutableList()
        if (favorite) saved.add(0, item)
        val json = org.json.JSONArray()
        saved.distinctBy { it.id }.take(300).forEach { json.put(mediaItemToJson(it)) }
        prefs.edit().putString("favorite_media_items", json.toString()).apply()
    }

    private fun toggleMediaFavorite(item: MediaItem) {
        val prefs = getSharedPreferences("cinehub-tv", MODE_PRIVATE)
        val saved = prefs.getStringSet("favorite_media", emptySet()).orEmpty().toMutableSet()
        val isNowFavorite = if (saved.contains(item.id)) {
            saved.remove(item.id)
            false
        } else {
            saved.add(item.id)
            true
        }
        prefs.edit().putStringSet("favorite_media", saved.toSet()).apply()
        saveMediaFavoriteSnapshot(item, isNowFavorite)
        android.widget.Toast.makeText(this,
            if (isNowFavorite) "Adicionado à Minha Lista" else "Removido da Minha Lista",
            android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun showRadioStations() {
        content.removeAllViews()
        content.setPadding(d(42), d(118), d(42), d(36))
        content.addView(label("RÁDIO", 12, RED, true).apply { setPadding(0, 0, 0, d(5)) })
        content.addView(label("Encontre a trilha sonora do seu momento", 28, WHITE, true).apply { setPadding(0, 0, 0, d(7)) })
        content.addView(label("Pesquise pelo nome da estação. A busca considera apenas o catálogo de rádios.", 14, MUTED, false).apply { setPadding(0, 0, 0, d(18)) })
        val search = EditText(this).apply {
            hint = "Buscar rádio pelo nome…"; setSingleLine(true); textSize = 16f
            setTextColor(WHITE); setHintTextColor(MUTED); setPadding(d(18), d(10), d(18), d(10))
            imeOptions = EditorInfo.IME_ACTION_SEARCH; background = focusBackground(PANEL)
            isFocusable = true; isFocusableInTouchMode = true
        }
        content.addView(search, LinearLayout.LayoutParams(-1, d(54)))
        val status = label("Carregando estações…", 14, MUTED, false).apply { setPadding(0, d(12), 0, d(10)) }
        content.addView(status)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results)
        var loadedStations: List<RadioStation> = emptyList()
        fun renderStations(query: String) {
            results.removeAllViews()
            val filtered = loadedStations.filter { it.name.contains(query.trim(), ignoreCase = true) }.take(160)
            status.text = when {
                loadedStations.isEmpty() -> "Carregando estações…"
                query.isNotBlank() && filtered.isEmpty() -> "Nenhuma rádio encontrada para “" + query + "”."
                query.isNotBlank() -> filtered.size.toString() + " rádio(s) encontrada(s)"
                else -> loadedStations.size.toString() + " estações disponíveis · mostrando até 160"
            }
            filtered.forEach { station ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(d(14), d(10), d(14), d(10)); background = focusBackground(PANEL)
                    isFocusable = true; isFocusableInTouchMode = true
                }
                val logo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; station.logoUrl?.let { load(it) { crossfade(true) } } }
                row.addView(logo, LinearLayout.LayoutParams(d(58), d(58)).apply { marginEnd = d(16) })
                val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                texts.addView(label(station.name, 17, WHITE, true))
                texts.addView(label("Rádio online  •  ▶ Reproduzir", 13, MUTED, false).apply { setPadding(0, d(4), 0, 0) })
                row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
                row.setOnClickListener {
                    startActivity(Intent(this@MainActivity, PlayerActivity::class.java).apply {
                        putStringArrayListExtra(PlayerActivity.EXTRA_SOURCES, arrayListOf(station.streamUrl))
                        putExtra(PlayerActivity.EXTRA_URL, station.streamUrl); putExtra(PlayerActivity.EXTRA_TITLE, station.name)
                        putExtra("cinehub_radio", true); putExtra(PlayerActivity.EXTRA_MEDIA_TYPE, "radio")
                    })
                }
                results.addView(row, LinearLayout.LayoutParams(-1, d(80)).apply { bottomMargin = d(8) })
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderStations(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        io.execute {
            val stations = try { SaimoContentRepository.loadRadios(this@MainActivity) } catch (_: Exception) { emptyList() }
            runOnUiThread {
                if (currentSection != "Rádio" || showingMediaDetails || isFinishing || isDestroyed) return@runOnUiThread
                loadedStations = stations
                if (stations.isEmpty()) status.text = "Não foi possível carregar as rádios. Verifique a conexão e tente novamente."
                else renderStations(search.text.toString())
            }
        }
    }

    private fun showMediaDetails(item: MediaItem) {
        showingMediaDetails = true
        content.removeAllViews()
        content.setPadding(0, 0, 0, d(32))
        val back = label("←  VOLTAR AO CATÁLOGO", 13, WHITE, true).apply {
            setPadding(d(42), d(18), d(42), d(12)); isFocusable = true; isFocusableInTouchMode = true
            background = focusBackground(BG)
            setOnClickListener { showingMediaDetails = false; showSection(currentSection) }
        }
        val hero = android.widget.FrameLayout(this).apply { setBackgroundColor(0xFF151515.toInt()); clipChildren = true }
        val backdrop = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            (item.backdrop ?: item.poster)?.let { load(it) { crossfade(true); placeholder(android.graphics.drawable.ColorDrawable(0xFF151515.toInt())) } }
            contentDescription = "Imagem de fundo de " + item.title
        }
        hero.addView(backdrop, android.widget.FrameLayout.LayoutParams(-1, -1))
        hero.addView(View(this).apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x22000000, 0xCC080808.toInt(), 0xFF080808.toInt()))
        }, android.widget.FrameLayout.LayoutParams(-1, -1))
        back.text = "‹"
        back.textSize = 22f
        back.gravity = Gravity.CENTER
        back.setPadding(0, 0, 0, 0)
        back.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = d(22).toFloat()
            setColor(0xAA15151A.toInt())
            setStroke(d(1), 0x447F7F88)
        }
        hero.addView(back, android.widget.FrameLayout.LayoutParams(d(48), d(48), Gravity.TOP or Gravity.START).apply {
            setMargins(d(28), d(22), 0, 0)
        })
        val heroInfo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM; setPadding(d(48), d(26), d(48), d(28)) }
        val typeLabel = when {
            item.type == "movie" -> "FILME"
            currentSection == "Animes" || item.language == "ja" -> "ANIME"
            currentSection == "Doramas" || item.language == "ko" || item.countries.contains("KR") -> "DORAMA"
            else -> "SÉRIE"
        }
        heroInfo.addView(label(typeLabel, 12, RED, true))
        heroInfo.addView(label(item.title, 32, WHITE, true).apply { setPadding(0, d(6), 0, d(7)); maxLines = 2 })
        heroInfo.addView(label(item.year.ifBlank { "Ano não informado" } + "   •   ★ " + item.rating + "   •   " + item.genreNames.take(3).joinToString(" / ").ifBlank { "CineHUB" }, 14, WHITE, true))
        hero.addView(heroInfo, android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        content.addView(hero, LinearLayout.LayoutParams(-1, d(330)))

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; setPadding(d(48), d(26), d(48), d(8)) }
        val poster = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            item.poster?.let { load(it) { crossfade(true); placeholder(android.graphics.drawable.ColorDrawable(PANEL)) } }
            contentDescription = "Capa de " + item.title
        }
        body.addView(poster, LinearLayout.LayoutParams(d(190), d(285)).apply { marginEnd = d(28) })
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(label("SOBRE O CONTEÚDO", 12, RED, true))
        info.addView(label(item.title, 25, WHITE, true).apply { setPadding(0, d(5), 0, d(10)); maxLines = 2 })
        info.addView(label(item.year.ifBlank { "Ano não informado" } + "   •   Avaliação ★ " + item.rating, 15, MUTED, true))
        info.addView(label(item.description.ifBlank { "A sinopse deste título ainda não está disponível no catálogo." }, 16, WHITE, false).apply { setPadding(0, d(14), 0, d(18)); maxLines = 8 })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val watch = label("▶  ASSISTIR", 14, WHITE, true).apply {
            setPadding(d(20), d(14), d(20), d(14)); background = focusBackground(RED); isFocusable = true; isFocusableInTouchMode = true
            setOnClickListener {
                text = "⏳  PROCURANDO FONTE…"; isEnabled = false
                val resolverSection = when {
                    currentSection == "Animes" || item.language == "ja" -> "Animes"
                    currentSection == "Doramas" || item.language == "ko" || item.countries.contains("KR") -> "Doramas"
                    else -> "Séries"
                }
                io.execute {
                    val resolvedSources = try { SaimoVodRepository.findDirectPlayback(this@MainActivity, item.title, item.type == "movie", resolverSection, item.id) } catch (_: Exception) { emptyList() }
                    val sources = (item.playbackSources + resolvedSources).distinct()
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        isEnabled = true; text = "▶  ASSISTIR"
                        if (sources.isNotEmpty()) launchMediaPlayback(item, sources)
                        else androidx.appcompat.app.AlertDialog.Builder(this@MainActivity).setTitle("Fonte não encontrada")
                            .setMessage("Ainda não encontramos uma fonte direta compatível com o player para este título.")
                            .setPositiveButton("Entendi", null).show()
                    }
                }
            }
        }
        val favorite = label(if (isMediaFavorite(item)) "♥  NA MINHA LISTA" else "♡  MINHA LISTA", 14, if (isMediaFavorite(item)) RED else WHITE, true).apply {
            setPadding(d(18), d(14), d(18), d(14)); background = focusBackground(PANEL); isFocusable = true; isFocusableInTouchMode = true
            setOnClickListener {
                toggleMediaFavorite(item)
                text = if (isMediaFavorite(item)) "♥  NA MINHA LISTA" else "♡  MINHA LISTA"
                setTextColor(if (isMediaFavorite(item)) RED else WHITE)
            }
        }
        actions.addView(watch); actions.addView(favorite, LinearLayout.LayoutParams(-2, -2).apply { marginStart = d(12) })
        info.addView(actions); body.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(body, LinearLayout.LayoutParams(-1, -2))

        val castSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(48), d(20), d(48), d(20)) }
        castSection.addView(label("ELENCO", 21, WHITE, true).apply { setPadding(0, 0, 0, d(10)) })
        castSection.addView(label("Carregando elenco…", 13, MUTED, false))
        content.addView(castSection, LinearLayout.LayoutParams(-1, -2))
        io.execute {
            val cast = runCatching { OnlineCatalogRepository.fetchCast(item) }.getOrDefault(emptyList())
            runOnUiThread {
                if (!showingMediaDetails || isFinishing || isDestroyed) return@runOnUiThread
                castSection.removeAllViews()
                castSection.addView(label("ELENCO", 21, WHITE, true).apply { setPadding(0, 0, 0, d(10)) })
                if (cast.isEmpty()) castSection.addView(label("As informações de elenco não estão disponíveis para este título.", 14, MUTED, false))
                else {
                    val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    cast.forEach { person ->
                        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = focusBackground(PANEL); setPadding(d(8), d(8), d(8), d(10)) }
                        val photo = ImageView(this@MainActivity).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            person.photoUrl?.let { load(it) { crossfade(true); placeholder(android.graphics.drawable.ColorDrawable(PANEL)) } }
                        }
                        card.addView(photo, LinearLayout.LayoutParams(d(112), d(140)))
                        card.addView(label(person.name, 13, WHITE, true).apply {
                            maxLines = 2; minLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                            setPadding(d(2), d(7), d(2), d(2))
                        })
                        card.addView(label(person.character.ifBlank { " " }, 11, MUTED, false).apply {
                            maxLines = 2; minLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                            setPadding(d(2), d(2), d(2), 0)
                        })
                        row.addView(card, LinearLayout.LayoutParams(d(132), d(230)).apply { marginEnd = d(10) })
                    }
                    scroll.addView(row); castSection.addView(scroll, LinearLayout.LayoutParams(-1, -2))
                }
            }
        }
        if (item.type != "movie") {
            val episodeSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(48), d(18), d(48), d(32)) }
            episodeSection.addView(label("TEMPORADAS E EPISÓDIOS", 21, WHITE, true).apply { setPadding(0, 0, 0, d(10)) })
            episodeSection.addView(label("Carregando episódios disponíveis…", 13, MUTED, false))
            content.addView(episodeSection, LinearLayout.LayoutParams(-1, -2))
            val resolverSection = when {
                currentSection == "Animes" || item.language == "ja" -> "Animes"
                currentSection == "Doramas" || item.language == "ko" || item.countries.contains("KR") -> "Doramas"
                else -> "Séries"
            }
            io.execute {
                val episodes = try { SaimoVodRepository.findEpisodes(this@MainActivity, item.title, resolverSection, item.id) } catch (_: Exception) { emptyList() }
                runOnUiThread {
                    if (!showingMediaDetails || isFinishing || isDestroyed) return@runOnUiThread
                    episodeSection.removeAllViews()
                    episodeSection.addView(label("TEMPORADAS E EPISÓDIOS", 21, WHITE, true).apply { setPadding(0, 0, 0, d(10)) })
                    if (episodes.isEmpty()) {
                        episodeSection.addView(label("Ainda não há episódios indexados para este título.", 14, MUTED, false)); return@runOnUiThread
                    }
                    val seasons = episodes.groupBy { it.season }.toSortedMap()
                    val seasonPicker = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
                    val seasonButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    val episodeList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    fun renderSeason(season: Int) {
                        episodeList.removeAllViews()
                        seasons[season].orEmpty().sortedBy { it.number }.forEach { episode ->
                            val row = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                                background = focusBackground(PANEL); setPadding(d(14), d(12), d(14), d(12))
                                isFocusable = true; isFocusableInTouchMode = true
                            }
                            row.addView(label("▶", 17, RED, true).apply { setPadding(0, 0, d(12), 0) })
                            val textBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                            textBlock.addView(label("Temporada " + season + "  •  Episódio " + episode.number, 15, WHITE, true))
                            textBlock.addView(label(if (episode.language.equals("leg", true)) "Legendado" else "Dublado", 12, MUTED, false).apply { setPadding(0, d(3), 0, 0) })
                            row.addView(textBlock, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(label("ASSISTIR", 12, RED, true))
                            row.setOnClickListener {
                                if (episode.sources.isNotEmpty()) launchMediaPlayback(item, episode.sources,
                                    item.title + " • T" + season + " E" + episode.number,
                                    item.id + "::" + season + ":" + episode.number + ":" + episode.language)
                            }
                            episodeList.addView(row, LinearLayout.LayoutParams(-1, d(68)).apply { bottomMargin = d(7) })
                        }
                    }
                    seasons.keys.forEachIndexed { index, season ->
                        val button = label("Temporada " + season, 13, WHITE, true).apply {
                            setPadding(d(14), d(10), d(14), d(10)); background = focusBackground(if (index == 0) RED else PANEL)
                            isFocusable = true; isFocusableInTouchMode = true
                            setOnClickListener {
                                for (i in 0 until seasonButtons.childCount) seasonButtons.getChildAt(i).background = focusBackground(PANEL)
                                background = focusBackground(RED); renderSeason(season)
                            }
                        }
                        seasonButtons.addView(button, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = d(8) })
                    }
                    seasonPicker.addView(seasonButtons)
                    episodeSection.addView(seasonPicker, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(12) })
                    episodeSection.addView(episodeList); renderSeason(seasons.keys.first())
                }
            }
        }
    }

private fun showMyList() {
        content.removeAllViews()
        content.addView(label("Minha lista", 28, WHITE, true).apply { setPadding(d(0), d(12), d(0), d(8)) })
        content.addView(label("Filmes, séries, animes, doramas e canais que você salvou.", 14, MUTED, false).apply {
            setPadding(d(0), d(0), d(0), d(14))
        })
        val status = label("Carregando sua lista…", 14, MUTED, false).apply { setPadding(d(2), d(12), d(0), d(12)) }
        content.addView(status)
        val mediaResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(mediaResults)
        val channelResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(channelResults)
        io.execute {
            val media = mutableListOf<MediaItem>()
            media.addAll(readSavedFavoriteMedia())
            val savedIds = getSharedPreferences("cinehub-tv", MODE_PRIVATE)
                .getStringSet("favorite_media", emptySet()).orEmpty()
            if (savedIds.isNotEmpty()) {
                listOf("Filmes", "Séries", "Animes", "Doramas").forEach { category ->
                    try { media.addAll(OnlineCatalogRepository.feed(category).filter { savedIds.contains(it.id) }) }
                    catch (_: Exception) { }
                }
            }
            var loadedChannels = channels
            if (loadedChannels.isEmpty()) try { loadedChannels = CatalogRepository.loadChannels() } catch (_: Exception) { }
            val savedChannelSnapshots = readSavedFavoriteChannels()
            loadedChannels = (loadedChannels + savedChannelSnapshots)
                .distinctBy { it.name.trim().lowercase() }
            runOnUiThread {
                if (currentSection != "Minha lista") return@runOnUiThread
                channels = loadedChannels
                val favorites = loadedChannels.filter { favoriteNames.contains(it.name) }
                status.text = when {
                    media.isEmpty() && favorites.isEmpty() -> "Sua lista está vazia. Use o coração nos cards para salvar títulos ou canais."
                    else -> "${media.size + favorites.size} item(ns) salvo(s)"
                }
                if (media.isNotEmpty()) renderMediaCards(media.distinctBy { it.id }, mediaResults)
                if (favorites.isNotEmpty()) renderChannelRows(favorites, channelResults, status, "")
            }
        }
    }

    private fun readSavedFavoriteChannels(): List<TvChannel> {
        val raw = getSharedPreferences("cinehub-tv", MODE_PRIVATE)
            .getString("favorite_channel_items", "[]") ?: "[]"
        return try {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val sourcesJson = obj.optJSONArray("sources") ?: org.json.JSONArray()
                val sources = (0 until sourcesJson.length()).mapNotNull { sourcesJson.optString(it).takeIf { s -> s.startsWith("http://") || s.startsWith("https://") } }
                TvChannel(name, obj.optString("category", "Outros"), obj.optString("logo").takeIf { it.startsWith("http://") || it.startsWith("https://") }, sources)
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveFavoriteChannelSnapshot(channel: TvChannel) {
        val prefs = getSharedPreferences("cinehub-tv", MODE_PRIVATE)
        val saved = readSavedFavoriteChannels().filterNot { it.name.equals(channel.name, ignoreCase = true) }.toMutableList()
        if (favoriteNames.contains(channel.name)) saved.add(channel)
        val array = org.json.JSONArray()
        saved.forEach { item ->
            array.put(org.json.JSONObject().apply {
                put("name", item.name)
                put("category", item.category)
                put("logo", item.logo ?: "")
                put("sources", org.json.JSONArray(item.sources))
            })
        }
        prefs.edit().putString("favorite_channel_items", array.toString()).apply()
    }

    private fun toggleFavorite(channel: TvChannel) {
        if (!favoriteNames.add(channel.name)) favoriteNames.remove(channel.name)
        getSharedPreferences("cinehub-tv", MODE_PRIVATE).edit()
            .putStringSet("favorite_channels", favoriteNames.toSet())
            .apply()
        saveFavoriteChannelSnapshot(channel)
    }


    private fun showSettings() {
        content.removeAllViews()
        content.addView(label("Ajustes", 28, WHITE, true).apply { setPadding(d(0), d(12), d(0), d(8)) })
        content.addView(label("Preferências e manutenção do CineHUB TV", 14, MUTED, false).apply {
            setPadding(d(0), d(0), d(0), d(18))
        })
        val status = label("Catálogo: ${if (channels.isEmpty()) "ainda não carregado" else "${channels.size} canais em memória"}", 15, MUTED, false).apply {
            setPadding(d(0), d(10), d(0), d(14))
        }
        content.addView(status)
        fun action(title: String, description: String, onClick: () -> Unit) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(d(18), d(14), d(18), d(14))
                background = focusBackground(PANEL)
                isFocusable = true
                isFocusableInTouchMode = true
                addView(label(title, 17, WHITE, true))
                addView(label(description, 13, MUTED, false).apply { setPadding(d(0), d(5), d(0), d(0)) })
                setOnClickListener { onClick() }
            }
            content.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        }
        action("Atualizar catálogo de canais", "Busca novamente as fontes públicas conectadas.") {
            status.text = "Atualizando catálogo…"
            io.execute {
                try {
                    val loaded = CatalogRepository.loadChannels()
                    runOnUiThread {
                        channels = loaded
                        status.text = "Catálogo atualizado: ${loaded.size} canais disponíveis."
                    }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "Não foi possível atualizar. Confira a internet e tente novamente." }
                }
            }
        }
        action("Limpar Minha lista", "Remove os canais favoritos salvos neste aparelho.") {
            favoriteNames.clear()
            getSharedPreferences("cinehub-tv", MODE_PRIVATE).edit()
                .putStringSet("favorite_channels", emptySet())
                .putStringSet("favorite_media", emptySet())
                .putString("favorite_media_items", "[]")
                .putString("favorite_channel_items", "[]")
                .apply()
            status.text = "Minha lista foi limpa."
        }
        action("Verificar atualização do aplicativo", "Consulta a versão oficial e instala atualizações com verificação de integridade.") {
            status.text = "Verificando atualizações…"
            otaUpdateManager?.checkForUpdate(force = true) { message -> status.text = message }
        }
        action("Sobre o CineHUB TV", "Versão nativa 1.3.1 • Interface Android/Kotlin • Player Media3") {
            status.text = "CineHUB TV Kotlin 1.3.1 • Atualizações OTA disponíveis nesta edição."
        }
    }

    private fun showInfo(title: String, message: String) {
        content.removeAllViews()
        content.addView(label(title, 26, WHITE, true).apply { setPadding(d(0), d(18), d(0), d(12)) })
        content.addView(label(message, 16, MUTED, false))
    }

    private fun showCatalogSearch() {
        content.removeAllViews()
        content.setPadding(d(42), d(116), d(42), d(36))
        content.addView(label("Buscar no CineHUB", 26, WHITE, true).apply {
            setPadding(d(0), d(12), d(0), d(8))
        })
        content.addView(label("Pesquise filmes, séries, animes, doramas e canais ao vivo em um só lugar.", 14, MUTED, false).apply {
            setPadding(d(0), d(0), d(0), d(14))
        })

        val searchLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val search = EditText(this).apply {
            hint = "Nome de filme, série ou canal…"
            setSingleLine(true)
            textSize = 16f
            setTextColor(WHITE)
            setHintTextColor(MUTED)
            setPadding(d(18), d(10), d(18), d(10))
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = focusBackground(PANEL)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        searchLine.addView(search, LinearLayout.LayoutParams(0, d(54), 1f))
        val searchButton = label("BUSCAR", 13, WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(d(18), d(12), d(18), d(12))
            background = focusBackground(RED)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        searchLine.addView(searchButton, LinearLayout.LayoutParams(-2, d(54)).apply {
            marginStart = d(10)
        })
        content.addView(searchLine)

        val status = label("Digite o que deseja encontrar e selecione Buscar.", 14, MUTED, false).apply {
            setPadding(d(2), d(12), d(0), d(8))
        }
        content.addView(status)

        val channelResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val movieResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val seriesResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(channelResults)
        content.addView(movieResults)
        content.addView(seriesResults)

        fun runSearch() {
            val query = search.text.toString().trim()
            if (query.isBlank()) {
                status.text = "Digite um nome para pesquisar filmes, séries e canais."
                channelResults.removeAllViews()
                movieResults.removeAllViews()
                seriesResults.removeAllViews()
                return
            }
            status.text = "Pesquisando “$query” em filmes, séries e canais…"
            searchButton.isEnabled = false
            io.execute {
                var loadedChannels = channels
                var channelError = false
                var movieError = false
                var seriesError = false
                var movies: List<MediaItem> = emptyList()
                var series: List<MediaItem> = emptyList()
                try {
                    if (loadedChannels.isEmpty()) loadedChannels = CatalogRepository.loadChannels()
                } catch (_: Exception) {
                    channelError = true
                }
                try {
                    movies = OnlineCatalogRepository.search(query, "Filmes")
                } catch (_: Exception) {
                    movieError = true
                }
                try {
                    series = OnlineCatalogRepository.search(query, "Séries")
                } catch (_: Exception) {
                    seriesError = true
                }
                val finalChannels = loadedChannels.filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.category.contains(query, ignoreCase = true)
                }
                runOnUiThread {
                    if (currentSection != "Buscar") return@runOnUiThread
                    channels = loadedChannels
                    val total = finalChannels.size + movies.size + series.size
                    val summary = when {
                        total > 0 -> "$total resultado(s) encontrados"
                        channelError && movieError && seriesError -> "Não foi possível acessar as fontes. Verifique a conexão e tente novamente."
                        else -> "Nenhum resultado encontrado para “$query”. Tente outro termo."
                    }
                    searchButton.isEnabled = true

                    channelResults.removeAllViews()
                    if (finalChannels.isNotEmpty()) {
                        renderChannelRows(finalChannels, channelResults, status, query)
                        channelResults.addView(label("CANAIS AO VIVO  •  ${finalChannels.size}", 19, WHITE, true).apply {
                            setPadding(d(0), d(14), d(0), d(8))
                        }, 0)
                    } else if (channelError) {
                        channelResults.addView(label("Não foi possível pesquisar canais ao vivo agora.", 13, MUTED, false).apply {
                            setPadding(d(0), d(6), d(0), d(6))
                        })
                    }

                    movieResults.removeAllViews()
                    if (movies.isNotEmpty()) {
                        renderMediaCards(movies, movieResults)
                        movieResults.addView(label("FILMES  •  ${movies.size}", 19, WHITE, true).apply {
                            setPadding(d(0), d(16), d(0), d(8))
                        }, 0)
                    } else if (movieError) {
                        movieResults.addView(label("Não foi possível pesquisar filmes agora.", 13, MUTED, false))
                    }

                    seriesResults.removeAllViews()
                    if (series.isNotEmpty()) {
                        renderMediaCards(series, seriesResults)
                        seriesResults.addView(label("SÉRIES, ANIMES E DORAMAS  •  ${series.size}", 19, WHITE, true).apply {
                            setPadding(d(0), d(16), d(0), d(8))
                        }, 0)
                    } else if (seriesError) {
                        seriesResults.addView(label("Não foi possível pesquisar séries agora.", 13, MUTED, false))
                    }
                    status.text = summary
                }
            }
        }
        searchButton.setOnClickListener { runSearch() }
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch()
                true
            } else false
        }
    }

    private fun showLiveChannels() {
        content.removeAllViews()
        content.setPadding(d(36), d(116), d(36), d(30))
        content.addView(label("Canais ao vivo", 26, WHITE, true).apply {
            setPadding(d(0), d(12), d(0), d(8))
        })
        content.addView(label("Pesquise por nome ou categoria. Se um canal tiver mais de uma fonte, o player tentará alternativas quando necessário.", 14, MUTED, false).apply {
            setPadding(d(0), d(0), d(0), d(10))
        })
        val guideButton = label("▦  Guia de programação", 15, WHITE, true).apply {
            setPadding(d(18), d(12), d(18), d(12))
            background = focusBackground(PANEL)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { showLiveGuide() }
        }
        content.addView(guideButton, LinearLayout.LayoutParams(-1, d(50)).apply { bottomMargin = d(10) })
        val search = EditText(this).apply {
            hint = "Buscar canal…"
            setSingleLine(true)
            textSize = 16f
            setTextColor(WHITE)
            setHintTextColor(MUTED)
            setPadding(d(18), d(10), d(18), d(10))
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = focusBackground(PANEL)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        content.addView(search, LinearLayout.LayoutParams(-1, d(54)))
        val status = label("Carregando catálogo público…", 14, MUTED, false).apply {
            setPadding(d(2), d(12), d(0), d(12))
        }
        content.addView(status)
        val rowsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(rowsContainer)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (channels.isNotEmpty()) renderChannelRows(channels, rowsContainer, status, s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        if (channels.isNotEmpty()) {
            renderChannelRows(channels, rowsContainer, status, search.text.toString())
            return
        }
        status.text = "Conectando ao catálogo de canais…"
        io.execute {
            try {
                val loaded = CatalogRepository.loadChannels()
                runOnUiThread {
                    channels = loaded
                    renderChannelRows(loaded, rowsContainer, status, search.text.toString())
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Não foi possível carregar o catálogo. Confira a internet e tente novamente."
                }
            }
        }
    }


    private fun showLiveGuide() {
        content.removeAllViews()
        content.addView(label("Guia de programação", 26, WHITE, true).apply {
            setPadding(d(0), d(10), d(0), d(6))
        })
        val back = label("←  Voltar aos canais ao vivo", 15, WHITE, true).apply {
            setPadding(d(16), d(10), d(16), d(10))
            background = focusBackground(PANEL)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { showLiveChannels() }
        }
        content.addView(back, LinearLayout.LayoutParams(-1, d(48)).apply { bottomMargin = d(8) })
        val status = label("Carregando programação do CineHUB ACERVO…", 14, MUTED, false).apply {
            setPadding(d(2), d(8), d(0), d(10))
        }
        content.addView(status)
        val guideRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(guideRows)

        io.execute {
            val loadedChannels = if (channels.isNotEmpty()) channels
                else runCatching { CatalogRepository.loadChannels() }.getOrDefault(emptyList())
            val schedule = runCatching { EpgRepository.load(this@MainActivity) }.getOrDefault(emptyMap())
            val now = System.currentTimeMillis()
            val entries = loadedChannels.mapNotNull { channel ->
                val programmes = EpgRepository.programmesFor(channel, schedule)
                    .filter { it.endMillis > now }
                    .take(6)
                if (programmes.isEmpty()) null else channel to programmes
            }.sortedBy { it.first.name.lowercase(Locale.ROOT) }

            runOnUiThread {
                if (currentSection != "Ao vivo" || isFinishing || isDestroyed) return@runOnUiThread
                channels = loadedChannels
                guideRows.removeAllViews()
                status.text = if (entries.isEmpty()) {
                    "A programação não está disponível neste momento. Tente novamente quando a conexão voltar."
                } else {
                    "Programação disponível para ${entries.size} canais · horários de Brasília."
                }
                entries.forEach { (channel, programmes) ->
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = focusBackground(PANEL)
                        setPadding(d(16), d(12), d(16), d(12))
                        isFocusable = channel.sources.isNotEmpty()
                        isFocusableInTouchMode = channel.sources.isNotEmpty()
                        if (channel.sources.isNotEmpty()) setOnClickListener {
                            startActivity(Intent(this@MainActivity, PlayerActivity::class.java).apply {
                                putStringArrayListExtra(PlayerActivity.EXTRA_SOURCES, ArrayList(channel.sources))
                                putExtra(PlayerActivity.EXTRA_URL, channel.primarySource)
                                putExtra(PlayerActivity.EXTRA_TITLE, channel.name)
                            })
                        }
                    }
                    card.addView(label(channel.name, 17, WHITE, true).apply { setPadding(d(0), d(0), d(0), d(6)) })
                    programmes.forEach { programme ->
                        val current = now >= programme.startMillis && now < programme.endMillis
                        val line = "${EpgRepository.timeLabel(programme.startMillis)}–${EpgRepository.timeLabel(programme.endMillis)}  •  ${programme.title}"
                        card.addView(label(if (current) "AGORA  •  $line" else line, 14, if (current) RED else WHITE, current).apply {
                            setPadding(d(0), d(3), d(0), d(3))
                        })
                        if (current && programme.description.isNotBlank()) {
                            card.addView(label(programme.description.take(180), 12, MUTED, false).apply {
                                setPadding(d(0), d(0), d(0), d(3))
                            })
                        }
                    }
                    guideRows.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(8) })
                }
            }
        }
    }

    private fun renderChannelRows(
        items: List<TvChannel>,
        rowsContainer: LinearLayout,
        status: TextView,
        query: String
    ) {
        rowsContainer.removeAllViews()
        val term = query.trim().lowercase()
        val filtered = if (term.isBlank()) items else items.filter {
            it.name.lowercase().contains(term) || it.category.lowercase().contains(term)
        }
        status.text = if (term.isBlank()) {
            "${filtered.size} canais disponíveis"
        } else {
            "${filtered.size} resultado(s) para “${query.trim()}”"
        }
        if (filtered.isEmpty()) {
            rowsContainer.addView(label("Nenhum canal encontrado. Tente outro nome ou categoria.", 16, MUTED, false).apply {
                setPadding(d(2), d(22), d(0), d(22))
            })
            return
        }
        if (filtered.size > 80) {
            rowsContainer.addView(label("Mostrando até 10 canais por categoria e 8 categorias para manter a TV fluida. Use a busca para localizar outros canais.", 13, MUTED, false).apply {
                setPadding(d(2), d(0), d(0), d(8))
            })
        }
        // Limita a quantidade de Views criada em um único frame. A lista completa
        // continua disponível pela busca, mas não inflamos milhares de cards de uma vez.
        val grouped = filtered.groupBy { it.category }.toSortedMap()
        val visibleGroups = grouped.entries.take(8)
        visibleGroups.forEach { (category, list) ->
            rowsContainer.addView(label(category, 19, WHITE, true).apply { setPadding(d(0), d(14), d(0), d(8)) })
            val rowScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            list.take(10).forEach { channel ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(d(16), d(12), d(16), d(12))
                    background = focusBackground(PANEL)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    val topLine = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    topLine.addView(label("AO VIVO", 10, RED, true), LinearLayout.LayoutParams(0, -2, 1f))
                    val heart = label(if (favoriteNames.contains(channel.name)) "♥" else "♡", 22,
                        if (favoriteNames.contains(channel.name)) RED else MUTED, true).apply {
                        isFocusable = true
                        setPadding(d(10), d(0), d(2), d(0))
                        contentDescription = "Adicionar ou remover ${channel.name} dos favoritos"
                        setOnClickListener {
                            toggleFavorite(channel)
                            text = if (favoriteNames.contains(channel.name)) "♥" else "♡"
                            setTextColor(if (favoriteNames.contains(channel.name)) RED else MUTED)
                            if (currentSection == "Minha lista") showMyList()
                        }
                    }
                    topLine.addView(heart)
                    addView(topLine)
                    channel.logo?.let { logoUrl ->
                        val logoView = ImageView(this@MainActivity).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = "Logo de ${channel.name}"
                            load(logoUrl) {
                                crossfade(true)
                                placeholder(android.graphics.drawable.ColorDrawable(0xFF242424.toInt()))
                                error(android.graphics.drawable.ColorDrawable(0xFF242424.toInt()))
                            }
                        }
                        addView(logoView, LinearLayout.LayoutParams(d(56), d(40)).apply { topMargin = 4 })
                    }
                    addView(label(channel.name, 16, WHITE, true).apply { setPadding(d(0), d(6), d(0), d(0)) })
                    addView(label(
                        when (channel.sources.size) {
                            0 -> "Sem fonte"
                            1 -> "1 fonte disponível"
                            else -> "${channel.sources.size} fontes disponíveis"
                        }, 12, MUTED, false
                    ).apply { setPadding(d(0), d(4), d(0), d(0)) })
                    setOnClickListener {
                        if (channel.sources.isEmpty()) {
                            status.text = "Este canal não possui uma fonte disponível."
                        } else {
                            startActivity(Intent(this@MainActivity, PlayerActivity::class.java).apply {
                                putStringArrayListExtra(PlayerActivity.EXTRA_SOURCES, ArrayList(channel.sources))
                                putExtra(PlayerActivity.EXTRA_URL, channel.primarySource)
                                putExtra(PlayerActivity.EXTRA_TITLE, channel.name)
                            })
                        }
                    }
                }
                val lp = LinearLayout.LayoutParams(d(230), d(100))
                lp.marginEnd = d(12)
                row.addView(card, lp)
            }
            rowScroll.addView(row)
            rowsContainer.addView(rowScroll, LinearLayout.LayoutParams(-1, d(110)))
        }
    }

    override fun onResume() {
        super.onResume()
        otaUpdateManager?.resumeInstallIfReady()
    }

    override fun onDestroy() {
        otaUpdateManager?.close()
        mainHandler.removeCallbacks(clockTick)
        mainHandler.removeCallbacks(homeHeroTick)
        io.shutdownNow()
        super.onDestroy()
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(color)
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            letterSpacing = if (bold && size >= 12) 0.015f else 0f
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun focusBackground(color: Int = PANEL): android.graphics.drawable.Drawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = d(12.toInt()).toFloat()
            setStroke(d(2), 0xFF4A4A4A.toInt())
        }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && showingMediaDetails) {
            showingMediaDetails = false
            showSection(currentSection)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && currentSection != "Início") {
            showSection("Início")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
