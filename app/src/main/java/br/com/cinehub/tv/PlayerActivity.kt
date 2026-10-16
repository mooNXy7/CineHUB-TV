package br.com.cinehub.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Test-only compatibility bridge: preserves the TV app's existing launch contract
 * while handing playback to the upgraded CineHUB PLAYER engine integrated in this APK.
 */
class PlayerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceList = intent.getStringArrayListExtra(EXTRA_SOURCES)?.distinct().orEmpty()
        val single = intent.getStringExtra(EXTRA_URL)
        val sources = (sourceList + listOfNotNull(single)).filter {
            it.startsWith("https://") || it.startsWith("http://")
        }.distinct()

        val title = intent.getStringExtra(EXTRA_PLAYBACK_TITLE)
            ?: intent.getStringExtra(EXTRA_TITLE)
            ?: "CineHUB TV"
        val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID).orEmpty()
        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE).orEmpty()
        val isRadio = mediaType.equals("radio", true) || intent.getBooleanExtra("cinehub_radio", false)
        val isLive = isRadio || mediaId.isBlank() || mediaType.equals("live", true)

        val playback = Intent(this, com.cinehub.player.PlayerActivity::class.java).apply {
            putExtra("url", sources.firstOrNull().orEmpty())
            putStringArrayListExtra("sources", ArrayList(sources))
            putExtra("live", isLive)
            putExtra("radio", isRadio)
            if (isRadio) putExtra("content_type", "radio") else if (isLive) putExtra("content_type", "live")
            putExtra("content_id", mediaId.ifBlank { "live:" + title.lowercase().replace(Regex("[^a-z0-9]+"), "-") })
            putExtra("title", title)
            putExtra("tmdb_id", intent.getIntExtra("tmdb_id", 0))
            putExtra("series_id", intent.getStringExtra("series_id").orEmpty())
            putExtra("catalog_category", when (mediaType.lowercase()) {
                "movie", "movies", "filme", "filmes" -> "movies"
                "series", "série", "series" -> "series"
                "anime", "animes" -> "anime"
                "dorama", "doramas" -> "doramas"
                else -> ""
            })
            putExtra("catalog_item_id", mediaId)
            putExtra("subtitle_url", intent.getStringExtra("subtitle_url").orEmpty())
            putExtra("season_number", intent.getIntExtra("season_number", 0))
            putExtra("episode_number", intent.getIntExtra("episode_number", 0))
            putExtra("poster_url", intent.getStringExtra(EXTRA_POSTER).orEmpty())
            putExtra("backdrop_url", intent.getStringExtra(EXTRA_BACKDROP).orEmpty())
        }
        if (sources.isEmpty()) {
            finish()
            return
        }
        startActivity(playback)
        finish()
    }

    companion object {
        const val EXTRA_URL = "br.com.cinehub.tv.STREAM_URL"
        const val EXTRA_SOURCES = "br.com.cinehub.tv.STREAM_SOURCES"
        const val EXTRA_TITLE = "br.com.cinehub.tv.STREAM_TITLE"
        const val EXTRA_MEDIA_ID = "br.com.cinehub.tv.MEDIA_ID"
        const val EXTRA_PLAYBACK_TITLE = "br.com.cinehub.tv.PLAYBACK_TITLE"
        const val EXTRA_POSTER = "br.com.cinehub.tv.MEDIA_POSTER"
        const val EXTRA_BACKDROP = "br.com.cinehub.tv.MEDIA_BACKDROP"
        const val EXTRA_MEDIA_TYPE = "br.com.cinehub.tv.MEDIA_TYPE"
        const val EXTRA_YEAR = "br.com.cinehub.tv.MEDIA_YEAR"
        const val EXTRA_RATING = "br.com.cinehub.tv.MEDIA_RATING"
        const val EXTRA_DESCRIPTION = "br.com.cinehub.tv.MEDIA_DESCRIPTION"
        const val EXTRA_LANGUAGE = "br.com.cinehub.tv.MEDIA_LANGUAGE"
        const val EXTRA_COUNTRIES = "br.com.cinehub.tv.MEDIA_COUNTRIES"
        const val EXTRA_GENRES = "br.com.cinehub.tv.MEDIA_GENRES"
        const val EXTRA_RESUME_POSITION = "br.com.cinehub.tv.RESUME_POSITION"
    }
}
