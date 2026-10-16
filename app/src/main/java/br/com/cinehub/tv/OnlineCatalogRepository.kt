package br.com.cinehub.tv

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class MediaItem(
    val id: String,
    val title: String,
    val type: String,
    val poster: String?,
    val backdrop: String?,
    val year: String,
    val rating: String,
    val description: String,
    val genreIds: Set<Int>,
    val language: String,
    val countries: Set<String>
)

object OnlineCatalogRepository {
    private const val FUNCTION_URL = "https://qdheeiitzlvynshvkwcs.supabase.co/functions/v1/cinehub-catalog"
    private const val PUBLIC_KEY = "sb_publishable_AzpaAW6_qoeiBxy4Pywa4g_XDn2up0z"
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500/"
    private const val ACERVO_BASE = "https://raw.githubusercontent.com/mooNXy7/CineHUB-ACERVO/main/catalog/vod/index/"
    private const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L
    private data class CachedCatalog(val items: List<MediaItem>, val loadedAt: Long)
    private val acervoCache = ConcurrentHashMap<String, CachedCatalog>()

    /**
     * ACERVO is the primary catalog for the four VOD sections. It includes all indexed
     * records, including metadata-only entries; the existing online feed remains a fallback.
     * Rendering is still bounded by MainActivity for TV-box memory and remote performance.
     */
    fun feed(section: String): List<MediaItem> {
        val acervo = runCatching { loadAcervoCategory(section) }.getOrDefault(emptyList())
        if (acervo.isNotEmpty()) return acervo.take(30)
        return runCatching { legacyFeed(section) }.getOrDefault(emptyList())
    }

    /**
     * Searches the complete selected ACERVO index locally after loading/caching it,
     * rather than searching only the first page of the external discovery API.
     */
    fun search(query: String, section: String): List<MediaItem> {
        val term = normalize(query)
        if (term.isBlank()) return emptyList()
        val catalog = runCatching { loadAcervoCategory(section) }.getOrDefault(emptyList())
        if (catalog.isNotEmpty()) {
            val matches = catalog.asSequence()
                .filter { normalize(it.title).contains(term) }
                .sortedWith(compareBy<MediaItem>({ !normalize(it.title).startsWith(term) }, { it.title.lowercase(Locale.ROOT) }))
                .take(100)
                .toList()
            if (matches.isNotEmpty()) return matches
        }
        return runCatching { legacySearch(query, section) }.getOrDefault(emptyList())
    }

    private fun loadAcervoCategory(section: String): List<MediaItem> {
        val category = when (section) {
            "Filmes" -> "movies"
            "Séries" -> "series"
            "Animes" -> "anime"
            "Doramas" -> "doramas"
            else -> return emptyList()
        }
        val now = System.currentTimeMillis()
        acervoCache[category]?.takeIf { now - it.loadedAt < CACHE_TTL_MS }?.let { return it.items }

        val raw = download("$ACERVO_BASE$category.json", 10000, 30000)
        val array = JSONArray(raw)
        val type = if (category == "movies") "movie" else "tv"
        val items = ArrayList<MediaItem>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id").trim()
            val title = item.optString("title").trim().ifBlank { item.optString("name").trim() }
            if (id.isBlank() || title.isBlank()) continue
            val poster = image(item.optString("poster_url"))
            val year = item.optString("year").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
            val genresArray = item.optJSONArray("genres")
            val genreIds = mutableSetOf<Int>()
            if (genresArray != null) {
                for (g in 0 until genresArray.length()) {
                    // Preserve the existing MediaItem shape; these IDs are only used for
                    // broad legacy filters. ACERVO category membership is authoritative.
                    val genre = genresArray.optString(g).lowercase(Locale.ROOT)
                    if (genre.contains("anima")) genreIds.add(16)
                }
            }
            items.add(MediaItem(
                id = "acervo-$id",
                title = title,
                type = type,
                poster = poster,
                backdrop = poster,
                year = year,
                rating = "—",
                description = "",
                genreIds = genreIds,
                language = when (category) { "anime" -> "ja"; "doramas" -> "ko"; else -> "" },
                countries = if (category == "doramas") setOf("KR") else emptySet()
            ))
        }
        val stable = items.distinctBy { it.id }
        acervoCache[category] = CachedCatalog(stable, now)
        Log.i("CineHUBCatalog", "ACERVO category=$category items=${stable.size}")
        return stable
    }

    private fun legacyFeed(section: String): List<MediaItem> {
        val type = if (section == "Filmes") "movie" else "tv"
        val action = if (section == "Filmes") "now_playing" else "trending"
        val data = request(JSONObject().put("action", action).put("type", type).put("page", 1))
        val results = data.optJSONArray("results") ?: return emptyList()
        val items = mutableListOf<MediaItem>()
        for (i in 0 until results.length()) {
            val raw = results.optJSONObject(i) ?: continue
            val item = parseItem(raw, type) ?: continue
            if (item.poster.isNullOrBlank()) continue
            when (section) {
                "Animes" -> if (item.genreIds.contains(16) || item.language == "ja") items.add(item)
                "Doramas" -> if (item.language == "ko" || item.countries.contains("KR")) items.add(item)
                else -> items.add(item)
            }
        }
        return items.distinctBy { it.id }.take(30)
    }

    private fun legacySearch(query: String, section: String): List<MediaItem> {
        val term = query.trim()
        if (term.isBlank()) return emptyList()
        val type = if (section == "Filmes") "movie" else "tv"
        val data = request(JSONObject().put("action", "search").put("type", type).put("query", term).put("page", 1))
        val results = data.optJSONArray("results") ?: return emptyList()
        val items = mutableListOf<MediaItem>()
        for (i in 0 until results.length()) {
            val raw = results.optJSONObject(i) ?: continue
            val item = parseItem(raw, type) ?: continue
            if (item.poster.isNullOrBlank()) continue
            when (section) {
                "Animes" -> if (item.genreIds.contains(16) || item.language == "ja") items.add(item)
                "Doramas" -> if (item.language == "ko" || item.countries.contains("KR")) items.add(item)
                else -> items.add(item)
            }
        }
        return items.distinctBy { it.id }.take(30)
    }

    private fun download(url: String, connectTimeout: Int, readTimeout: Int): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            this.connectTimeout = connectTimeout
            this.readTimeout = readTimeout
            requestMethod = "GET"
            setRequestProperty("User-Agent", "CineHUB-TV/1.3.2-test")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Catálogo ACERVO respondeu HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun request(body: JSONObject): JSONObject {
        val connection = (URL(FUNCTION_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("apikey", PUBLIC_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Supabase HTTP $code")
            val json = JSONObject(response)
            if (!json.optBoolean("success", false)) {
                throw IllegalStateException(json.optString("error", "Catálogo online indisponível"))
            }
            return json.optJSONObject("data") ?: JSONObject()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseItem(raw: JSONObject, type: String): MediaItem? {
        val id = raw.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = raw.optString(if (type == "movie") "title" else "name")
            .ifBlank { raw.optString("title") }
            .ifBlank { raw.optString("name") }
            .ifBlank { return null }
        val poster = image(raw.optString("poster_path"))
        val backdrop = image(raw.optString("backdrop_path"), "w1280")
        val date = raw.optString(if (type == "movie") "release_date" else "first_air_date")
        val genreIds = raw.optJSONArray("genre_ids")
        val genres = mutableSetOf<Int>()
        if (genreIds != null) for (i in 0 until genreIds.length()) genres.add(genreIds.optInt(i))
        val countriesArray = raw.optJSONArray("origin_country")
        val countries = mutableSetOf<String>()
        if (countriesArray != null) for (i in 0 until countriesArray.length()) countries.add(countriesArray.optString(i))
        val ratingValue = raw.optDouble("vote_average", 0.0)
        return MediaItem(
            id = "$type-$id",
            title = title,
            type = type,
            poster = poster,
            backdrop = backdrop,
            year = date.take(4),
            rating = if (ratingValue > 0) String.format(Locale.US, "%.1f", ratingValue) else "—",
            description = raw.optString("overview"),
            genreIds = genres,
            language = raw.optString("original_language"),
            countries = countries
        )
    }

    private fun image(path: String, size: String = "w500"): String? {
        if (path.isBlank() || path == "null") return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return if (path.startsWith("/")) "https://image.tmdb.org/t/p/$size$path" else "$IMAGE_BASE$path"
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()
        .replace(Regex("""\s+"""), " ")
}
