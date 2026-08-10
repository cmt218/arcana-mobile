package org.arcana.mobile.auth

/**
 * Observability seam for the platform secure store (iOS Keychain / Android
 * EncryptedSharedPreferences).
 *
 * [SecureStorage] is an `expect class` with a no-arg constructor, so it cannot
 * take a `Telemetry` dependency. This object is the seam instead: the platform
 * `actual`s report every non-success result here, and the app wires [listener]
 * to Telemetry once, at DI time.
 *
 * **Why this exists.** A member was force-logged-out on 2026-07-16 with a
 * perfectly valid session (the server answered their token refresh `200`), and
 * it could not be explained afterwards: the Keychain layer collapses every
 * `OSStatus` into a bare `null`, so "the token is temporarily unreadable"
 * (device locked → `errSecInteractionNotAllowed`) is indistinguishable from
 * "there is genuinely no token" (a real logout → `errSecItemNotFound`) — and
 * neither was reported anywhere. Diagnosing it required correlating PostHog
 * against raw server logs, and still landed on a hypothesis rather than an
 * answer. This records what the platform actually returned so the next
 * occurrence is answerable directly.
 *
 * **This is reporting only.** Nothing here may change storage behavior — the
 * callers still see exactly the values they saw before. The behavioral fix
 * (making a failed read/write stop destroying a valid session) is deliberately
 * a separate change, to be made once this telemetry says which status fires.
 */
object SecureStorageDiagnostics {

    /**
     * A non-success result from the platform secure store.
     *
     * [status] is the raw platform code, kept raw on purpose so we can tell the
     * exact failure apart: on iOS an `OSStatus` (`-25300` errSecItemNotFound,
     * `-25308` errSecInteractionNotAllowed, `-25299` errSecDuplicateItem, ...);
     * on Android [STATUS_EXCEPTION], which only signals "threw".
     */
    data class Failure(val op: String, val key: String, val status: Int)

    /** Android has no status code — it throws. Distinct from any real OSStatus. */
    const val STATUS_EXCEPTION: Int = Int.MIN_VALUE

    object Op {
        const val LOAD = "load"
        const val SAVE = "save"
        const val DELETE = "delete"
    }

    /**
     * Forwards *notable* failures to Telemetry. Wired once when Telemetry is
     * constructed (see AppModule). Failures raised before that are still
     * recorded — they just aren't emitted as their own event.
     */
    var listener: ((Failure) -> Unit)? = null

    /**
     * Last failure per key.
     *
     * Per-key on purpose: the secure store is not only used for tokens — it also
     * holds `base_url_override`, the signup-survey flags, and the recovery flag,
     * which are *routinely* absent (`errSecItemNotFound`) for most members. A
     * single global "last failure" would therefore attribute a stale, unrelated
     * `base_url_override` miss to a forced logout, which is worse than reporting
     * nothing at all.
     */
    private val lastFailureByKey = mutableMapOf<String, Failure>()

    /**
     * What the store last did for [key] — the field that answers "was it locked,
     * or genuinely gone?" when a token reads back null.
     */
    fun lastFailureFor(key: String): Failure? = lastFailureByKey[key]

    /**
     * Record a non-success result.
     *
     * [notable] separates "this is worth its own event" from "record it for
     * attribution only". A routine `errSecItemNotFound` (no value has ever been
     * written — a signed-out member, or no dev base-URL override) is expected and
     * would be pure noise as an event, but is still exactly what we want attached
     * to a later forced logout. Platform code decides, since the meaning of a
     * status is platform-specific.
     */
    fun report(op: String, key: String, status: Int, notable: Boolean = true) {
        val failure = Failure(op, key, status)
        lastFailureByKey[key] = failure
        if (notable) listener?.invoke(failure)
    }

    /** Test hook — clears state between tests. */
    fun resetForTest() {
        lastFailureByKey.clear()
        listener = null
    }
}
