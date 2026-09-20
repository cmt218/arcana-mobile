package org.arcana.mobile.review

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.arcana.mobile.auth.SecureStorage

/**
 * Comments a member typed that the server does not have yet, by review id.
 * A save can fail on the subway and a screen can be closed mid-thought: the
 * words come back the next time that review's card opens. One storage key and
 * a small cap, so a sign-out clears it in one call and it cannot grow.
 */
class ReviewDrafts internal constructor(
    private val loadRaw: () -> String?,
    private val saveRaw: (String) -> Unit,
    private val deleteRaw: () -> Unit,
) {
    fun load(reviewId: Int): String? = all()[reviewId]?.takeIf { it.isNotBlank() }

    fun save(reviewId: Int, text: String) {
        if (text.isBlank()) return clear(reviewId)
        // Re-inserted last, so the cap below always drops the oldest draft.
        write(all() - reviewId + (reviewId to text))
    }

    fun clear(reviewId: Int) {
        val drafts = all()
        if (reviewId in drafts) write(drafts - reviewId)
    }

    fun clearAll() = deleteRaw()

    private fun all(): Map<Int, String> =
        loadRaw()?.let { raw -> runCatching { Json.decodeFromString(FORMAT, raw) }.getOrNull() }.orEmpty()

    private fun write(drafts: Map<Int, String>) {
        // Delete rather than save an empty value: the iOS Keychain cannot take one.
        if (drafts.isEmpty()) return deleteRaw()
        saveRaw(Json.encodeToString(FORMAT, drafts.entries.toList().takeLast(MAX_DRAFTS).associate { it.toPair() }))
    }

    companion object {
        private const val KEY = "review_drafts"
        const val MAX_DRAFTS = 5
        private val FORMAT = MapSerializer(Int.serializer(), String.serializer())

        /** Holds drafts for this process only: tests and previews. */
        fun inMemory(): ReviewDrafts {
            var raw: String? = null
            return ReviewDrafts(loadRaw = { raw }, saveRaw = { raw = it }, deleteRaw = { raw = null })
        }

        fun backedBy(storage: SecureStorage) = ReviewDrafts(
            loadRaw = { storage.load(KEY) },
            saveRaw = { storage.save(KEY, it) },
            deleteRaw = { storage.delete(KEY) },
        )
    }
}
