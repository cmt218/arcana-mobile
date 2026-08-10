package org.arcana.mobile.favorites

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.logWarning
import org.arcana.mobile.networking.FavoritesApi

/**
 * Session-lifetime cache of the member's favorites. Koin single — it outlives
 * the session-scoped ViewModelStore, so AppSessionController wires [clear] as its
 * onSessionCleared and wipes it on logout to keep
 * one member's favorites from flashing for the next.
 *
 * `favorites.value == null` means "not loaded yet" (distinct from an empty
 * FavoritesDto, which means "loaded; member has none").
 */
class FavoritesRepository(private val api: FavoritesApi) {

    private val _favorites = MutableStateFlow<FavoritesDto?>(null)
    val favorites: StateFlow<FavoritesDto?> = _favorites

    /** Fetch from the server. On failure, keeps (and returns) the prior
     *  cached value — stale favorites beat no favorites for filter UX. */
    suspend fun refresh(): FavoritesDto? = try {
        api.fetchFavorites().also { _favorites.value = it }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logWarning("FavoritesRepository", e.message ?: "favorites fetch failed")
        _favorites.value
    }

    /** Replace-set write-through (PUT). Throws on failure — callers surface
     *  the error (the management screen shows a retry). */
    suspend fun save(studioSlugs: List<String>, locationIds: List<Int>): FavoritesDto =
        api.updateFavorites(studioSlugs, locationIds).also { _favorites.value = it }

    fun clear() {
        _favorites.value = null
    }
}
