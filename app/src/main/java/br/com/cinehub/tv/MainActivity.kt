package br.com.cinehub.tv

import android.content.Intent
import java.util.concurrent.Executors

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
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
    private var channels: List<TvChannel> = emptyList()
    private var currentSection = "Início"
    private val favoriteNames = linkedSetOf<String>()
    private val mainHandler = Handler(android.os.Looper.getMainLooper())
    private var clockView: TextView? = null
    private val clockTick = object : Runnable { override fun run() { clockView?.text = SimpleDateFormat("HH:mm", Locale("pt", "BR")).apply { timeZone = TimeZone.getTimeZone("America/Sao_Paulo") }.format(Date()); mainHandler.postDelayed(this, 30_000L) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        favoriteNames.addAll(getSharedPreferences("cinehub-tv", MODE_PRIVATE).getStringSet("favorite_channels", emptySet()).orEmpty())
        buildHome()
    }

    private fun buildHome() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(d(42), d(20), d(42), d(20))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label("Cine", 28, WHITE, true))
        header.addView(label("HUB", 28, RED, true))
        val spacer = View(this)
        header.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        clockView = label("--:--", 18, WHITE, true)
        header.addView(clockView!!)
        clockTick.run()
        root.addView(header, LinearLayout.LayoutParams(-1, d(54)))

        val navScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sections.forEach { title ->
            val item = label(title, 15, if (title == currentSection) WHITE else MUTED, title == currentSection).apply {
                setPadding(d(16), d(11), d(16), d(11))
                isFocusable = true
                isFocusableInTouchMode = true
                background = navBackground(title == currentSection, false)
                setOnClickListener { showSection(title) }
                setOnFocusChangeListener { view, focused ->
                    view.background = navBackground(title == currentSection, focused)
                    setTextColor(if (title == currentSection || focused) WHITE else MUTED)
                    setTypeface(typeface, if (title == currentSection) Typeface.BOLD else Typeface.NORMAL)
                }
            }
            nav.addView(item)
        }
        navScroll.addView(nav)
        root.addView(navScroll, LinearLayout.LayoutParams(-1, d(54)))

        val scroll = ScrollView(this).apply { clipChildren = false; clipToPadding = false }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(d(0), d(20), d(0), d(28))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        showSection("Início")
    }

    private fun showSection(section: String) {
        currentSection = section
        refreshNavigationSelection()
        content.removeAllViews()
        when (section) {
            "Ao vivo" -> { showLiveChannels(); return }
            "Buscar" -> { showCatalogSearch(); return }
            "Minha lista" -> { showMyList(); return }
            "Ajustes" -> { showSettings(); return }
            "Rádio" -> { showInfo("Rádio", "A integração das rádios entra na próxima etapa."); return }
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
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(d(28), d(24), d(28), d(24))
            background = focusBackground(0xFF241012.toInt())
        }
        hero.addView(label("CINEHUB TV  •  SUA CENTRAL DE FILMES", 13, RED, true))
        hero.addView(label("Seu próximo universo começa aqui", 30, WHITE, true).apply {
            setPadding(d(0), d(10), d(0), d(8))
        })
        hero.addView(label("Filmes, séries, animes e doramas com informações e capas online.", 16, MUTED, false))
        content.addView(hero, LinearLayout.LayoutParams(-1, d(210)))
        content.addView(label("Explore o CineHUB", 22, WHITE, true).apply { setPadding(d(2), d(20), d(0), d(8)) })
        val grid = android.widget.GridLayout(this).apply { columnCount = 3; rowCount = 2; alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS }
        listOf("Filmes", "Séries", "Doramas", "Animes", "Rádio", "Ao vivo").forEachIndexed { index, name ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(d(16), d(10), d(16), d(10)); background = focusBackground(PANEL)
                isFocusable = true; isFocusableInTouchMode = true
                addView(label("CINEHUB", 10, RED, true))
                addView(label(name, 19, WHITE, true).apply { setPadding(0, d(5), 0, d(3)) })
                addView(label(if (name == "Ao vivo") "Canais e programação" else if (name == "Rádio") "Estações online" else "Ver catálogo", 12, MUTED, false))
                setOnClickListener { showSection(name) }
            }
            val lp = android.widget.GridLayout.LayoutParams().apply { width = 0; height = d(96); columnSpec = android.widget.GridLayout.spec(index % 3, 1f); rowSpec = android.widget.GridLayout.spec(index / 3); setMargins(d(5), d(5), d(5), d(5)) }
            grid.addView(card, lp)
        }
        content.addView(grid, LinearLayout.LayoutParams(-1, d(216)))
        content.addView(label("Os títulos, capas e sinopses são consultados online pelo Supabase.", 13, MUTED, false).apply {
            setPadding(d(2), d(22), d(0), d(0))
        })
    }

    private fun showMediaSection(section: String) {
        content.removeAllViews()
        val headline = when (section) {
            "Filmes" -> "Qual é o filme de hoje?"
            "Séries" -> "Sua próxima maratona começa aqui"
            "Animes" -> "Entre em um novo mundo"
            else -> "Histórias para acompanhar"
        }
        content.addView(label(section.uppercase(), 12, RED, true).apply { setPadding(d(0), d(8), d(0), d(4)) })
        content.addView(label(headline, 28, WHITE, true).apply { setPadding(d(0), d(0), d(0), d(8)) })
        content.addView(label(when (section) {
            "Filmes" -> "Lançamentos, tendências e informações atualizadas online."
            "Séries" -> "Descubra séries e encontre informações, capas e sinopses."
            "Animes" -> "Animações e produções japonesas encontradas no catálogo online."
            else -> "Dramas coreanos e outras produções coreanas disponíveis no catálogo."
        }, 14, MUTED, false).apply { setPadding(d(0), d(0), d(0), d(14)) })

        val searchLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val search = EditText(this).apply {
            hint = when (section) {
                "Filmes" -> "Buscar filme…"
                "Séries" -> "Buscar série…"
                "Animes" -> "Buscar anime…"
                else -> "Buscar dorama…"
            }
            setSingleLine(true)
            textSize = 15f
            setTextColor(WHITE)
            setHintTextColor(MUTED)
            setPadding(d(16), d(8), d(16), d(8))
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = focusBackground(PANEL)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        searchLine.addView(search, LinearLayout.LayoutParams(0, d(50), 1f))
        val searchButton = label("BUSCAR", 13, WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(d(18), d(12), d(18), d(12))
            background = focusBackground(RED)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        searchLine.addView(searchButton, LinearLayout.LayoutParams(-2, d(50)).apply { marginStart = d(10) })
        content.addView(searchLine)
        val status = label("Conectando ao catálogo online…", 14, MUTED, false).apply {
            setPadding(d(2), d(14), d(0), d(10))
        }
        content.addView(status)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results)

        fun runSearch(query: String) {
            status.text = if (query.isBlank()) "Atualizando catálogo online…" else "Buscando “$query”…"
            searchButton.isEnabled = false
            io.execute {
                try {
                    val items = if (query.isBlank()) OnlineCatalogRepository.feed(section)
                        else OnlineCatalogRepository.search(query, section)
                    runOnUiThread {
                        if (currentSection != section) return@runOnUiThread
                        status.text = if (items.isEmpty()) {
                            if (query.isBlank()) "Nenhum título encontrado nesta área. Tente buscar pelo nome."
                            else "Nenhum resultado encontrado para “$query”."
                        } else "${items.size} títulos encontrados"
                        searchButton.isEnabled = true
                        renderMediaCards(items, results)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        if (currentSection != section) return@runOnUiThread
                        status.text = "Não foi possível conectar ao catálogo online. Tente novamente."
                        searchButton.isEnabled = true
                    }
                }
            }
        }
        searchButton.setOnClickListener { runSearch(search.text.toString()) }
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { runSearch(search.text.toString()); true } else false
        }
        runSearch("")
    }

    private fun renderMediaCards(items: List<MediaItem>, container: LinearLayout) {
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(label("Quando o catálogo responder, os títulos aparecerão aqui com suas capas.", 14, MUTED, false).apply {
                setPadding(d(2), d(12), d(0), d(18))
            })
            return
        }
        container.addView(label(if (currentSection == "Filmes") "Em destaque" else "Descobertas para você", 20, WHITE, true).apply {
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
                setOnClickListener { showMediaDetails(item) }
            }
            row.addView(card, LinearLayout.LayoutParams(d(164), d(278)).apply { marginEnd = d(14) })
        }
        scroll.addView(row)
        container.addView(scroll, LinearLayout.LayoutParams(-1, d(306)))
    }

    private fun isMediaFavorite(item: MediaItem): Boolean =
        getSharedPreferences("cinehub-tv", MODE_PRIVATE).getStringSet("favorite_media", emptySet()).orEmpty().contains(item.id)

    private fun toggleMediaFavorite(item: MediaItem) {
        val prefs = getSharedPreferences("cinehub-tv", MODE_PRIVATE)
        val saved = prefs.getStringSet("favorite_media", emptySet()).orEmpty().toMutableSet()
        if (!saved.add(item.id)) saved.remove(item.id)
        prefs.edit().putStringSet("favorite_media", saved).apply()
        android.widget.Toast.makeText(this,
            if (saved.contains(item.id)) "Adicionado à Minha Lista" else "Removido da Minha Lista",
            android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun showMediaDetails(item: MediaItem) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(d(20), d(18), d(20), d(12))
            gravity = Gravity.TOP
            setBackgroundColor(0xFF101010.toInt())
        }
        val poster = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            item.poster?.let { load(it) { crossfade(true) } }
        }
        panel.addView(poster, LinearLayout.LayoutParams(d(176), d(264)).apply { marginEnd = d(20) })
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(label(when {
            item.type == "movie" -> "FILME"
            currentSection == "Animes" -> "ANIME"
            currentSection == "Doramas" -> "DORAMA"
            else -> "SÉRIE"
        }, 12, RED, true))
        info.addView(label(item.title, 25, WHITE, true).apply { setPadding(0, d(6), 0, d(8)) })
        info.addView(label("${item.year.ifBlank { "Ano não informado" }}   •   ★ ${item.rating}", 14, MUTED, true))
        info.addView(label(item.description.ifBlank { "Sinopse indisponível para este título." }, 15, WHITE, false).apply {
            setPadding(0, d(14), 0, d(18))
        })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val favorite = label(if (isMediaFavorite(item)) "♥ Na Minha Lista" else "♡ Favoritar", 14,
            if (isMediaFavorite(item)) RED else WHITE, true).apply {
            setPadding(d(14), d(12), d(14), d(12))
            background = focusBackground(PANEL)
            isFocusable = true; isFocusableInTouchMode = true
            setOnClickListener {
                toggleMediaFavorite(item)
                text = if (isMediaFavorite(item)) "♥ Na Minha Lista" else "♡ Favoritar"
                setTextColor(if (isMediaFavorite(item)) RED else WHITE)
            }
        }
        val watch = label("▶  Assistir", 14, WHITE, true).apply {
            setPadding(d(16), d(12), d(16), d(12))
            background = focusBackground(RED)
            isFocusable = true; isFocusableInTouchMode = true
            setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Reprodução")
                    .setMessage("Este título ainda não tem uma fonte de reprodução vinculada ao catálogo do CineHUB TV.")
                    .setPositiveButton("Entendi", null).show()
            }
        }
        actions.addView(watch)
        actions.addView(favorite, LinearLayout.LayoutParams(-2, -2).apply { marginStart = d(10) })
        info.addView(actions)
        panel.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(panel)
            .setNegativeButton("Fechar", null)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener {
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), -2)
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), -2)
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
                .apply()
            status.text = "Minha lista foi limpa."
        }
        action("Sobre o CineHUB TV", "Versão nativa 0.2.0 • Interface Android/Kotlin • Player Media3") {
            status.text = "CineHUB TV está em reconstrução nativa. Esta versão ainda é de desenvolvimento."
        }
    }

    private fun showInfo(title: String, message: String) {
        content.removeAllViews()
        content.addView(label(title, 26, WHITE, true).apply { setPadding(d(0), d(18), d(0), d(12)) })
        content.addView(label(message, 16, MUTED, false))
    }

    private fun showCatalogSearch() {
        content.removeAllViews()
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
        content.addView(label("Canais ao vivo", 26, WHITE, true).apply {
            setPadding(d(0), d(12), d(0), d(8))
        })
        content.addView(label("Pesquise por nome ou categoria. Se um canal tiver mais de uma fonte, o player tentará alternativas quando necessário.", 14, MUTED, false).apply {
            setPadding(d(0), d(0), d(0), d(14))
        })
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

    override fun onDestroy() {
        mainHandler.removeCallbacks(clockTick)
        io.shutdownNow()
        super.onDestroy()
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun focusBackground(color: Int = PANEL): android.graphics.drawable.Drawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = d(12.toInt()).toFloat()
            setStroke(d(2), 0xFF4A4A4A.toInt())
        }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && currentSection != "Início") {
            showSection("Início")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
