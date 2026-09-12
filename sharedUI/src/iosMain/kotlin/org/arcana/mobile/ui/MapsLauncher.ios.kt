package org.arcana.mobile.ui

import org.arcana.mobile.maps.MapTarget
import org.arcana.mobile.maps.appleMapsUrl
import org.arcana.mobile.maps.googleMapsUrl
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard

/** Google Maps appears only when its scheme can be opened, which needs
 *  `comgooglemaps` in Info.plist's `LSApplicationQueriesSchemes`. */
actual object MapsLauncher {
    actual fun availableApps(): List<MapsApp> {
        val google = NSURL.URLWithString("comgooglemaps://")
        val hasGoogle = google != null && UIApplication.sharedApplication.canOpenURL(google)
        return if (hasGoogle) listOf(MapsApp.Apple, MapsApp.Google) else listOf(MapsApp.Apple)
    }

    actual fun open(app: MapsApp, target: MapTarget) {
        val raw = when (app) {
            MapsApp.Apple -> appleMapsUrl(target)
            MapsApp.Google -> googleMapsUrl(target)
        }
        val url = NSURL.URLWithString(raw) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    actual fun copyAddress(address: String) {
        UIPasteboard.generalPasteboard.string = address
    }
}
