package com.cinehub.player

import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import android.util.Base64

/**
 * Pure URL builder for indexed provider references published by SaimoPlayer.
 * It deliberately builds candidates only; playback success is determined by Media3.
 */
internal object ProviderUrlResolver {
    data class ResolvedSource(val url: String, val referer: String, val mimeType: String? = null)
    data class NhdApiConfig(val apiPath: String, val apiKey: String, val providers: List<String>)

    private val urlPattern = Regex("""(?i)https?:\\?/\\?/[^\s"'<>\\]{8,4096}""")
    private val attributePattern = Regex("""(?is)(?:src|file|source|url|hls|playlist|manifest)\s*[:=]\s*["']([^"']{4,4096})["']""")
    private val iframePattern = Regex("""(?is)<iframe[^>]+(?:src|data-src)\s*=\s*["']([^"']+)["']""")
    private val atobPattern = Regex("""(?is)atob\(\s*["']([A-Za-z0-9+/=_-]{20,8192})["']\s*\)""")
    private val mediaPattern = Regex("""(?i)\.(?:m3u8|mpd|mp4|mkv|webm|avi|mov|m4v|ts)(?:$|[?#&])""")

    fun isResolvableProviderPage(rawUrl: String): Boolean {
        if (!PlaybackUrlPolicy.isSafeHttpUrl(rawUrl)) return false
        val uri = runCatching { URL(rawUrl) }.getOrNull() ?: return false
        val host = uri.host.lowercase()
        val path = uri.path.lowercase()
        return (host == "dadinhodetapioca.fun" && path.contains("/play/")) ||
            ((host == "mgeb.top" || host.endsWith(".mgeb.top")) && path.startsWith("/embed/")) ||
            ((host == "nhdapi.com" || host.endsWith(".nhdapi.com")) &&
                (path.startsWith("/embed/") || path.startsWith("/dl/") ||
                    path.startsWith("/movie/") || path.startsWith("/tv/")))
    }

    fun candidatePages(rawUrl: String): List<String> {
        val base = runCatching { URL(rawUrl) }.getOrNull() ?: return emptyList()
        val host = base.host.lowercase()
        val path = base.path.trimEnd('/')
        val candidates = linkedSetOf(rawUrl)
        if (host == "mgeb.top" || host.endsWith(".mgeb.top")) {
            val parts = path.split('/').filter(String::isNotBlank)
            if (parts.size == 2 && parts[0] == "embed") {
                candidates += rawUrl + "?player=clappr"
                candidates += rawUrl + "?player=vidstack"
            } else if (parts.size == 4 && parts[0] == "embed") {
                candidates += "https://mgeb.top/embed/" + parts[1] + "-" + parts[2] + "-" + parts[3]
                candidates += rawUrl + "?player=clappr"
            }
        } else if (host == "nhdapi.com" || host.endsWith(".nhdapi.com")) {
            val parts = path.split('/').filter(String::isNotBlank)
            if (parts.size == 3 && parts[0] == "embed" && parts[1] == "movie") {
                candidates += "https://nhdapi.com/dl/movie/" + parts[2]
                candidates += "https://nhdapi.com/movie/" + parts[2]
            } else if (parts.size == 5 && parts[0] == "embed" && parts[1] == "tv") {
                candidates += "https://nhdapi.com/dl/tv/" + parts[2] + "/" + parts[3] + "/" + parts[4]
                candidates += "https://nhdapi.com/tv/" + parts[2] + "/" + parts[3] + "/" + parts[4]
            }
        }
        return candidates.filter(PlaybackUrlPolicy::isSafeHttpUrl).take(8)
    }

    fun extractIframeUrls(html: String, baseUrl: String): List<String> {
        val output = linkedSetOf<String>()
        decodedVariants(html).forEach { variant ->
            iframePattern.findAll(variant).forEach { match ->
                normalizeUrl(match.groupValues[1], baseUrl)?.let(output::add)
            }
        }
        return output.take(3)
    }

    fun extractMediaUrls(html: String, baseUrl: String): List<String> {
        val output = linkedSetOf<String>()
        decodedVariants(html).forEach { variant ->
            val iframeUrls = extractIframeUrls(variant, baseUrl).toSet()
            val candidates = sequence {
                attributePattern.findAll(variant).forEach { yield(it.groupValues[1]) }
                urlPattern.findAll(variant).forEach { yield(it.value) }
            }
            candidates.forEach { raw ->
                val url = normalizeUrl(raw, baseUrl) ?: return@forEach
                if (url in iframeUrls) return@forEach
                val parsed = runCatching { URL(url) }.getOrNull() ?: return@forEach
                val lower = url.lowercase()
                val strong = mediaPattern.containsMatchIn(lower) ||
                    lower.contains("playlist") || lower.contains("manifest") || lower.contains("hls.php") ||
                    (parsed.host.equals("embedplayer2.xyz", true) && parsed.path.contains("/cdn/hls/"))
                val pageLike = parsed.path.startsWith("/embed/") || parsed.path.startsWith("/movie/") ||
                    parsed.path.startsWith("/tv/") || parsed.path.startsWith("/dl/")
                if (strong && !pageLike) output += url
            }
        }
        return output.take(16)
    }

    fun parseNhdApiConfig(html: String, pageUrl: String): NhdApiConfig? {
        val path = Regex("""(?m)\bvar\s+API_PATH\s*=\s*["']([^"']+)["']""")
            .find(html)?.groupValues?.getOrNull(1)?.let { normalizeUrl(it, pageUrl) } ?: return null
        val key = Regex("""(?m)\bvar\s+API_KEY\s*=\s*["']([^"']*)["']""")
            .find(html)?.groupValues?.getOrNull(1).orEmpty()
        val rawProviders = Regex("""(?m)\bvar\s+PROVIDER_LIST\s*=\s*(\[[^;]+\])""")
            .find(html)?.groupValues?.getOrNull(1)
        val providers = Regex("""["']([^"']+)["']""")
            .findAll(rawProviders.orEmpty())
            .map { it.groupValues[1] }
            .filter(String::isNotBlank)
            .take(10)
            .toList()
        return NhdApiConfig(path, key, providers)
    }

    fun nhdApiEndpoints(config: NhdApiConfig): List<String> {
        val endpoints = linkedSetOf<String>()
        val providers: List<String?> = listOf(null) + config.providers.map { it as String? }
        val rawBase = config.apiPath.substringBefore('#')
        val base = rawBase.substringBefore('?')
        val existingQuery = rawBase.substringAfter('?', "").takeIf(String::isNotBlank)
        providers.forEach { provider ->
            val params = mutableListOf("_ts=1")
            if (config.apiKey.isNotBlank()) params += "key=" + URLEncoder.encode(config.apiKey, "UTF-8")
            if (!provider.isNullOrBlank()) {
                params += "provider=" + URLEncoder.encode(provider, "UTF-8")
                params += "fresh=1"
            }
            val query = listOfNotNull(existingQuery) + params
            val endpoint = base + "?" + query.joinToString("&")
            if (PlaybackUrlPolicy.isSafeHttpUrl(endpoint)) endpoints += endpoint
        }
        return endpoints.toList()
    }

    fun extractNhdPlayUrl(jsonText: String, baseUrl: String? = null): String? {
        if (!Regex("""(?i)"success"\s*:\s*true""").containsMatchIn(jsonText)) return null
        val candidates = mutableListOf<String>()
        Regex("""(?is)"playUrl"\s*:\s*"((?:\\.|[^"\\])*)"""")
            .find(jsonText)?.groupValues?.getOrNull(1)?.let(candidates::add)
        val trackPattern = Regex("""(?is)"(?:url|playUrl|file)"\s*:\s*"((?:\\.|[^"\\])*)"""")
        trackPattern.findAll(jsonText).forEach { candidates += it.groupValues[1] }
        return candidates.firstNotNullOfOrNull { raw ->
            val decoded = raw.replace("\\/", "/").replace("\\u002F", "/")
                .replace("\\u003A", ":").replace("\\u0026", "&")
                .replace("\\\"", "\"").replace("\\\\", "\\")
            if (baseUrl != null) normalizeUrl(decoded, baseUrl)
            else decoded.takeIf(PlaybackUrlPolicy::isSafeHttpUrl)
        }
    }

    private fun decodedVariants(raw: String): List<String> {
        val base = raw.replace("\\/", "/").replace("\\u002F", "/").replace("\\u003A", ":")
        val variants = linkedSetOf(raw, base)
        atobPattern.findAll(raw).forEach { match ->
            val token = match.groupValues[1]
            runCatching {
                val padded = token + "=".repeat((4 - token.length % 4) % 4)
                String(Base64.decode(padded, Base64.DEFAULT), StandardCharsets.UTF_8)
            }.getOrNull()?.let(variants::add)
        }
        return variants.toList()
    }

    private fun normalizeUrl(raw: String, baseUrl: String): String? {
        var value = raw.trim().trim('"', '\'')
            .replace("\\/", "/")
            .replace("\\u002F", "/")
            .replace("\\u003A", ":")
            .replace("&amp;", "&")
        if (value.startsWith("//")) value = "https:$value"
        val resolved = runCatching { URL(URL(baseUrl), value).toString() }.getOrNull() ?: return null
        return resolved.takeIf(PlaybackUrlPolicy::isSafeHttpUrl)
    }

    /** Extracts the signed HLS URL embedded by the authorized Dadinho provider page. */
    fun extractDadinhoStreamUrl(html: String): String? {
        val pattern = Regex("""STREAM_URLS\s*=\s*\[\s*"([^"]+)""")
        val candidate = pattern.find(html)?.groupValues?.getOrNull(1)?.replace("\\/", "/") ?: return null
        return candidate.takeIf(PlaybackUrlPolicy::isSafeHttpUrl)
    }

    fun resolve(base: String, rawReference: String): String? {
        val normalizedBase = if (base.endsWith("/")) base else "$base/"
        val reference = rawReference.trim()
        if (reference.isBlank() || reference.contains("://") ||
            reference.split('/').any { it == ".." }) return null

        val baseUrl = runCatching { URL(normalizedBase) }.getOrNull() ?: return null
        if (baseUrl.protocol !in setOf("http", "https") ||
            baseUrl.host.endsWith(".invalid", ignoreCase = true)) return null

        // Match SaimoTV VodFormato: refs with an extension are used as-is; otherwise append .mp4.
        val suffix = if (reference.contains('.')) reference else "$reference.mp4"
        val candidate = normalizedBase + suffix
        val parsed = runCatching { URL(candidate) }.getOrNull() ?: return null
        if (parsed.protocol !in setOf("http", "https") ||
            parsed.host.endsWith(".invalid", ignoreCase = true)) return null
        return candidate
    }
}
