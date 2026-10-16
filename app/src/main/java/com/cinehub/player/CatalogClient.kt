package com.cinehub.player

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Low-memory client for public CineHUB-ACERVO JSON indexes.
 * Large catalog arrays are read incrementally; previews stop after a small page.
 */
object CatalogClient {
    private const val BASE =
        "https://raw.githubusercontent.com/mooNXy7/CineHUB-ACERVO/main/"
    private const val SAIMO_BASE =
        "https://raw.githubusercontent.com/gabrielsaimo/SaimoPlayer/main/"
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var appContext: Context? = null
    fun initialize(context: Context) { appContext = context.applicationContext }
    fun clearMemoryCache() { synchronized(catalogCache) { catalogCache.clear() } }
    private data class CachedValue(val storedAtMs: Long, val value: Any)
    private val catalogCache = LinkedHashMap<String, CachedValue>(16, 0.75f, true)
    private const val CATALOG_CACHE_TTL_MS = 5 * 60 * 1000L
    private const val CATALOG_CACHE_MAX_ENTRIES = 32
    @Volatile private var cachedProviderBases: Map<Int, String> = emptyMap()
    @Volatile private var providerBasesLoadedAt = 0L
    private const val PROVIDER_BASES_TTL_MS = 10 * 60 * 1000L
    private val categoryPaths = mapOf(
        "movies" to "catalog/vod/index/movies.json",
        "series" to "catalog/vod/index/series.json",
        "anime" to "catalog/vod/index/anime.json",
        "doramas" to "catalog/vod/index/doramas.json"
    )

    data class CatalogIndex(
        val schemaVersion: Int,
        val categories: Map<String, Pair<Int, String>>
    )

    data class CatalogEntry(
        val id: String,
        val title: String,
        val type: String,
        val detailPath: String,
        val posterPath: String,
        val year: String,
        val genres: List<String> = emptyList(),
        val subtype: String = ""
    )

    data class RadioEntry(val id: String, val name: String, val streamUrl: String, val genre: String)
    data class ChannelEntry(
        val id: String,
        val name: String,
        val category: String,
        val logoUrl: String,
        val sources: List<String>
    )
    data class PlaybackSource(
        val url: String,
        val audio: String,
        val priority: Int,
        val sourceId: String,
        val sourceType: String,
        val referer: String = ""
    )
    data class ProviderReference(val provider: String, val ref: String, val audio: String, val sourceId: String)
    data class EpisodeEntry(
        val season: Int,
        val episode: Int,
        val audio: String,
        val sources: List<PlaybackSource>,
        val providerRefs: List<ProviderReference> = emptyList()
    )
    data class ContentDetails(
        val id: String,
        val title: String,
        val type: String,
        val sources: List<PlaybackSource>,
        val episodes: List<EpisodeEntry>,
        val providerRefs: List<ProviderReference> = emptyList(),
        val tmdbId: Int = 0,
        val genres: List<String> = emptyList(),
        val subtype: String = "",
        val posterUrl: String = ""
    )

    /**
     * Bounded in-memory TTL cache for small catalog pages and individual details.
     * If refresh fails, return the last valid value rather than breaking the UI.
     */
    private fun <T : Any> cachedResult(key: String, loader: () -> T): Result<T> {
        readCached<T>(key, allowStale = false)?.let { return Result.success(it) }
        readDiskCache<T>(key, allowStale = false)?.let {
            writeMemoryCache(key, it)
            return Result.success(it)
        }
        val result = runCatching(loader)
        result.getOrNull()?.let { value ->
            writeMemoryCache(key, value)
            writeDiskCache(key, value)
        }
        if (result.isFailure) {
            readCached<T>(key, allowStale = true)?.let { return Result.success(it) }
            readDiskCache<T>(key, allowStale = true)?.let { return Result.success(it) }
        }
        return result
    }

    private fun writeMemoryCache(key: String, value: Any) {
        synchronized(catalogCache) {
            catalogCache[key] = CachedValue(System.currentTimeMillis(), value)
            while (catalogCache.size > CATALOG_CACHE_MAX_ENTRIES) {
                val firstKey = catalogCache.entries.iterator().next().key
                catalogCache.remove(firstKey)
            }
        }
    }

    private fun cacheDigest(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun writeDiskCache(key: String, value: Any) {
        val context = appContext ?: return
        val payload = encodeCacheValue(key, value) ?: return
        val root = JSONObject().put("stored_at", System.currentTimeMillis()).put("payload", payload)
        val text = root.toString()
        if (text.length > 2 * 1024 * 1024) return
        runCatching {
            val directory = File(context.cacheDir, "catalog-cache")
            if (!directory.exists()) directory.mkdirs()
            val target = File(directory, cacheDigest(key) + ".json")
            val temp = File(directory, cacheDigest(key) + ".tmp")
            temp.writeText(text, Charsets.UTF_8)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                target.writeText(text, Charsets.UTF_8)
                temp.delete()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> readDiskCache(key: String, allowStale: Boolean): T? {
        val context = appContext ?: return null
        val file = File(File(context.cacheDir, "catalog-cache"), cacheDigest(key) + ".json")
        if (!file.isFile || file.length() !in 1..(2L * 1024 * 1024)) return null
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val storedAt = root.optLong("stored_at", 0L)
            if (storedAt <= 0L || (!allowStale && System.currentTimeMillis() - storedAt > CATALOG_CACHE_TTL_MS)) {
                return@runCatching null
            }
            decodeCacheValue(key, root.optJSONObject("payload") ?: return@runCatching null) as? T
        }.getOrNull()
    }

    private fun encodeCacheValue(key: String, value: Any): JSONObject? = when {
        key == "vod-index" && value is CatalogIndex -> {
            val categories = JSONObject()
            value.categories.forEach { (name, pair) ->
                categories.put(name, JSONObject().put("count", pair.first).put("index_path", pair.second))
            }
            JSONObject().put("kind", "index").put("schema_version", value.schemaVersion).put("categories", categories)
        }
        key.startsWith("category:") && value is List<*> -> {
            val array = JSONArray()
            value.filterIsInstance<CatalogEntry>().forEach { entry ->
                array.put(JSONObject().put("id", entry.id).put("title", entry.title).put("type", entry.type)
                    .put("detail_path", entry.detailPath).put("poster_url", entry.posterPath).put("year", entry.year)
                    .put("genres", JSONArray(entry.genres)).put("subtype", entry.subtype))
            }
            JSONObject().put("kind", "entries").put("items", array)
        }
        key.startsWith("radios:") && value is List<*> -> {
            val array = JSONArray()
            value.filterIsInstance<RadioEntry>().forEach { entry ->
                array.put(JSONObject().put("id", entry.id).put("name", entry.name)
                    .put("stream_url", entry.streamUrl).put("genre", entry.genre))
            }
            JSONObject().put("kind", "radios").put("items", array)
        }
        key.startsWith("channels:") && value is List<*> -> {
            val array = JSONArray()
            value.filterIsInstance<ChannelEntry>().forEach { entry ->
                array.put(JSONObject().put("id", entry.id).put("name", entry.name)
                    .put("category", entry.category).put("logo_url", entry.logoUrl)
                    .put("sources", JSONArray(entry.sources)))
            }
            JSONObject().put("kind", "channels").put("items", array)
        }
        else -> null
    }

    private fun decodeCacheValue(key: String, payload: JSONObject): Any? = when {
        key == "vod-index" && payload.optString("kind") == "index" -> {
            val source = payload.optJSONObject("categories") ?: JSONObject()
            val categories = mutableMapOf<String, Pair<Int, String>>()
            val keys = source.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val item = source.optJSONObject(name) ?: continue
                categories[name] = item.optInt("count") to item.optString("index_path")
            }
            CatalogIndex(payload.optInt("schema_version", 1), categories)
        }
        key.startsWith("category:") && payload.optString("kind") == "entries" -> {
            val array = payload.optJSONArray("items") ?: JSONArray()
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val genreArray = item.optJSONArray("genres") ?: JSONArray()
                CatalogEntry(
                    item.optString("id"), item.optString("title"), item.optString("type"),
                    item.optString("detail_path"), item.optString("poster_url"), item.optString("year"),
                    (0 until genreArray.length()).map { genreArray.optString(it) }, item.optString("subtype")
                )
            }
        }
        key.startsWith("radios:") && payload.optString("kind") == "radios" -> {
            val array = payload.optJSONArray("items") ?: JSONArray()
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                RadioEntry(item.optString("id"), item.optString("name"), item.optString("stream_url"), item.optString("genre"))
            }
        }
        key.startsWith("channels:") && payload.optString("kind") == "channels" -> {
            val array = payload.optJSONArray("items") ?: JSONArray()
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val sources = item.optJSONArray("sources") ?: JSONArray()
                ChannelEntry(
                    item.optString("id"), item.optString("name"), item.optString("category"), item.optString("logo_url"),
                    (0 until sources.length()).map { sources.optString(it) }.filter(PlaybackUrlPolicy::isSafeHttpUrl)
                )
            }
        }
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> readCached(key: String, allowStale: Boolean): T? =
        synchronized(catalogCache) {
            val entry = catalogCache[key] ?: return@synchronized null
            if (!allowStale && System.currentTimeMillis() - entry.storedAtMs > CATALOG_CACHE_TTL_MS) {
                return@synchronized null
            }
            entry.value as? T
        }


    /** HTML pages are admitted only when a provider-specific resolver is available. */
    fun isDirectPlaybackSource(source: PlaybackSource): Boolean {
        if (!PlaybackUrlPolicy.isSafeHttpUrl(source.url)) return false
        val uri = runCatching { URL(source.url) }.getOrNull() ?: return false
        val host = uri.host.lowercase()
        val path = uri.path.lowercase()
        val hasProviderResolver = ProviderUrlResolver.isResolvableProviderPage(source.url)
        if (source.sourceType.equals("embed", ignoreCase = true) && !hasProviderResolver) return false
        if (host == "mgeb.top" || host.endsWith(".mgeb.top") ||
            host == "nhdapi.com" || host.endsWith(".nhdapi.com")
        ) return hasProviderResolver
        if ((path.contains("/embed/") || path.endsWith("/embed")) && !hasProviderResolver) return false
        return true
    }

    fun loadVodIndex(onResult: (Result<CatalogIndex>) -> Unit) {
        executor.execute {
            onResult(cachedResult("vod-index") {
                val json = JSONObject(fetchText(BASE + "catalog/vod/index.json", 2 * 1024 * 1024))
                val categoriesJson = json.getJSONObject("categories")
                val categories = mutableMapOf<String, Pair<Int, String>>()
                val keys = categoriesJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val item = categoriesJson.getJSONObject(key)
                    categories[key] = item.optInt("count") to item.optString("index_path")
                }
                CatalogIndex(json.optInt("schema_version", 1), categories)
            })
        }
    }

    fun loadCategoryPreview(category: String, limit: Int = 40, onResult: (Result<List<CatalogEntry>>) -> Unit) {
        executor.execute {
            onResult(cachedResult("category:${category.lowercase()}:${limit}") {
                require(limit in 1..100) { "Preview limit must be between 1 and 100" }
                val path = categoryPaths[category.lowercase()]
                    ?: error("Unknown catalog category: $category")
                readArrayPreview(BASE + path, limit)
            })
        }
    }

    fun loadContentDetails(entry: CatalogEntry, onResult: (Result<ContentDetails>) -> Unit) {
        executor.execute {
            onResult(cachedResult("details:${entry.id}") {
                require(entry.detailPath.startsWith("catalog/vod/details/")) { "Invalid catalog detail path" }
                // Some shard names begin with "#"; encode it so URL parsing does not treat it as a fragment.
                val safePath = entry.detailPath.replace("#", "%23")
                val details = readContentDetails(BASE + safePath, entry.id)
                // Provider refs use the same indexed bases and relative file refs as SaimoPlayer's VodFormato.
                val bases = providerBases()
                val directSources = (details.sources + resolveProviderReferences(details.providerRefs, bases))
                    .distinctBy { it.url }.sortedBy { it.priority }
                val episodes = details.episodes.map { episode ->
                    episode.copy(
                        sources = (episode.sources + resolveProviderReferences(episode.providerRefs, bases))
                            .distinctBy { it.url }.sortedBy { it.priority }
                    )
                }
                details.copy(
                    sources = directSources,
                    episodes = episodes,
                    genres = details.genres.ifEmpty { entry.genres },
                    subtype = details.subtype.ifBlank { entry.subtype },
                    posterUrl = normalizePosterUrl(details.posterUrl.ifBlank { entry.posterPath })
                )
            })
        }
    }

    private fun readContentDetails(address: String, wantedId: String): ContentDetails {
        val connection = open(address)
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("Detalhes do catálogo responderam HTTP $status")
            val reader = JsonReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            reader.beginArray()
            while (reader.hasNext()) {
                var id = ""
                var title = ""
                var type = ""
                var sources = emptyList<PlaybackSource>()
                var episodes = emptyList<EpisodeEntry>()
                var providerRefs = emptyList<ProviderReference>()
                var tmdbId = 0
                var genres = emptyList<String>()
                var subtype = ""
                var posterUrl = ""
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "id" -> id = readNullableString(reader)
                        "title" -> title = readNullableString(reader)
                        "type" -> type = readNullableString(reader)
                        "sources" -> sources = readSources(reader)
                        "seasons" -> episodes = readSeasons(reader)
                        "provider_refs" -> providerRefs = readProviderReferences(reader)
                        "tmdb_id" -> tmdbId = readNullableString(reader).toIntOrNull() ?: 0
                        "genres" -> genres = readStringArray(reader)
                        "subtype" -> subtype = readNullableString(reader)
                        "poster_url" -> posterUrl = readNullableString(reader)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (id == wantedId) {
                    reader.close()
                    return ContentDetails(id, title, type, sources, episodes, providerRefs, tmdbId, genres, subtype, posterUrl)
                }
            }
            reader.close()
            error("Título não encontrado no arquivo de detalhes")
        } finally {
            connection.disconnect()
        }
    }

    private fun providerBases(): Map<Int, String> {
        val now = System.currentTimeMillis()
        if (cachedProviderBases.isNotEmpty() && now - providerBasesLoadedAt < PROVIDER_BASES_TTL_MS) {
            return cachedProviderBases
        }
        return runCatching {
            val text = fetchText(SAIMO_BASE + "vod/indice.txt", 2 * 1024 * 1024)
            val parsed = text.lineSequence().mapNotNull { line ->
                if (!line.startsWith("base:")) return@mapNotNull null
                val value = line.removePrefix("base:").trim()
                val split = value.split(Regex("\\s+"), limit = 2)
                if (split.size != 2) return@mapNotNull null
                val index = split[0].toIntOrNull() ?: return@mapNotNull null
                val base = split[1].trim()
                val uri = runCatching { URL(base) }.getOrNull() ?: return@mapNotNull null
                if (!PlaybackUrlPolicy.isSafeHttpUrl(base) ||
                    uri.host.endsWith(".invalid", ignoreCase = true)) return@mapNotNull null
                index to if (base.endsWith("/")) base else "$base/"
            }.toMap()
            cachedProviderBases = parsed
            providerBasesLoadedAt = now
            parsed
        }.getOrElse { cachedProviderBases }
    }

    private fun resolveProviderReferences(
        references: List<ProviderReference>,
        bases: Map<Int, String>
    ): List<PlaybackSource> = references.mapIndexedNotNull { index, reference ->
        val providerIndex = reference.provider.toIntOrNull() ?: return@mapIndexedNotNull null
        val base = bases[providerIndex] ?: return@mapIndexedNotNull null
        val url = ProviderUrlResolver.resolve(base, reference.ref)
            ?: return@mapIndexedNotNull null
        PlaybackSource(
            url = url,
            audio = reference.audio,
            priority = 200 + index,
            sourceId = reference.sourceId.ifBlank { "provider-" + reference.provider },
            sourceType = "provider-resolved"
        )
    }

    private fun readSources(reader: JsonReader): List<PlaybackSource> {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return emptyList()
        }
        val result = mutableListOf<PlaybackSource>()
        reader.beginArray()
        while (reader.hasNext()) {
            var url = ""
            var audio = ""
            var priority = 100
            var sourceId = ""
            var sourceType = ""
            var referer = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "url" -> url = readNullableString(reader)
                    "audio" -> audio = readNullableString(reader)
                    "priority" -> priority = readNullableString(reader).toIntOrNull() ?: 100
                    "source_id" -> sourceId = readNullableString(reader)
                    "source_type" -> sourceType = readNullableString(reader)
                    "referer" -> referer = readNullableString(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (PlaybackUrlPolicy.isSafeHttpUrl(url)) {
                result.add(
                    PlaybackSource(
                        url = url,
                        audio = audio,
                        priority = priority,
                        sourceId = sourceId,
                        sourceType = sourceType,
                        referer = referer.takeIf(PlaybackUrlPolicy::isSafeHttpUrl).orEmpty()
                    )
                )
            }
        }
        reader.endArray()
        return result.sortedBy { it.priority }
    }

    private fun readProviderReferences(reader: JsonReader): List<ProviderReference> {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return emptyList()
        }
        val result = mutableListOf<ProviderReference>()
        reader.beginArray()
        while (reader.hasNext()) {
            var provider = ""
            var ref = ""
            var audio = ""
            var sourceId = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "provider" -> provider = readNullableString(reader)
                    "ref" -> ref = readNullableString(reader)
                    "audio" -> audio = readNullableString(reader)
                    "source_id" -> sourceId = readNullableString(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (provider.isNotBlank() && ref.isNotBlank()) {
                result.add(ProviderReference(provider, ref, audio, sourceId))
            }
        }
        reader.endArray()
        return result
    }

    private fun readSeasons(reader: JsonReader): List<EpisodeEntry> {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return emptyList()
        }
        val result = mutableListOf<EpisodeEntry>()
        reader.beginArray()
        while (reader.hasNext()) {
            var seasonNumber = 1
            var seasonEpisodes = emptyList<EpisodeEntry>()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "season" -> seasonNumber = readNullableString(reader).toIntOrNull() ?: 1
                    "episodes" -> {
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull()
                        } else {
                            reader.beginArray()
                            val parsed = mutableListOf<EpisodeEntry>()
                            while (reader.hasNext()) {
                                var episodeNumber = 0
                                var audio = ""
                                var sources = emptyList<PlaybackSource>()
                                var providerRefs = emptyList<ProviderReference>()
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "episode" -> episodeNumber = readNullableString(reader).toIntOrNull() ?: 0
                                        "audio" -> audio = readNullableString(reader)
                                        "sources" -> sources = readSources(reader)
                                        "provider_refs" -> providerRefs = readProviderReferences(reader)
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                parsed.add(EpisodeEntry(seasonNumber, episodeNumber, audio, sources, providerRefs))
                            }
                            reader.endArray()
                            seasonEpisodes = parsed
                        }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            result.addAll(seasonEpisodes)
        }
        reader.endArray()
        return result.sortedWith(compareBy<EpisodeEntry> { it.season }.thenBy { it.episode }.thenBy { it.audio })
    }

    /**
     * Loads a small, playable-only channel preview. Records with no direct source are
     * deliberately omitted: pending candidates are metadata, not playable streams.
     */
    fun normalizePosterUrl(rawPath: String): String {
        val value = rawPath.trim()
        if (value.isBlank()) return ""
        if (PlaybackUrlPolicy.isSafeHttpUrl(value)) return value
        if (!value.startsWith("/") || value.startsWith("//")) return ""
        return "https://image.tmdb.org/t/p/w342$value"
    }

    /**
     * Returns a small genre-matched recommendation set from catalog index metadata.
     * It deliberately scans only a bounded preview to keep memory/network costs low.
     */
    fun loadRelatedPreview(
        category: String,
        currentId: String,
        genres: List<String>,
        limit: Int = 8,
        onResult: (Result<List<CatalogEntry>>) -> Unit
    ) {
        require(limit in 1..20) { "Recommendation limit must be between 1 and 20" }
        val normalizedGenres = genres.map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
        if (normalizedGenres.isEmpty()) {
            onResult(Result.success(emptyList()))
            return
        }
        loadCategoryPreview(category, 100) { result ->
            onResult(result.map { entries ->
                CatalogRecommendationPolicy.select(entries, currentId, normalizedGenres.toList(), limit)
            })
        }
    }

    fun loadChannelPreview(limit: Int = 60, onResult: (Result<List<ChannelEntry>>) -> Unit) {
        executor.execute {
            onResult(cachedResult("channels:${limit}") {
                require(limit in 1..100) { "Channel preview limit must be between 1 and 100" }
                val connection = open(BASE + "catalog/channels/channels.json")
                try {
                    val status = connection.responseCode
                    if (status !in 200..299) error("Catálogo de canais respondeu HTTP $status")
                    val reader = JsonReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                    val entries = ArrayList<ChannelEntry>(limit)
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "channels" -> {
                                reader.beginArray()
                                while (reader.hasNext() && entries.size < limit) {
                                    var id = ""
                                    var name = ""
                                    var category = ""
                                    var logo = ""
                                    var streamUrl = ""
                                    var sources = emptyList<String>()
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "id" -> id = readNullableString(reader)
                                            "name", "title" -> name = readNullableString(reader)
                                            "category", "group" -> category = readNullableString(reader)
                                            "logo_url", "logo" -> logo = readNullableString(reader)
                                            "stream_url", "url" -> streamUrl = readNullableString(reader)
                                            "sources" -> sources = readChannelSources(reader)
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                    val validSources = ChannelSourcePolicy.normalize(streamUrl, sources)
                                    if (name.isNotBlank() && validSources.isNotEmpty()) {
                                        entries.add(ChannelEntry(id.ifBlank { name.lowercase() }, name, category, logo, validSources))
                                    }
                                }
                                reader.close()
                                return@cachedResult entries
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.close()
                    entries
                } finally {
                    connection.disconnect()
                }
            })
        }
    }

    private fun readChannelSources(reader: JsonReader): List<String> {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return emptyList()
        }
        val result = mutableListOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> {
                    val candidate = readNullableString(reader)
                    if (PlaybackUrlPolicy.isSafeHttpUrl(candidate)) result.add(candidate)
                }
                JsonToken.BEGIN_OBJECT -> {
                    var url = ""
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "url", "stream_url", "uri" -> url = readNullableString(reader)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (PlaybackUrlPolicy.isSafeHttpUrl(url)) result.add(url)
                }
                else -> reader.skipValue()
            }
        }
        reader.endArray()
        return result.distinct()
    }

    fun loadRadioPreview(limit: Int = 50, onResult: (Result<List<RadioEntry>>) -> Unit) {
        executor.execute {
            onResult(cachedResult("radios:${limit}") {
                require(limit in 1..100) { "Radio preview limit must be between 1 and 100" }
                val connection = open(BASE + "catalog/radios/radios.json")
                try {
                    val status = connection.responseCode
                    if (status !in 200..299) error("Catálogo respondeu HTTP $status")
                    val reader = JsonReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                    val entries = ArrayList<RadioEntry>(limit)
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "radios" -> {
                                reader.beginArray()
                                while (reader.hasNext() && entries.size < limit) {
                                    var id = ""
                                    var name = ""
                                    var stream = ""
                                    var genre = ""
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "id" -> id = readNullableString(reader)
                                            "name" -> name = readNullableString(reader)
                                            "stream_url" -> stream = readNullableString(reader)
                                            "genre" -> genre = readNullableString(reader)
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                    if (name.isNotBlank() && PlaybackUrlPolicy.isSafeHttpUrl(stream)) {
                                        entries.add(RadioEntry(id, name, stream, genre))
                                    }
                                }
                                // We intentionally stop after a bounded preview for low-memory devices.
                                reader.close()
                                return@cachedResult entries
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.close()
                    entries
                } finally {
                    connection.disconnect()
                }
            })
        }
    }

    private fun readArrayPreview(address: String, limit: Int): List<CatalogEntry> {
        val connection = open(address)
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("Catálogo respondeu HTTP $status")
            val reader = JsonReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            reader.isLenient = false
            val entries = ArrayList<CatalogEntry>(limit)
            reader.beginArray()
            while (reader.hasNext() && entries.size < limit) {
                var id = ""
                var title = ""
                var type = ""
                var detailPath = ""
                var posterPath = ""
                var year = ""
                var genres = emptyList<String>()
                var subtype = ""
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "id" -> id = readNullableString(reader)
                        "title" -> title = readNullableString(reader)
                        "type" -> type = readNullableString(reader)
                        "detail_path" -> detailPath = readNullableString(reader)
                        "poster_url" -> posterPath = readNullableString(reader)
                        "year" -> year = readNullableString(reader)
                        "genres" -> genres = readStringArray(reader)
                        "subtype" -> subtype = readNullableString(reader)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (title.isNotBlank()) entries.add(CatalogEntry(id, title, type, detailPath, posterPath, year, genres, subtype))
            }
            reader.close()
            return entries
        } finally {
            connection.disconnect()
        }
    }

    private fun readStringArray(reader: JsonReader): List<String> {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return emptyList()
        }
        val values = mutableListOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            val value = readNullableString(reader).trim()
            if (value.isNotBlank()) values.add(value)
        }
        reader.endArray()
        return values.distinct()
    }

    private fun readNullableString(reader: JsonReader): String =
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            ""
        } else {
            reader.nextString()
        }

    private fun fetchText(address: String, maxBytes: Int): String {
        val connection = open(address)
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("Catálogo respondeu HTTP $status")
            val output = StringBuilder()
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { input ->
                val buffer = CharArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) error("Resposta do catálogo excede o limite permitido")
                    output.append(buffer, 0, count)
                }
            }
            return output.toString()
        } finally {
            connection.disconnect()
        }
    }

    private fun open(address: String): HttpURLConnection =
        (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CineHUB-PLAYER/0.1")
        }
}
