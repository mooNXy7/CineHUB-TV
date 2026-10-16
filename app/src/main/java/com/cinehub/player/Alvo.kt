package com.cinehub.player

import android.content.Intent

data class Alvo(
    val titulo: String,
    val serie: Boolean,
    val letra: String,
    val ano: String = "",
    val colecao: String = "",
    val tmdbId: Int = 0,
) {
    /// O filme já traz o ano no nome ("O Fim da Rua (2026)"); a série o
    /// guarda à parte. Só se acrescenta quando o nome ainda não tem.
    val nomeCompleto: String get() =
        if (ano.isBlank() || titulo.trimEnd().endsWith("($ano)")) titulo else "$titulo ($ano)"

    fun em(intent: Intent): Intent = intent
        .putExtra(TITULO, titulo).putExtra(SERIE, serie).putExtra(LETRA, letra)
        .putExtra(ANO, ano).putExtra(COLECAO, colecao).putExtra(TMDB, tmdbId)

    companion object {
        private const val TITULO = "alvo.titulo"
        private const val SERIE = "alvo.serie"
        private const val LETRA = "alvo.letra"
        private const val ANO = "alvo.ano"
        private const val COLECAO = "alvo.colecao"
        private const val TMDB = "alvo.tmdb"

        fun de(intent: Intent?): Alvo? {
            val i = intent ?: return null
            val titulo = i.getStringExtra(TITULO)
                ?: i.getStringExtra("title")
                ?: i.getStringExtra("content_title")
                ?: i.getStringExtra("radio_name")
                ?: i.getStringExtra("channel_name")
                ?: i.getStringExtra("content_id")
                ?: i.getStringExtra("url")?.substringAfterLast('/')?.substringBefore('?')
                ?: intent?.dataString?.substringAfterLast('/')?.substringBefore('?')
                ?: return null
            return Alvo(
                titulo = titulo,
                serie = i.getBooleanExtra(SERIE, intent.getStringExtra("content_type").orEmpty().lowercase() in setOf("series", "serie", "tv", "anime", "dorama")),
                letra = intent.getStringExtra(LETRA).orEmpty(),
                ano = intent.getStringExtra(ANO).orEmpty(),
                colecao = intent.getStringExtra(COLECAO).orEmpty(),
                tmdbId = i.getIntExtra(TMDB, i.getIntExtra("tmdb_id", 0)),
            )
        }
    }
}

