package org.arcana.mobile.networking

/** Reason codes for a submit that failed at the transport level rather than
 *  for a reason the server named. */
const val CONNECTION_FAILED = "connection_failed"
const val SERVER_FAILED = "server_failed"

/** Copy for the two transport codes, or null if [code] is a server reason the
 *  caller should map itself. Shared so every submit flow words these the same. */
fun transportErrorCopy(code: String): String? = when (code) {
    CONNECTION_FAILED -> "Couldn't reach Arcana. Check your connection and try again."
    SERVER_FAILED -> "Something went wrong on our end. Try again in a moment."
    else -> null
}

/** Maps a caught failure to [CONNECTION_FAILED] or [SERVER_FAILED]. */
fun Throwable.transportFailureCode(): String =
    if (toErrorType() == ErrorType.SERVER) SERVER_FAILED else CONNECTION_FAILED
