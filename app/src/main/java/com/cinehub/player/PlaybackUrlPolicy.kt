package com.cinehub.player

import java.net.URI

/**
 * Pure URL policy shared by playback entry points and unit tests.
 * Only absolute HTTP(S) URLs with a host and without embedded credentials are accepted.
 */
internal object PlaybackUrlPolicy {
    fun isSafeHttpUrl(value: String): Boolean {
        if (value.isBlank() || value.any { it.isWhitespace() }) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        return uri.isAbsolute &&
            scheme in setOf("http", "https") &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null
    }
}
