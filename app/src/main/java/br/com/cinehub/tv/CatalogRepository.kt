package br.com.cinehub.tv

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class TvChannel(
    val name: String,
    val category: String,
    val logo: String?,
    val sources: List<String>,
    val acervoId: String? = null,
    val epgId: String? = null
) {
    val primarySource: String? get() = sources.firstOrNull()
}

object CatalogRepository {
    private const val REFERENCE_URL =
        "https://raw.githubusercontent.com/gabrielsaimo/SaimoPlayer/main/catalogo.txt"
    private const val ACERVO_URL =
        "https://raw.githubusercontent.com/mooNXy7/CineHUB-ACERVO/main/catalog/channels/channels.json"

    fun loadChannels(): List<TvChannel> {
        val pool = Executors.newFixedThreadPool(2)
        return try {
            val referenceFuture = pool.submit<List<TvChannel>> { loadReferenceChannels() }
            val acervoFuture = pool.submit<List<TvChannel>> { loadAcervoChannels() }
            val reference = runCatching { referenceFuture.get(20, TimeUnit.SECONDS) }.getOrDefault(emptyList())
            val acervo = runCatching { acervoFuture.get(20, TimeUnit.SECONDS) }.getOrDefault(emptyList())
            if (reference.isEmpty() && acervo.isEmpty()) {
                throw IllegalStateException("Nenhuma fonte de canais respondeu.")
            }
            mergeCatalogs(reference, acervo)
        } finally {
            pool.shutdownNow()
        }
    }

    private fun loadReferenceChannels(): List<TvChannel> =
        parse(downloadText(REFERENCE_URL, "CineHUB-TV/1.3.2", 8000, 12000))

    private fun loadAcervoChannels(): List<TvChannel> {
        val root = JSONObject(downloadText(ACERVO_URL, "CineHUB-TV/1.3.2", 6000, 12000))
        val array = root.optJSONArray("channels") ?: return emptyList()
        val result = mutableListOf<TvChannel>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name").trim()
            if (name.isBlank()) continue
            val sources = mutableListOf<String>()
            val sourceArray = item.optJSONArray("sources")
            if (sourceArray != null) for (j in 0 until sourceArray.length()) {
                val source = sourceArray.optJSONObject(j) ?: continue
                val url = source.optString("url").trim()
                if (url.startsWith("https://") || url.startsWith("http://")) sources.add(url)
            }
            result.add(TvChannel(
                name = name,
                category = item.optString("group", "Outros").ifBlank { "Outros" },
                logo = item.optString("logo_url").takeIf { it.startsWith("https://") || it.startsWith("http://") },
                sources = sources.distinct(),
                acervoId = item.optString("id").takeIf { it.isNotBlank() },
                epgId = item.optString("epg_id").takeIf { it.isNotBlank() }
            ))
        }
        return result
    }

    private fun mergeCatalogs(reference: List<TvChannel>, acervo: List<TvChannel>): List<TvChannel> {
        if (acervo.isEmpty()) return reference
        if (reference.isEmpty()) return acervo.filter { it.sources.isNotEmpty() }.distinctBy { canonical(it.name) }
        val acervoByName = acervo.associateBy { canonical(it.name) }
        val merged = reference.map { existing ->
            val metadata = acervoByName[canonical(existing.name)] ?: return@map existing
            existing.copy(
                category = metadata.category.ifBlank { existing.category },
                logo = metadata.logo ?: existing.logo,
                acervoId = metadata.acervoId,
                epgId = metadata.epgId
            )
        }
        val known = merged.mapTo(mutableSetOf()) { canonical(it.name) }
        val playableExtras = acervo.filter { it.sources.isNotEmpty() && canonical(it.name) !in known }
        return (merged + playableExtras).distinctBy { canonical(it.name) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    private fun downloadText(url: String, userAgent: String, connectTimeout: Int, readTimeout: Int): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            this.connectTimeout = connectTimeout
            this.readTimeout = readTimeout
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Catálogo respondeu HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
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
            name = ""; category = ""; logo = null; sources = mutableListOf()
        }
        raw.lineSequence().forEach { original ->
            val line = original.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val split = line.indexOf(':')
            if (split <= 0) return@forEach
            val key = line.substring(0, split).trim().lowercase(Locale.ROOT)
            val value = line.substring(split + 1).trim()
            when (key) {
                "canal" -> { flush(); name = value }
                "categoria" -> category = value
                "logo" -> logo = value.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                "fonte" -> if (value.startsWith("https://") || value.startsWith("http://")) sources.add(value)
            }
        }
        flush()
        return result.distinctBy { canonical(it.name) }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    private fun canonical(value: String): String {
        val plain = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("""\b(?:hd|sd|fhd|uhd|4k|br)\b"""), " ")
        return plain.replace(Regex("""[^a-z0-9]+"""), " ").trim().replace(Regex("""\s+"""), " ")
    }
}
