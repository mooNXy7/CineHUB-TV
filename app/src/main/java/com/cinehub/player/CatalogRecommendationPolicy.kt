package com.cinehub.player

/** Pure genre-based recommendation ranking over a bounded catalog preview. */
internal object CatalogRecommendationPolicy {
    fun select(
        entries: List<CatalogClient.CatalogEntry>,
        currentId: String,
        genres: List<String>,
        limit: Int = 8
    ): List<CatalogClient.CatalogEntry> {
        require(limit in 1..20) { "Recommendation limit must be between 1 and 20" }
        val normalizedGenres = genres.map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
        if (normalizedGenres.isEmpty()) return emptyList()
        return entries.asSequence()
            .filter { it.id != currentId }
            .map { entry ->
                entry to entry.genres.count { it.trim().lowercase() in normalizedGenres }
            }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<CatalogClient.CatalogEntry, Int>> { it.second }
                    .thenBy { it.first.title.lowercase() }
            )
            .take(limit)
            .map { it.first }
            .toList()
    }
}
