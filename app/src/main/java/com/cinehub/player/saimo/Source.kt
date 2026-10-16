package com.cinehub.player.saimo

/** Source model used by the authorized Saimo Android playback transport. */
data class Source(
    val url: String,
    val referer: String? = null,
    val userAgent: String? = null,
    val keyId: String? = null,
    val key: String? = null,
    val quality: String? = null,
    val nome: String? = null,
) {
    val isDash: Boolean get() = url.contains(".mpd", ignoreCase = true)
    val isHls: Boolean get() =
        url.contains(".m3u8", ignoreCase = true) ||
            url.substringBefore('?').endsWith(".txt", ignoreCase = true) ||
            url.contains("dadinhodetapioca.fun", ignoreCase = true)
}
