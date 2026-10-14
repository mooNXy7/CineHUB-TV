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
        header.addView(label("BR  •  TV", 14, MUTED, false))
        root.addView(header, LinearLayout.LayoutParams(-1, d(54)))

        val navScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sections.forEachIndexed { index, title ->
            val item = label(title, 15, if (index == 0) WHITE else MUTED, index == 0).apply {
                setPadding(d(16), d(14), d(16), d(14))
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
        root.addView(navScroll, LinearLayout.LayoutParams(-1, d(54)))

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(0), d(16), d(0), d(20)) }
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
        if (section == "Ajustes") { showSettings(); return }
        if (section == "Rádio") { showInfo("Rádio", "A integração das rádios entra na próxima etapa."); return }
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(d(28), d(24), d(28), d(24))
            setBackgroundColor(0xFF241012.toInt())
        }
        hero.addView(label("CINEHUB TV  •  SUA CENTRAL DE FILMES", 13, RED, true))
        hero.addView(label(when (section) {
            "Filmes" -> "Qual é o filme de hoje?"
            "Séries" -> "Sua próxima maratona começa aqui"
            "Buscar" -> "Encontre algo para assistir"
            else -> "Entre no seu próximo universo"
        }, 30, WHITE, true).apply { setPadding(d(0), d(10), d(0), d(8)) })
        hero.addView(label("A experiência CineHUB está sendo preparada para sua TV.", 16, MUTED, false))
        content.addView(hero, LinearLayout.LayoutParams(-1, d(210)))

        content.addView(label(if (section == "Início") "Continue assistindo" else section, 22, WHITE, true).apply {
            setPadding(d(2), d(24), d(0), d(12))
        })
        val rowScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Destaque CineHUB", "Filmes", "Séries", "Animes", "Doramas").forEachIndexed { i, name ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM
                setPadding(d(16), d(12), d(16), d(16))
                isFocusable = true
                isFocusableInTouchMode = true
                background = focusBackground(if (i == 0) 0xFF351015.toInt() else PANEL)
                addView(label("CINEHUB", 11, RED, true))
                addView(label(name, 18, WHITE, true).apply { setPadding(d(0), d(8), d(0), d(0)) })
                addView(label("Conteúdo de demonstração", 12, MUTED, false).apply { setPadding(d(0), d(5), d(0), d(0)) })
            }
            val params = LinearLayout.LayoutParams(d(240), d(132))
            params.marginEnd = d(14)
            row.addView(card, params)
        }
        rowScroll.addView(row)
        content.addView(rowScroll, LinearLayout.LayoutParams(-1, d(142)))
        content.addView(label("Fundação 0.2.0  •  Interface nativa em construção", 12, MUTED, false).apply {
            setPadding(d(2), d(22), d(0), d(0))
        })
    }

    private fun showMyList() {
        content.removeAllViews()
        content.addView(label("Minha lista", 28, WHITE, true).apply { setPadding(d(0), d(12), d(0), d(8)) })
        content.addView(label("Seus canais favoritos ficam salvos neste aparelho.", 14, MUTED, false).apply {
            setPadding(d(0), d(0), d(0), d(14))
        })
        val status = label("", 14, MUTED, false).apply { setPadding(d(2), d(12), d(0), d(12)) }
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
        content.addView(label("Pesquise no catálogo conectado. Nesta etapa, os resultados disponíveis são canais ao vivo; filmes e séries serão conectados às suas fontes nas próximas fases.", 14, MUTED, false).apply {
            setPadding(d(0), d(0), d(0), d(14))
        })
        val search = EditText(this).apply {
            hint = "Digite nome do canal ou categoria…"
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
        val status = label("Preparando pesquisa…", 14, MUTED, false).apply {
            setPadding(d(2), d(12), d(0), d(12))
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
