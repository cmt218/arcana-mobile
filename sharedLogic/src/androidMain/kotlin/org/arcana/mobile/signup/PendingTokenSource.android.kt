package org.arcana.mobile.signup

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.suspendCancellableCoroutine
import org.arcana.mobile.SharedAndroidContext
import org.arcana.mobile.logWarning
import kotlin.coroutines.resume

actual class PendingTokenSource actual constructor() {
    private val context: Context = SharedAndroidContext.require()

    actual suspend fun consumePendingToken(): String? =
        suspendCancellableCoroutine { cont ->
            val client = InstallReferrerClient.newBuilder(context).build()
            cont.invokeOnCancellation { runCatching { client.endConnection() } }
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    val token = try {
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                            val referrer = client.installReferrer.installReferrer
                            // Web fallback stashes "token=<token>" (possibly among &-joined params).
                            referrer.split('&')
                                .firstOrNull { it.startsWith("token=") }
                                ?.substringAfter("token=")
                                ?.takeIf { it.isNotEmpty() }
                        } else null
                    } catch (t: Throwable) {
                        logWarning("PendingTokenSource", "Install Referrer read failed: ${t.message}")
                        null
                    } finally {
                        runCatching { client.endConnection() }
                    }
                    if (cont.isActive) cont.resume(token)
                }
                override fun onInstallReferrerServiceDisconnected() {
                    // One-shot at install time; ignore disconnections.
                }
            })
        }
}
