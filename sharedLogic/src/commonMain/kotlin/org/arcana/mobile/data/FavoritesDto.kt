package org.arcana.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response shape for `GET/PUT /api/v1/users/me/favorites/`. Contract:
 *  `docs/superpowers/specs/2026-06-10-schedule-loading-design.md` (workspace root). */
@Serializable
data class FavoritesDto(
    val studios: List<FavoriteStudioDto> = emptyList(),
    val locations: List<FavoriteLocationDto> = emptyList(),
) {
    fun isEmpty(): Boolean = studios.isEmpty() && locations.isEmpty()

    /** Union of every studio-favorite's resolved location ids plus the
     *  explicit location favorites — the `location_id` csv the schedule
     *  fetch sends. Deduped, order-stable. */
    fun expandedLocationIds(): List<Int> =
        (studios.flatMap { it.locationIds } + locations.map { it.id }).distinct()
}

@Serializable
data class FavoriteStudioDto(
    val id: Int,
    val slug: String,
    val name: String,
    @SerialName("logo_url") val logoUrl: String = "",
    @SerialName("primary_color") val primaryColor: String = "",
    /** Active locations resolved server-side at read time — includes
     *  locations onboarded after the favorite was saved. No default on
     *  purpose: a server-side rename must fail decoding loudly, not turn
     *  studio-grain favorites into "filters nothing". */
    @SerialName("location_ids") val locationIds: List<Int>,
)

@Serializable
data class FavoriteLocationDto(
    val id: Int,
    val name: String,
    @SerialName("studio_slug") val studioSlug: String,
    @SerialName("studio_name") val studioName: String,
)

@Serializable
data class UpdateFavoritesRequest(
    @SerialName("studio_slugs") val studioSlugs: List<String>,
    @SerialName("location_ids") val locationIds: List<Int>,
)
