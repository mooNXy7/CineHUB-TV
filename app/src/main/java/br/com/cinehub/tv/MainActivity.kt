package br.com.cinehub.tv

import android.content.Intent
import java.util.concurrent.Executors

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
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
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private val sections = listOf("Início", "Ao vivo", "Filmes", "Séries", "Animes", "Doramas", "Rádio", "Minha lista", "Buscar", "Ajustes")
    private val io = Executors.newSingleThreadExecutor()
    private var channels: List<TvChannel> = emptyList()
    private var currentSection = "Início"
    private val favoriteNames = linkedSetOf<String>()

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
            setPadding(42, 20, 42, 20)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label("Cine", 28, WHITE, true))
        header.addView(label("HUB", 28, RED, true))
        val spacer = View(this)
        header.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(label("BR  •  TV", 14, MUTED, false))
        root.addView(header, LinearLayout.LayoutParams(-1, 54))

        val navScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sections.forEachIndexed { index, title ->
            val item = label(title, 15, if (index == 0) WHITE else MUTED, index == 0).apply {
                setPadding(16, 14, 16, 14)
                isFocusable = true
                isFocusableInTouchMode = true
                background = focusBackground()
                setOnClickListener { showSection(title) }
                setOnFocusChangeListener { view, focused ->
                    view.setBackgroundColor(if (focused) RED else Color.TRANSPARENT)
                    setTextColor(if (focused || index == 0) WHITE else MUTED)
                }
            }
            nav.addView(item)
        }
        navScroll.addView(nav)
        root.addView(navScroll, LinearLayout.LayoutParams(-1, 54))

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 20) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        showSection("Início")
    }

    private fun showSection(section: String) {
        currentSection = section
        content.removeAllViews()
        if (section == "Ao vivo") { showLiveChannels(); return }
        if (section == "Buscar") { showCatalogSearch(); return }
        if (section == "Minha lista") { showMyList(); return }
        if (section == "Rádio") { showInfo("Rádio", "A integração das rádios entra na próxima etapa."); return }
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(28, 24, 28, 24)
            setBackgroundColor(0xFF241012.toInt())
        }
        hero.addView(label("CINEHUB TV  •  SUA CENTRAL DE FILMES", 13, RED, true))
        hero.addView(label(when (section) {
            "Filmes" -> "Qual é o filme de hoje?"
            "Séries" -> "Sua próxima maratona começa aqui"
            "Buscar" -> "Encontre algo para assistir"
            else -> "Entre no seu próximo universo"
        }, 30, WHITE, true).apply { setPadding(0, 10, 0, 8) })
        hero.addView(label("A experiência CineHUB está sendo preparada para sua TV.", 16, MUTED, false))
        content.addView(hero, LinearLayout.LayoutParams(-1, 210))

        content.addView(label(if (section == "Início") "Continue assistindo" else section, 22, WHITE, true).apply {
            setPadding(2, 24, 0, 12)
        })
        val rowScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Destaque CineHUB", "Filmes", "Séries", "Animes", "Doramas").forEachIndexed { i, name ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM
                setPadding(16, 12, 16, 16)
                isFocusable = true
                isFocusableInTouchMode = true
                background = focusBackground(if (i == 0) 0xFF351015.toInt() else PANEL)
                addView(label("CINEHUB", 11, RED, true))
                addView(label(name, 18, WHITE, true).apply { setPadding(0, 8, 0, 0) })
                addView(label("Conteúdo de demonstração", 12, MUTED, false).apply { setPadding(0, 5, 0, 0) })
            }
            val params = LinearLayout.LayoutParams(240, 132)
            params.marginEnd = 14
            row.addView(card, params)
        }
        rowScroll.addView(row)
        content.addView(rowScroll, LinearLayout.LayoutParams(-1, 142))
        content.addView(label("Fundação 0.2.0  •  Interface nativa em construção", 12, MUTED, false).apply {
            setPadding(2, 22, 0, 0)
        })
    }

    private fun showMyList() {
        content.removeAllViews()
        content.addView(label("Minha lista", 28, WHITE, true).apply { setPadding(0, 12, 0, 8) })
        content.addView(label("Seus canais favoritos ficam salvos neste aparelho.", 14, MUTED, false).apply {
            setPadding(0, 0, 0, 14)
        })
        val status = label("", 14, MUTED, false).apply { setPadding(2, 12, 0, 12) }
        content.addView(status)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results)
        if (channels.isEmpty()) {
            status.text = if (favoriteNames.isEmpty()) "Ainda não há favoritos. Abra Ao vivo e toque no coração de um canal." else "Carregando seus canais favoritos…"
            io.execute {
                try {
                    val loaded = CatalogRepository.loadChannels()
                    runOnUiThread {
                        channels = loaded
                        val favorites = loaded.filter { favoriteNames.contains(it.name) }
                        status.text = if (favorites.isEmpty()) "Ainda não há favoritos. Abra Ao vivo e toque no coração de um canal." else "${favorites.size} canal(is) favorito(s)"
                        renderChannelRows(favorites, results, status, "")
                    }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "Não foi possível carregar os favoritos. Verifique a conexão." }
                }
            }
        } else {
            val favorites = channels.filter { favoriteNames.contains(it.name) }
            status.text = if (favorites.isEmpty()) "Ainda não há favoritos. Abra Ao vivo e toque no coração de um canal." else "${favorites.size} canal(is) favorito(s)"
            renderChannelRows(favorites, results, status, "")
        }
    }

    private fun toggleFavorite(channel: TvChannel) {
        if (!favoriteNames.add(channel.name)) favoriteNames.remove(channel.name)
        getSharedPreferences("cinehub-tv", MODE_PRIVATE).edit()
            .putStringSet("favorite_channels", favoriteNames.toSet())
            .apply()
    }

    private fun showInfo(title: String, message: String) {
        content.removeAllViews()
        content.addView(label(title, 26, WHITE, true).apply { setPadding(0, 18, 0, 12) })
        content.addView(label(message, 16, MUTED, false))
    }

    private fun showCatalogSearch() {
        content.removeAllViews()
        content.addView(label("Buscar no CineHUB", 26, WHITE, true).apply {
            setPadding(0, 12, 0, 8)
        })
        content.addView(label("Pesquise no catálogo conectado. Nesta etapa, os resultados disponíveis são canais ao vivo; filmes e séries serão conectados às suas fontes nas próximas fases.", 14, MUTED, false).apply {
            setPadding(0, 0, 0, 14)
        })
        val search = EditText(this).apply {
            hint = "Digite nome do canal ou categoria…"
            setSingleLine(true)
            textSize = 16f
            setTextColor(WHITE)
            setHintTextColor(MUTED)
            setPadding(18, 10, 18, 10)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = focusBackground(PANEL)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        content.addView(search, LinearLayout.LayoutParams(-1, 54))
        val status = label("Preparando pesquisa…", 14, MUTED, false).apply {
            setPadding(2, 12, 0, 12)
        }
        content.addView(status)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (channels.isNotEmpty()) renderChannelRows(channels, results, status, s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        if (channels.isNotEmpty()) {
            renderChannelRows(channels, results, status, search.text.toString())
            return
        }
        io.execute {
            try {
                val loaded = CatalogRepository.loadChannels()
                runOnUiThread {
                    channels = loaded
                    renderChannelRows(loaded, results, status, search.text.toString())
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Falha ao acessar o catálogo. Verifique a conexão e tente novamente." }
            }
        }
    }

    private fun showLiveChannels() {
        content.removeAllViews()
        content.addView(label("Canais ao vivo", 26, WHITE, true).apply {
            setPadding(0, 12, 0, 8)
        })
        content.addView(label("Pesquise por nome ou categoria. Se um canal tiver mais de uma fonte, o player tentará alternativas quando necessário.", 14, MUTED, false).apply {
            setPadding(0, 0, 0, 14)
        })
        val search = EditText(this).apply {
            hint = "Buscar canal…"
            setSingleLine(true)
            textSize = 16f
            setTextColor(WHITE)
            setHintTextColor(MUTED)
            setPadding(18, 10, 18, 10)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = focusBackground(PANEL)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        content.addView(search, LinearLayout.LayoutParams(-1, 54))
        val status = label("Carregando catálogo público…", 14, MUTED, false).apply {
            setPadding(2, 12, 0, 12)
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
                setPadding(2, 22, 0, 22)
            })
            return
        }
        filtered.groupBy { it.category }.toSortedMap().forEach { (category, list) ->
            rowsContainer.addView(label(category, 19, WHITE, true).apply { setPadding(0, 14, 0, 8) })
            val rowScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            list.forEach { channel ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(16, 12, 16, 12)
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
                        setPadding(10, 0, 2, 0)
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
                    addView(label(channel.name, 16, WHITE, true).apply { setPadding(0, 6, 0, 0) })
                    addView(label(
                        when (channel.sources.size) {
                            0 -> "Sem fonte"
                            1 -> "1 fonte disponível"
                            else -> "${channel.sources.size} fontes disponíveis"
                        }, 12, MUTED, false
                    ).apply { setPadding(0, 4, 0, 0) })
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
                val lp = LinearLayout.LayoutParams(230, 100)
                lp.marginEnd = 12
                row.addView(card, lp)
            }
            rowScroll.addView(row)
            rowsContainer.addView(rowScroll, LinearLayout.LayoutParams(-1, 110))
        }
    }

    override fun onDestroy() {
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
            cornerRadius = 12f
            setStroke(2, 0xFF4A4A4A.toInt())
        }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && currentSection != "Início") {
            showSection("Início")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
