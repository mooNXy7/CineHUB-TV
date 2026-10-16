package com.cinehub.player

/**
 * Normalizes channel endpoints before exposing them to the UI or the playback Intent.
 * Keep the list bounded because TV launch Intents should not carry an unbounded catalog.
 */
internal object ChannelSourcePolicy {
    const val MAX_SOURCES_PER_CHANNEL = 12

    fun normalize(primary: String, candidates: List<String>): List<String> =
        (listOf(primary) + candidates)
            .asSequence()
            .map(String::trim)
            .filter(PlaybackUrlPolicy::isSafeHttpUrl)
            .distinct()
            .take(MAX_SOURCES_PER_CHANNEL)
            .toList()
}
