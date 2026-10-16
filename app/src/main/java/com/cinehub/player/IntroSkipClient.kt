package com.cinehub.player

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal enum class IntroSegmentType(val field: String, val label: String) {
    INTRO("intro", "abertura"),
    RECAP("recap", "recapitulação"),
    CREDITS("credits", "créditos"),
    PREVIEW("preview", "prévia")
}

internal data class IntroMarker(
    val type: IntroSegmentType,
    val startMs: Long,
    val endMs: Long?
) {
    val key: String get() = "${type.name}:$startMs:${endMs ?: -1L}"
}

internal object IntroSkipPolicy {
    fun activeMarker(
        positionMs: Long,
        durationMs: Long,
        markers: List<IntroMarker>,
        alreadyPrompted: Set<String>
    ): IntroMarker? {
        if (positionMs < 0L) return null
        return markers.firstOrNull { marker ->
            marker.type != IntroSegmentType.CREDITS &&
                marker.endMs != null &&
                marker.endMs > marker.startMs &&
                positionMs >= marker.startMs &&
                positionMs < marker.endMs &&
                marker.key !in alreadyPrompted &&
                (durationMs <= 0L || marker.endMs <= durationMs + 1_000L)
        }
    }
}

/**
 * Small asynchronous TheIntroDB client. Failure is intentionally non-fatal:
 * playback continues normally when the service is unavailable or has no marks.
 */
internal object IntroSkipClient {
    private const val BASE = "https://api.theintrodb.org/v3/media"
    private const val MAX_RESPONSE_BYTES = 256 * 1024
    private val executor = Executors.newSingleThreadExecutor()
    private val cache = ConcurrentHashMap<String, List<IntroMarker>>()

    fun load(
        tmdbId: Int,
        season: Int,
        episode: Int,
        onResult: (List<IntroMarker>) -> Unit
    ) {
        executor.execute {
            val markers = if (tmdbId <= 0) emptyList() else runCatching {
                val key = "$tmdbId|$season|$episode"
                cache[key] ?: fetch(tmdbId, season, episode).also { cache[key] = it }
            }.getOrDefault(emptyList())
            onResult(markers)
        }
    }

    internal fun parse(json: JSONObject): List<IntroMarker> {
        val result = mutableListOf<IntroMarker>()
        IntroSegmentType.entries.forEach { type ->
            val array = json.optJSONArray(type.field) ?: return@forEach
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val start = item.optLong("start_ms", -1L)
                val end = if (item.has("end_ms") && !item.isNull("end_ms")) {
                    item.optLong("end_ms", -1L)
                } else null
                if (start < 0L || (end != null && end - start < 3_000L)) continue
                result += IntroMarker(type, start, end)
            }
        }
        return result.sortedBy { it.startMs }.take(80)
    }

    private fun fetch(tmdbId: Int, season: Int, episode: Int): List<IntroMarker> {
        val address = buildString {
            append(BASE)
            append("?tmdb_id=")
            append(tmdbId)
            if (season > 0 && episode > 0) {
                append("&season=")
                append(season)
                append("&episode=")
                append(episode)
            }
        }
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return emptyList()
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_RESPONSE_BYTES) return emptyList()
                    output.write(buffer, 0, read)
                }
            }
            return parse(JSONObject(output.toString(Charsets.UTF_8.name())))
        } finally {
            connection.disconnect()
        }
    }
}
