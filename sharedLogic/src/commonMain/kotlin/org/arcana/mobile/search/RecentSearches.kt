package org.arcana.mobile.search

import org.arcana.mobile.auth.SecureStorage

/** Last-N device-local search queries, newest first. One newline-joined
 *  stored value — tiny, per-device, survives restarts, never synced.
 *  Construct via [backedBy]; the seam keeps commonTest off SecureStorage. */
class RecentSearches internal constructor(
    private val loadRaw: () -> String?,
    private val saveRaw: (String) -> Unit,
    private val deleteRaw: () -> Unit,
) {
    fun all(): List<String> =
        loadRaw()?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun record(query: String) {
        val cleaned = query.trim()
        if (cleaned.length < MIN_LENGTH) return
        // Collapse typing intermediates: an existing entry that is a prefix of
        // the new query (slow forward typing) or an extension of it
        // (backspacing) was never a deliberate search of its own.
        val kept = all().filterNot {
            it.startsWith(cleaned, ignoreCase = true) ||
                cleaned.startsWith(it, ignoreCase = true)
        }
        saveRaw((listOf(cleaned) + kept).take(MAX_ENTRIES).joinToString("\n"))
    }

    fun clear() {
        // Delete rather than save("") — iOS Keychain save cannot take an
        // empty value (zero-length array crash, caught live 2026-08-30).
        deleteRaw()
    }

    companion object {
        private const val KEY = "recent_searches"
        private const val MIN_LENGTH = 2
        const val MAX_ENTRIES = 8

        fun backedBy(storage: SecureStorage) = RecentSearches(
            loadRaw = { storage.load(KEY) },
            saveRaw = { storage.save(KEY, it) },
            deleteRaw = { storage.delete(KEY) },
        )
    }
}
