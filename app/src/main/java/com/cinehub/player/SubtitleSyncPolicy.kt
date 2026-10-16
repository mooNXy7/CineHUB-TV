package com.cinehub.player

import java.util.Locale

/** Shifts SRT/WebVTT cue timestamps without changing cue text or metadata. */
internal object SubtitleSyncPolicy {
    private val timestampPattern = Regex("""(?<!\d)(?:\d{1,2}:)?\d{2}:\d{2}[,.]\d{3}(?!\d)""")

    fun shiftTimestamps(text: String, offsetMs: Long): String {
        if (offsetMs == 0L || text.isEmpty()) return text
        return timestampPattern.replace(text) { match ->
            val original = match.value
            val parts = original.replace(',', '.').split(':')
            val secondsParts = parts.last().split('.')
            if (secondsParts.size != 2) return@replace original
            val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return@replace original else 0L
            val minutes = (if (parts.size == 3) parts[1] else parts[0]).toLongOrNull()
                ?: return@replace original
            val seconds = secondsParts[0].toLongOrNull() ?: return@replace original
            val millis = secondsParts[1].toLongOrNull() ?: return@replace original
            val shifted = (hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + millis + offsetMs)
                .coerceAtLeast(0L)
            val h = shifted / 3_600_000L
            val m = (shifted / 60_000L) % 60L
            val s = (shifted / 1_000L) % 60L
            val ms = shifted % 1_000L
            val delimiter = if (original.contains(',')) ',' else '.'
            if (parts.size == 3) {
                String.format(Locale.US, "%02d:%02d:%02d%c%03d", h, m, s, delimiter, ms)
            } else {
                val totalMinutes = shifted / 60_000L
                String.format(Locale.US, "%02d:%02d%c%03d", totalMinutes, s, delimiter, ms)
            }
        }
    }
}
