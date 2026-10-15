package br.com.cinehub.tv

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Leitor do acervo VOD publicado pelo SaimoPlayer.
 * Lê arquivos sob demanda e mantém cache local por seis horas, sem exigir
 * downloads do catálogo inteiro nem apagar uma cópia válida quando a rede cai.
 */
object SaimoVodRepository {
    private const val BASE = "https://raw.githubusercontent.com/gabrielsaimo/SaimoPlayer/main/vod/"
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    private val memory = ConcurrentHashMap<String, String>()

    data class Episode(
        val season: Int,
        val number: Int,
        val language: String,
        val sources: List<String>
    ) {
        val label: String get() = "T$season • E$number" + if (language.equals("leg", true)) " LEG" else " DUB"
    }

    fun findDirectPlayback(
        context: Context,
        title: String,
        isMovie: Boolean,
        section: String = "Séries",
        tmdbId: String? = null
    ): List<String> {
        if (title.isBlank()) return emptyList()
        val sourceBases = bases(context)
        if (isMovie) {
            val byId = tmdbId?.takeIf { it.isNotBlank() }?.let { movieByTmdbId(context, it, sourceBases) }.orEmpty()
            if (byId.isNotEmpty()) return byId
            return movieByTitle(context, title, sourceBases)
        }
        return findEpisodes(context, title, section, tmdbId).firstOrNull()?.sources.orEmpty()
    }

    /** Episódios reais da série; animes e doramas usam seus índices próprios. */
    fun findEpisodes(
        context: Context,
        title: String,
        section: String,
        tmdbId: String? = null
    ): List<Episode> {
        if (title.isBlank()) return emptyList()
        val sourceBases = bases(context)
        val collectionType = when {
            section.equals("Animes", true) -> "animes"
            section.equals("Doramas", true) -> "doramas"
            else -> null
        }
        if (collectionType != null) {
            val episodes = collectionEpisodes(context, collectionType, title, tmdbId, sourceBases)
            if (episodes.isNotEmpty()) return episodes
        }
        return seriesEpisodes(context, title, sourceBases)
    }

    private fun movieByTmdbId(context: Context, id: String, sourceBases: Map<Int, String>): List<String> {
        val text = fileText(context, "redeflix/links-filmes.txt") ?: return emptyList()
        for (line in text.lineSequence()) {
            if (line.split('\t').none { it.trim() == "tmdb=$id" }) continue
            val sources = parseSourceFields(line.split('\t').drop(1), sourceBases)
            if (sources.isNotEmpty()) return sources
        }
        return emptyList()
    }

    private fun movieByTitle(context: Context, title: String, sourceBases: Map<Int, String>): List<String> {
        val text = fileText(context, "filmes-${letter(title)}.txt") ?: return emptyList()
        val wanted = stripYear(normalize(title))
        val matches = linkedSetOf<String>()
        for (line in text.lineSequence()) {
            val fields = line.split('\t')
            val actualTitle = fields.firstOrNull()?.trim().orEmpty()
            if (actualTitle.isBlank() || stripYear(normalize(actualTitle)) != wanted) continue
            matches += parseSourceFields(fields.drop(1), sourceBases)
            if (matches.size >= 8) break
        }
        return matches.toList()
    }

    private fun seriesEpisodes(context: Context, title: String, sourceBases: Map<Int, String>): List<Episode> {
        val letter = letter(title)
        val index = fileText(context, "series-$letter.txt") ?: return emptyList()
        val wanted = stripYear(normalize(title))
        var chunk: Int? = null
        var year = ""
        for (line in index.lineSequence()) {
            val fields = line.split('\t')
            if (fields.size < 4 || stripYear(normalize(fields[0])) != wanted) continue
            year = fields.getOrNull(1).orEmpty().trim()
            chunk = fields.getOrNull(2)?.toIntOrNull()
            break
        }
        val chunkNumber = chunk ?: return emptyList()
        val text = fileText(context, "series-$letter-$chunkNumber.txt") ?: return emptyList()
        val episodes = mutableListOf<Episode>()
        var inTarget = false
        for (line in text.lineSequence()) {
            if (line.startsWith("@")) {
                if (inTarget) break
                val identity = line.drop(1).split('\t', limit = 2)
                inTarget = stripYear(normalize(identity[0])) == wanted &&
                    (year.isBlank() || identity.getOrNull(1).orEmpty().trim() == year)
                continue
            }
            if (!inTarget) continue
            val fields = line.split('\t')
            if (fields.size < 4) continue
            val urls = resolveTokens(fields[3].split(',').map(String::trim), sourceBases)
            if (urls.isEmpty()) continue
            episodes += Episode(fields[0].toIntOrNull() ?: 0, fields[1].toIntOrNull() ?: 0,
                fields[2].ifBlank { "dub" }, urls)
        }
        return episodes.distinctBy { "${it.season}:${it.number}:${it.language}" }
    }

    private fun collectionEpisodes(
        context: Context,
        type: String,
        title: String,
        tmdbId: String?,
        sourceBases: Map<Int, String>
    ): List<Episode> {
        val text = fileText(context, "redeflix/links-$type.txt") ?: return emptyList()
        val wanted = stripYear(normalize(title))
        val episodes = mutableListOf<Episode>()
        var inTarget = false
        for (line in text.lineSequence()) {
            if (line.startsWith("@")) {
                if (inTarget) break
                val fields = line.drop(1).split('\t')
                val candidate = stripYear(normalize(fields.getOrNull(0).orEmpty()))
                val candidateId = fields.getOrNull(2).orEmpty().trim()
                val idMatches = !tmdbId.isNullOrBlank() && candidateId == tmdbId
                inTarget = idMatches || candidate == wanted
                continue
            }
            if (!inTarget) continue
            val fields = line.split('\t')
            if (fields.size < 4) continue
            val urls = resolveTokens(fields[3].split(',').map(String::trim), sourceBases)
            if (urls.isEmpty()) continue
            episodes += Episode(fields[0].toIntOrNull() ?: 0, fields[1].toIntOrNull() ?: 0,
                fields[2].ifBlank { "dub" }, urls)
        }
        return episodes.distinctBy { "${it.season}:${it.number}:${it.language}" }
    }

    private fun parseSourceFields(fields: List<String>, sourceBases: Map<Int, String>): List<String> {
        val tokens = fields.flatMap { field ->
            val value = field.substringAfter('=', "").trim()
            if (value.isBlank()) emptyList() else value.split(',').map(String::trim)
        }
        return resolveTokens(tokens, sourceBases)
    }

    private fun resolveTokens(tokens: List<String>, sourceBases: Map<Int, String>): List<String> =
        tokens.mapNotNull { token ->
            val value = token.trim().trimEnd(';')
            if (value.isBlank()) return@mapNotNull null
            val resolved = if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
                value
            } else {
                val separator = value.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                val index = value.substring(0, separator).toIntOrNull() ?: return@mapNotNull null
                val base = sourceBases[index] ?: return@mapNotNull null
                val rest = value.substring(separator + 1)
                if (rest.contains('.')) base + rest else "$base$rest.mp4"
            }
            val lower = resolved.lowercase(Locale.ROOT)
            if (!lower.startsWith("http") || lower.contains("youtube.com") || lower.contains("youtu.be")) return@mapNotNull null
            if (lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".mkv") ||
                lower.contains(".mpd") || lower.contains(".ts") || lower.contains("/stream") ||
                lower.contains("/live") || lower.contains("master.txt") || lower.contains(".mp3")) resolved
            else null
        }.distinct()

    private fun bases(context: Context): Map<Int, String> {
        val text = fileText(context, "indice.txt") ?: return emptyMap()
        val result = mutableMapOf<Int, String>()
        for (line in text.lineSequence()) {
            if (!line.startsWith("base:")) continue
            val value = line.removePrefix("base:").trim()
            val index = value.substringBefore(' ').toIntOrNull() ?: continue
            val url = value.substringAfter(' ', "").trim()
            if (url.startsWith("http://") || url.startsWith("https://")) result[index] = url
        }
        return result
    }

    private fun fileText(context: Context, path: String): String? {
        memory[path]?.let { return it }
        val directory = File(context.cacheDir, "saimo-vod").apply { mkdirs() }
        val local = File(directory, path.replace("#", "hash").replace('/', '_'))
        if (local.exists() && local.length() > 0L &&
            System.currentTimeMillis() - local.lastModified() < CACHE_TTL_MS) {
            return runCatching { local.readText(Charsets.UTF_8) }.getOrNull()?.also { memory[path] = it }
        }
        val stale = runCatching {
            if (local.exists() && local.length() > 0L) local.readText(Charsets.UTF_8) else null
        }.getOrNull()
        val encoded = path.split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val downloaded = try {
            val connection = (URL(BASE + encoded).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "CineHUB-TV/0.3")
                setRequestProperty("Accept", "text/plain,*/*")
            }
            try {
                if (connection.responseCode !in 200..299) null
                else connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally { connection.disconnect() }
        } catch (_: Exception) { null }
        val text = downloaded?.takeIf { it.isNotBlank() } ?: stale ?: return null
        if (downloaded != null && downloaded.isNotBlank()) runCatching { local.writeText(downloaded, Charsets.UTF_8) }
        memory[path] = text
        return text
    }

    private fun letter(title: String): String {
        val first = title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '#'
        return if (first in 'A'..'Z') first.toString() else "#"
    }

    private fun stripYear(value: String): String =
        value.replace(Regex("""\s*\(?(?:19|20)\d{2}\)?\s*$"""), "").trim()

    private fun normalize(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT).replace(Regex("""\(\d{4}\)"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim(),
        Normalizer.Form.NFD
    ).replace(Regex("""\p{M}+"""), "").replace(Regex("""\s+"""), " ").trim()
}
