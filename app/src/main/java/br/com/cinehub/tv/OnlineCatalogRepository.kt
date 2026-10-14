package br.com.cinehub.tv

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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

    fun feed(section: String): List<MediaItem> {
        val type = if (section == "Filmes") "movie" else "tv"
        val action = if (section == "Filmes") "now_playing" else "trending"
        val payload = JSONObject()
            .put("action", action)
            .put("type", type)
            .put("page", 1)
        val data = request(payload)
        val results = data.optJSONArray("results") ?: return emptyList()
        val items = mutableListOf<MediaItem>()
        for (i in 0 until results.length()) {
            val raw = results.optJSONObject(i) ?: continue
            val item = parseItem(raw, type) ?: continue
            if (item.poster.isNullOrBlank()) continue
            when (section) {
                "Animes" -> if (item.genreIds.contains(16) || item.genreIds.contains(animationGenreTv)) items.add(item)
                "Doramas" -> if (item.language == "ko" || item.countries.contains("KR")) items.add(item)
                else -> items.add(item)
            }
        }
        // Anime genre 16 is TMDB animation; keep filtering permissive because TV anime
        // availability varies by country and provider metadata.
        return items.distinctBy { it.id }.take(30)
    }

    fun search(query: String, section: String): List<MediaItem> {
        val term = query.trim()
        if (term.isBlank()) return emptyList()
        val type = if (section == "Filmes") "movie" else "tv"
        val data = request(JSONObject()
            .put("action", "search")
            .put("type", type)
            .put("query", term)
            .put("page", 1))
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
            rating = if (ratingValue > 0) String.format(java.util.Locale.US, "%.1f", ratingValue) else "—",
            description = raw.optString("overview"),
            genreIds = genres,
            language = raw.optString("original_language"),
            countries = countries
        )
    }

    private fun image(path: String, size: String = "w500"): String? {
        if (path.isBlank() || path == "null") return null
        return if (path.startsWith("http")) path else "https://image.tmdb.org/t/p/$size/$path"
    }

    private const val animationGenreTv = 16
}
