package br.com.cinehub.tv

import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale

data class RadioStation(
    val name: String,
    val streamUrl: String,
    val logoUrl: String?
)

object SaimoContentRepository {
    private const val BASE = "https://raw.githubusercontent.com/gabrielsaimo/SaimoPlayer/main/"
    private val urlRegex = Regex("""https?://[^\s|,<>"]+""", RegexOption.IGNORE_CASE)

    fun loadRadios(): List<RadioStation> {
        val raw = download("vod/radios.txt")
        val result = mutableListOf<RadioStation>()
        raw.lineSequence().forEach { line ->
            val parts = line.trim().split("|")
            if (parts.size >= 2) {
                val name = parts[0].trim()
                val stream = parts[1].trim()
                val logo = parts.getOrNull(2)?.trim()?.takeIf { it.startsWith("http") }
                if (name.isNotBlank() && stream.startsWith("http")) {
                    result.add(RadioStation(name, stream, logo))
                }
            }
        }
        return result.distinctBy { normalize(it.name) }.sortedBy { it.name.lowercase(Locale("pt", "BR")) }
    }

    /**
     * First-pass resolver for direct files in Saimo's VOD index.
     * It deliberately returns only URLs suitable for a native Media3 player;
     * metadata IDs and web-page embed URLs are not treated as playable streams.
     */
    fun findDirectPlayback(title: String, isMovie: Boolean): List<String> {
        val clean = title.trim()
        if (clean.isBlank()) return emptyList()
        val first = clean.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '#'
        val letter = if (first in 'A'..'Z') first.toString() else "#"
        val candidates = if (isMovie) {
            listOf("vod/filmes-$letter.txt", "vod/filmes-$letter.txt".replace("#", "%23"))
        } else {
            (0..12).map { "vod/series-$letter-$it.txt" } + "vod/series-$letter.txt"
        }
        val wanted = normalize(clean)
        val matches = linkedSetOf<String>()
        for (path in candidates.distinct()) {
            val raw = try { download(path) } catch (_: Exception) { continue }
            for (line in raw.lineSequence()) {
                val titlePart = line.substringBefore('\t').trim()
                if (titlePart.isBlank()) continue
                val actual = normalize(titlePart)
                if (actual != wanted && !actual.startsWith(wanted) && !wanted.startsWith(actual)) continue
                urlRegex.findAll(line).map { it.value.trimEnd('.', ';', ')', ']') }.forEach { url ->
                    val lower = url.lowercase(Locale.ROOT)
                    if (lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".mkv") ||
                        lower.contains(".mpd") || lower.contains(".ts") || lower.contains("/stream") ||
                        lower.contains("/live") || lower.contains("master.txt")) {
                        if (!lower.contains("youtube.com") && !lower.contains("youtu.be")) matches.add(url)
                    }
                }
                if (matches.isNotEmpty()) return matches.toList()
            }
        }
        return matches.toList()
    }

    private fun download(path: String): String {
        val encodedPath = path.replace("#", "%23")
        val connection = (URL(BASE + encodedPath).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 10000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "CineHUB-TV/0.3")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Saimo content HTTP ${connection.responseCode}: $path")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun normalize(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT).replace(Regex("""\(\d{4}\)"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim(),
        Normalizer.Form.NFD
    ).replace(Regex("""\p{M}+"""), "").replace(Regex("""\s+"""), " ")
}
