package com.cinehub.player

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal data class OnlineSubtitleOption(
    val id: String,
    val languageCode: String,
    val label: String,
    val url: String
)

/**
 * Low-volume OpenSubtitles addon adapter. It resolves TMDB IDs through the
 * SaimoPlayer shard map and downloads only the small shard for the requested ID.
 * All requests run off the main thread and failure leaves manual URL subtitles available.
 */
internal object SubtitleSearchClient {
    private const val SAIMO_BASE = "https://raw.githubusercontent.com/gabrielsaimo/SaimoPlayer/main/"
    private const val SERVICE = "https://opensubtitles-v3.strem.io/subtitles"
    private const val MAX_RESPONSE_BYTES = 512 * 1024
    private val executor = Executors.newSingleThreadExecutor()
    private val cache = ConcurrentHashMap<String, List<OnlineSubtitleOption>>()

    private val languageOrder = listOf(
        "pob" to Pair("Português (Brasil)", 5),
        "por" to Pair("Português (Portugal)", 3),
        "eng" to Pair("Inglês", 3),
        "spa" to Pair("Espanhol", 2)
    )

    fun search(
        tmdbId: Int,
        isSeries: Boolean,
        season: Int,
        episode: Int,
        onResult: (List<OnlineSubtitleOption>) -> Unit
    ) {
        executor.execute {
            val key = "$tmdbId|$isSeries|$season|$episode"
            val options = if (tmdbId <= 0) emptyList() else runCatching {
                cache[key] ?: searchNow(tmdbId, isSeries, season, episode).also { result ->
                    if (result.isNotEmpty()) cache[key] = result
                }
            }.getOrDefault(emptyList())
            onResult(options)
        }
    }

    private fun searchNow(
        tmdbId: Int,
        isSeries: Boolean,
        season: Int,
        episode: Int
    ): List<OnlineSubtitleOption> {
        val imdb = resolveImdbId(tmdbId, isSeries) ?: return emptyList()
        val target = if (isSeries && season > 0 && episode > 0) {
            "series/$imdb:$season:$episode"
        } else {
            "movie/$imdb"
        }
        val json = JSONObject(fetchText("$SERVICE/$target.json") ?: return emptyList())
        val subtitles = json.optJSONArray("subtitles") ?: return emptyList()
        val result = mutableListOf<OnlineSubtitleOption>()
        languageOrder.forEach { (code, language) ->
            var added = 0
            for (index in 0 until subtitles.length()) {
                if (added >= language.second) break
                val item = subtitles.optJSONObject(index) ?: continue
                if (!item.optString("lang").equals(code, ignoreCase = true)) continue
                val address = item.optString("url").trim()
                if (!PlaybackUrlPolicy.isSafeHttpUrl(address)) continue
                val release = listOf("releaseGroup", "releaseFormat")
                    .asSequence()
                    .map { item.optString(it).trim() }
                    .firstOrNull { it.isNotBlank() }
                    ?: "Versão ${added + 1}"
                result += OnlineSubtitleOption(
                    id = "$code-${item.optString("id", added.toString())}",
                    languageCode = code,
                    label = "${language.first} · $release",
                    url = address
                )
                added++
            }
        }
        return result.take(13)
    }

    private fun resolveImdbId(tmdbId: Int, isSeries: Boolean): String? {
        if (tmdbId <= 0) return null
        val shard = (tmdbId % 100).toString().padStart(2, '0')
        val kind = if (isSeries) "s" else "f"
        val body = fetchText("${SAIMO_BASE}vod/imdb/$kind-$shard.txt") ?: return null
        for (line in body.lineSequence()) {
            val parts = line.split('\t', limit = 3)
            if (parts.size < 2) continue
            if (parts[0].trim().toIntOrNull() == tmdbId) {
                val imdb = parts[1].trim()
                if (imdb.matches(Regex("tt[0-9]{5,12}"))) return imdb
            }
        }
        return null
    }

    private fun fetchText(address: String): String? {
        val connection = runCatching {
            URL(address).openConnection() as HttpURLConnection
        }.getOrNull() ?: return null
        connection.connectTimeout = 5_000
        connection.readTimeout = 8_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json, text/plain, */*")
        connection.setRequestProperty("User-Agent", "CineHUB-PLAYER/0.1")
        try {
            if (connection.responseCode !in 200..299) return null
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_RESPONSE_BYTES) return null
                    output.write(buffer, 0, read)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        } catch (_: Exception) {
            return null
        } finally {
            connection.disconnect()
        }
    }
}
