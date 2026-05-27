package org.arcana.mobile.networking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.arcana.mobile.auth.SecureStorage

/**
 * Persisted override for the API base URL.
 *
 * Pre-launch flow: the server runs locally on Cole's Mac and is exposed to
 * physical devices via Cloudflare quick tunnels (URLs that change on every
 * restart). Rather than rebuilding the app each time, the URL is editable at
 * runtime through the Developer Settings screen and persists across launches.
 *
 * The fallback default is platform-specific (see `defaultBaseUrl()` in the
 * platform actuals) — `10.0.2.2:8000` on Android (emulator loopback) and
 * `localhost:8000` on iOS (simulator). Physical devices always need an
 * override; the default is purely for local emulator / simulator dev.
 *
 * Post-launch: the platform defaults will move to the prod API hostname so
 * fresh installs work out of the box. See `arcana-mobile/CLAUDE.md` →
 * "Temporary debug treatment" for the cutover checklist.
 *
 * `ArcanaApiClient` calls [get] on every request, so URL changes apply to
 * the very next outbound call with no client recreation.
 */
class BaseUrlProvider(
    private val storage: SecureStorage,
    val defaultUrl: String,
) {

    private val _current = MutableStateFlow(load())

    /** Observable for UI binding (Developer Settings shows the live value). */
    val current: StateFlow<String> = _current

    /** Read the current base URL — called per-request by `ArcanaApiClient`. */
    fun get(): String = _current.value

    /** Persist a user-entered override. Trailing slashes are normalized off. */
    fun set(url: String) {
        val normalized = url.trim().trimEnd('/')
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "Base URL must start with http:// or https://"
        }
        storage.save(KEY, normalized)
        _current.value = normalized
    }

    /** Forget the override and fall back to [defaultUrl]. */
    fun reset() {
        storage.delete(KEY)
        _current.value = defaultUrl
    }

    /** True when an override is in effect (vs. the bundled default). */
    val isOverridden: Boolean
        get() = _current.value != defaultUrl

    private fun load(): String = storage.load(KEY)?.takeIf { it.isNotBlank() } ?: defaultUrl

    companion object {
        private const val KEY = "base_url"
    }
}
