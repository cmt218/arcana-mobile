package org.arcana.mobile.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.arcana.mobile.SharedAndroidContext
import org.arcana.mobile.logWarning
import org.arcana.mobile.maps.MapTarget
import org.arcana.mobile.maps.geoUri

/** The `geo:` intent goes through the system chooser, so one menu entry
 *  covers Google Maps and whatever else is installed. */
actual object MapsLauncher {
    actual fun availableApps(): List<MapsApp> = listOf(MapsApp.Google)

    actual fun open(app: MapsApp, target: MapTarget) {
        val ctx = SharedAndroidContext.require()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri(target)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            logWarning("MapsLauncher", "No maps app handles geo: intents")
        }
    }

    actual fun copyAddress(address: String) {
        val ctx = SharedAndroidContext.require()
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Address", address))
    }
}
