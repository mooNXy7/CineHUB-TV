package br.com.cinehub.tv

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
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
        val brand = label("Cine", 28, WHITE, true)
        val hub = label("HUB", 28, RED, true)
        header.addView(brand)
        header.addView(hub)
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
        content.removeAllViews()
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(28, 24, 28, 24)
            setBackgroundColor(0xFF241012.toInt())
        }
        hero.addView(label("CINEHUB TV  •  SUA CENTRAL DE FILMES", 13, RED, true))
        hero.addView(label(when (section) {
            "Ao vivo" -> "O que está passando agora?"
            "Filmes" -> "Qual é o filme de hoje?"
            "Séries" -> "Sua próxima maratona começa aqui"
            "Rádio" -> "Sua trilha sonora"
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
                setBackgroundColor(if (i == 0) 0xFF351015.toInt() else PANEL)
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
        content.addView(label("Fundação 0.1.0  •  Interface nativa em construção", 12, MUTED, false).apply {
            setPadding(2, 22, 0, 0)
        })
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun focusBackground(color: Int = PANEL): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = 12f
            setStroke(2, 0xFF4A4A4A.toInt())
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (content.childCount > 0) {
                showSection("Início")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
