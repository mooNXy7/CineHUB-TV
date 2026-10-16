package br.com.cinehub.tv

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class EpgProgramme(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val description: String = "",
    val category: String = ""
)

object EpgRepository {
    private const val GUIDE_URL =
        "https://raw.githubusercontent.com/mooNXy7/CineHUB/main/CineHUB_Data/epg/guide.index.json"
    private const val CACHE_NAME = "cinehub-epg-guide.json"
    private const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L
    private const val MAX_BYTES = 12 * 1024 * 1024
    private val lock = Any()
    @Volatile private var memory: Map<String, List<EpgProgramme>> = emptyMap()
    @Volatile private var memoryAt = 0L

    fun load(context: Context): Map<String, List<EpgProgramme>> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (memory.isNotEmpty() && now - memoryAt < CACHE_TTL_MS) return memory
        }
        val cache = File(context.cacheDir, CACHE_NAME)
        val cachedText = runCatching { cache.takeIf { it.exists() && it.length() > 0L }?.readText(Charsets.UTF_8) }.getOrNull()
        if (!cachedText.isNullOrBlank() && cache.lastModified() > 0L && now - cache.lastModified() < CACHE_TTL_MS) {
            val parsed = runCatching { parse(cachedText, now) }.getOrDefault(emptyMap())
            if (parsed.isNotEmpty()) {
                synchronized(lock) { memory = parsed; memoryAt = cache.lastModified() }
                return parsed
            }
        }
        val downloaded = runCatching { downloadGuide() }.getOrNull()
        val raw = downloaded ?: cachedText
        if (raw.isNullOrBlank()) return emptyMap()
        if (downloaded != null) runCatching {
            val temp = File(cache.parentFile, "$CACHE_NAME.tmp")
            temp.writeText(downloaded, Charsets.UTF_8)
            if (!temp.renameTo(cache)) {
                cache.writeText(downloaded, Charsets.UTF_8)
                temp.delete()
            }
        }
        val parsed = runCatching { parse(raw, now) }.getOrDefault(emptyMap())
        synchronized(lock) {
            memory = parsed
            memoryAt = if (downloaded != null) now else cache.lastModified()
        }
        return parsed
    }

    fun programmesFor(channel: TvChannel, guide: Map<String, List<EpgProgramme>>): List<EpgProgramme> {
        channel.acervoId?.let { id ->
            guide["id:$id"]?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return guide[normalise(channel.name)].orEmpty()
    }

    fun timeLabel(timestamp: Long): String =
        SimpleDateFormat("HH:mm", Locale("pt", "BR")).apply {
            timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
        }.format(Date(timestamp))

    private fun downloadGuide(): String {
        val connection = (URL(GUIDE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "CineHUB-TV/1.3.2")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("EPG respondeu HTTP ${connection.responseCode}")
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_BYTES) throw IllegalStateException("Índice EPG excede o limite de tamanho")
                    output.write(buffer, 0, count)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(raw: String, now: Long): Map<String, List<EpgProgramme>> {
        val root = JSONObject(raw)
        val channels = root.optJSONArray("channels") ?: return emptyMap()
        val byId = linkedMapOf<String, List<EpgProgramme>>()
        val byName = linkedMapOf<String, MutableList<EpgProgramme>>()
        val lowerBound = now - 6L * 60L * 60L * 1000L
        val upperBound = now + 72L * 60L * 60L * 1000L
        for (i in 0 until channels.length()) {
            val channel = channels.optJSONObject(i) ?: continue
            val id = channel.optString("id").trim()
            val name = channel.optString("name").trim()
            val entries = channel.optJSONArray("programs") ?: continue
            val programmes = mutableListOf<EpgProgramme>()
            for (j in 0 until entries.length()) {
                val item = entries.optJSONObject(j) ?: continue
                val start = parseTimestamp(item.optString("start")) ?: continue
                val end = parseTimestamp(item.optString("end")) ?: continue
                if (end <= lowerBound || start >= upperBound || end <= start) continue
                val title = item.optString("title").trim()
                if (title.isBlank()) continue
                programmes.add(EpgProgramme(
                    title = title, startMillis = start, endMillis = end,
                    description = item.optString("description").trim(),
                    category = item.optString("category").trim()
                ))
            }
            val sorted = programmes.distinctBy { "${it.startMillis}|${it.endMillis}|${it.title}" }.sortedBy { it.startMillis }
            if (sorted.isEmpty()) continue
            if (id.isNotBlank()) byId["id:$id"] = sorted
            val key = normalise(name)
            if (key.isNotBlank()) byName.getOrPut(key) { mutableListOf() }.addAll(sorted)
        }
        val result = linkedMapOf<String, List<EpgProgramme>>()
        result.putAll(byId)
        byName.forEach { (key, items) ->
            result[key] = items.distinctBy { "${it.startMillis}|${it.endMillis}|${it.title}" }.sortedBy { it.startMillis }
        }
        return result
    }

    private fun parseTimestamp(value: String): Long? {
        val match = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.\d+)?(Z|[+-]\d{2}:?\d{2})$""")
            .matchEntire(value.trim()) ?: return null
        val zone = if (match.groupValues[2] == "Z") "+0000" else match.groupValues[2].replace(":", "")
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply { isLenient = false }
        return runCatching { parser.parse(match.groupValues[1] + zone)?.time }.getOrNull()
    }

    private fun normalise(value: String): String {
        val plain = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("""\b(?:hd|sd|fhd|uhd|4k|br)\b"""), " ")
        return plain.replace(Regex("""[^a-z0-9]+"""), " ").trim().replace(Regex("""\s+"""), " ")
    }
}
