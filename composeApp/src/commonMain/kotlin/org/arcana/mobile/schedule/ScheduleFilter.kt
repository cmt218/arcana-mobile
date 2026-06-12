package org.arcana.mobile.schedule

/**
 * Expand a selection (whole-studio slugs + individual location ids) into the
 * flat, deduped `location_id` list the schedule fetch sends. Whole studios
 * resolve to their location ids via [catalog] (slug → its location ids).
 * `studio_slug` is never sent (the server ANDs the two params), so a mixed
 * multi-studio set MUST be expressed as locations only — this is that.
 */
internal fun expandSelectionToLocationIds(
    studioSlugs: Set<String>,
    locationIds: Set<Int>,
    catalog: Map<String, List<Int>>,
): List<Int> =
    (studioSlugs.flatMap { catalog[it].orEmpty() } + locationIds).distinct()

/**
 * The collapsed filter-bar summary label. Pure so it's unit-testable without a
 * VM. Favorites → "Favorites"; All-Studios → "All Studios"; Custom → the
 * selection ("Filter" when nothing picked yet, else "BARRY'S · 2 locations" /
 * "3 Studios"). "Studios touched" = studios selected whole OR with any
 * individual location.
 */
internal fun filterSummary(
    filterMode: FilterMode,
    selectedStudioSlugs: Set<String>,
    selectedLocationIds: Set<Int>,
    studioNamesBySlug: Map<String, String>,
    locationStudioSlugById: Map<Int, String>,
): String {
    when (filterMode) {
        FilterMode.Favorites -> return "Favorites"
        FilterMode.AllStudios -> return "All Studios"
        FilterMode.Custom -> {
            val touchedSlugs = selectedStudioSlugs +
                selectedLocationIds.mapNotNull { locationStudioSlugById[it] }
            if (touchedSlugs.isEmpty()) return "Filter"
            return if (touchedSlugs.size == 1) {
                val slug = touchedSlugs.single()
                val name = (studioNamesBySlug[slug] ?: slug).uppercase()
                if (slug in selectedStudioSlugs) {
                    name
                } else {
                    val k = selectedLocationIds.count { locationStudioSlugById[it] == slug }
                    val word = if (k == 1) "location" else "locations"
                    "$name · $k $word"
                }
            } else {
                "${touchedSlugs.size} Studios"
            }
        }
    }
}
