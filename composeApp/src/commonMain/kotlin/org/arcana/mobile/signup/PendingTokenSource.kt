package org.arcana.mobile.signup

/**
 * First-launch recovery of a welcome token when the deep link itself didn't carry
 * one into the app. iOS reads (and clears) the clipboard fallback format
 * `arcana:welcome:<token>`; Android reads the Play Install Referrer. Returns null
 * when there's nothing to recover.
 */
expect class PendingTokenSource() {
    suspend fun consumePendingToken(): String?
}
