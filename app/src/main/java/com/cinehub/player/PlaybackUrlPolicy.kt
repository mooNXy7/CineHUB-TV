package com.cinehub.player

import java.net.URI
import java.net.URL

/**
 * Pure URL policy shared by playback entry points and unit tests.
 * Only absolute HTTP(S) URLs with a host and without embedded credentials are accepted.
 */
internal object PlaybackUrlPolicy {
    fun isSafeHttpUrl(value: String): Boolean {
        if (value.isBlank() || value.any { it.isWhitespace() }) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        // URI.host rejects legacy CDN host labels containing underscores. Saimo's
        // transport normalizes those hosts before TLS/DNS, so validate with URL too.
        val parsedUrl = runCatching { URL(value) }.getOrNull() ?: return false
        return uri.isAbsolute &&
            scheme in setOf("http", "https") &&
            parsedUrl.protocol.lowercase() in setOf("http", "https") &&
            parsedUrl.host.isNotBlank() &&
            uri.rawUserInfo == null &&
            parsedUrl.userInfo == null
    }
}
