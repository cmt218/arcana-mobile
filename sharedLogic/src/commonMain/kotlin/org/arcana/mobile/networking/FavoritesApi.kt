package org.arcana.mobile.networking

import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.StudioDto

/** Narrow interface over favorites + studio-directory endpoints so ViewModels
 *  and the FavoritesRepository can be faked in commonTest. */
interface FavoritesApi {
    suspend fun fetchStudios(): List<StudioDto>
    suspend fun fetchFavorites(): FavoritesDto
    suspend fun updateFavorites(studioSlugs: List<String>, locationIds: List<Int>): FavoritesDto
}
