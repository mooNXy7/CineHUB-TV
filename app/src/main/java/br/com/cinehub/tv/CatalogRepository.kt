package br.com.cinehub.tv

import java.net.HttpURLConnection
import java.net.URL

data class TvChannel(
    val name: String,
    val category: String,
    val logo: String?,
    val sources: List<String>
) {
    val primarySource: String? get() = sources.firstOrNull()
}

object CatalogRepository {
    // Catálogo público de referência; a integração permanece desacoplada da interface.
    private const val CATALOG_URL =
        "https://raw.githubusercontent.com/gabrielsaimo/SaimoPlayer/main/catalogo.txt"

    fun loadChannels(): List<TvChannel> {
        val connection = (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "CineHUB-TV/0.1")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Catálogo respondeu HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(raw: String): List<TvChannel> {
        val result = mutableListOf<TvChannel>()
        var name = ""
        var category = ""
        var logo: String? = null
        var sources = mutableListOf<String>()

        fun flush() {
            if (name.isNotBlank() && sources.isNotEmpty()) {
                result.add(TvChannel(name.trim(), category.trim().ifBlank { "Outros" }, logo, sources.distinct()))
            }
            name = ""
            category = ""
            logo = null
            sources = mutableListOf()
        }

        raw.lineSequence().forEach { original ->
            val line = original.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val split = line.indexOf(':')
            if (split <= 0) return@forEach
            val key = line.substring(0, split).trim().lowercase()
            val value = line.substring(split + 1).trim()
            when (key) {
                "canal" -> { flush(); name = value }
                "categoria" -> category = value
                "logo" -> logo = value.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                "fonte" -> if (value.startsWith("https://") || value.startsWith("http://")) sources.add(value)
            }
        }
        flush()
        return result.distinctBy { it.name.lowercase() }.sortedBy { it.name.lowercase() }
    }
}
