package org.arcana.mobile.auth

/**
 * App-review demo accounts sign in against STAGING; everyone else against prod.
 *
 * Apple and Google reviewers get credentials we set in App Store Connect / Play
 * Console. Recognizing those emails at sign-in and retargeting the base URL
 * means reviewers never see instructions, never touch production data, and
 * (under direct fulfillment) never create a real reservation at a real studio.
 * The redirect is persisted with its own marker key so a process death
 * mid-review keeps the session on staging, and it is cleared on sign-out AND on
 * any later sign-in attempt with a non-reviewer email, so a real member's
 * device can never stay stuck pointed at staging. A Developer Settings override
 * (no marker) is never touched.
 */
class ReviewerRedirect(
    private val setUrl: (String) -> Unit,
    private val resetUrl: () -> Unit,
    private val loadKey: (String) -> String?,
    private val saveKey: (String, String) -> Unit,
    private val deleteKey: (String) -> Unit,
) {

    /** Call before submitting a sign-in attempt for [email]. */
    fun applyFor(email: String) {
        if (isReviewer(email)) {
            saveKey(MARKER_KEY, "1")
            setUrl(STAGING_URL)
        } else if (loadKey(MARKER_KEY) != null) {
            clear()
        }
    }

    /** Call when the session ends (manual or forced sign-out). */
    fun onSessionEnded() {
        if (loadKey(MARKER_KEY) != null) clear()
    }

    private fun clear() {
        deleteKey(MARKER_KEY)
        resetUrl()
    }

    companion object {
        const val STAGING_URL = "https://api.staging.arcana.fit"
        private const val MARKER_KEY = "reviewer_redirect"

        // The accounts exist only in staging (seed_staging REVIEWER_ACCOUNTS);
        // nothing of value is behind them, so no need to hide the strings.
        private val REVIEWER_EMAILS = setOf(
            "apple-reviewer@test.com",
            "google-reviewer@test.com",
        )

        fun isReviewer(email: String): Boolean =
            email.trim().lowercase() in REVIEWER_EMAILS
    }
}
