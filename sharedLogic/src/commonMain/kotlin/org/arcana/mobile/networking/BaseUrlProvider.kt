package org.arcana.mobile.networking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.analytics.classifyEnvironment
import org.arcana.mobile.auth.SecureStorage

/**
 * Persisted override for the API base URL.
 *
 * The fallback default is the PROD hostname (`https://api.arcana.fit`) on both
 * platforms — see `defaultBaseUrl()` in the platform actuals — so a fresh
 * install reaches prod with no setup. Local/dev work overrides it at runtime
 * through the Developer Settings screen (the value persists across launches):
 * `http://localhost:8000` on the iOS simulator, `http://10.0.2.2:8000` on the
 * Android emulator, or a Cloudflare quick-tunnel URL on a physical device.
 * Quick-tunnel URLs change on every `cloudflared` restart, which is why this
 * runtime override exists rather than a rebuild.
 *
 * `ArcanaApiClient` calls [get] on every request, so URL changes apply to
 * the very next outbound call with no client recreation.
 */
class BaseUrlProvider(
    private val storage: SecureStorage,
    private val telemetry: Telemetry,
    val defaultUrl: String,
) {

    private val _current = MutableStateFlow(load())

    /** Observable for UI binding (Developer Settings shows the live value). */
    val current: StateFlow<String> = _current

    init {
        // Tag the analytics environment from the resolved base URL up front, so
        // events are attributed to prod vs local/tunnel from the first one.
        syncEnvironment()
    }

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
        syncEnvironment()
    }

    /** Forget the override and fall back to [defaultUrl]. */
    fun reset() {
        storage.delete(KEY)
        _current.value = defaultUrl
        syncEnvironment()
    }

    /** Re-derive and register the analytics `environment` from the current URL.
     *  Called on construction and on every base-URL change so a dev override
     *  (localhost / tunnel) re-tags subsequent events out of the prod metrics. */
    private fun syncEnvironment() {
        telemetry.setEnvironment(classifyEnvironment(_current.value))
    }

    /** True when an override is in effect (vs. the bundled default). */
    val isOverridden: Boolean
        get() = _current.value != defaultUrl

    private fun load(): String = storedUrl(storage, defaultUrl)

    companion object {
        private const val KEY = "base_url"

        /** Resolve the persisted override without constructing the provider,
         *  for callers that run before Koin (see [TelemetryGate]). */
        fun storedUrl(storage: SecureStorage, defaultUrl: String): String =
            storage.load(KEY)?.takeIf { it.isNotBlank() } ?: defaultUrl
    }
}
