package com.cinehub.player

/**
 * Selects the next source in declared priority order without retrying failed URLs.
 * The current index is exclusive so a failing source is never selected again.
 */
internal object PlaybackSourcePolicy {
    fun nextUnattemptedIndex(
        sources: List<String>,
        currentIndex: Int,
        attemptedUrls: Set<String>
    ): Int? = sources.indices.firstOrNull { index ->
        index > currentIndex && sources[index] !in attemptedUrls
    }
}
