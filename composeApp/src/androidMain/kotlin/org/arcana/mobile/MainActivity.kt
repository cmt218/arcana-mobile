package org.arcana.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import org.arcana.mobile.navigation.DeepLinkHandler

class MainActivity : ComponentActivity() {

    // Read by the setContent composition; updated on warm-start (onNewIntent) so a
    // link arriving while the app is running recomposes App with the new token.
    private val pendingDeepLinkToken = mutableStateOf<String?>(null)

    // Persisted across recreation so a config change after the link was consumed
    // (or after the user chose "log in instead") doesn't re-route into signup from
    // the still-present launch intent.
    private var deepLinkConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        deepLinkConsumed = savedInstanceState?.getBoolean(STATE_DEEP_LINK_CONSUMED) ?: false
        pendingDeepLinkToken.value = if (deepLinkConsumed) null else extractToken(intent)
        setContent {
            App(
                initialWelcomeToken = pendingDeepLinkToken.value,
                onWelcomeTokenConsumed = {
                    pendingDeepLinkToken.value = null
                    deepLinkConsumed = true
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val token = extractToken(intent)
        if (token != null) {
            deepLinkConsumed = false
            pendingDeepLinkToken.value = token
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_DEEP_LINK_CONSUMED, deepLinkConsumed)
    }

    private fun extractToken(intent: Intent?): String? =
        intent?.data?.toString()?.let { DeepLinkHandler.extractWelcomeToken(it) }

    companion object {
        private const val STATE_DEEP_LINK_CONSUMED = "deep_link_consumed"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
