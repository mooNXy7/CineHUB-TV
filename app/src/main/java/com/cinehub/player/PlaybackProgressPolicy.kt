package com.cinehub.player

/**
 * Returns the position safe to persist when the activity leaves the foreground.
 * A null result means playback never reached a stable state, so the previous
 * saved position must be preserved instead of being overwritten with zero.
 */
internal object PlaybackProgressPolicy {
    private const val COMPLETION_WINDOW_MS = 60_000L

    fun positionToPersist(
        positionMs: Long,
        durationMs: Long,
        isReady: Boolean,
        isEnded: Boolean
    ): Long? {
        if (!isReady && !isEnded) return null
        if (isEnded) return 0L
        if (positionMs < 0L) return null
        if (durationMs > 0L && positionMs >= durationMs - COMPLETION_WINDOW_MS) return 0L
        return positionMs
    }
}
