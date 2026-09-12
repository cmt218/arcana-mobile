package org.arcana.mobile.ui

import org.arcana.mobile.maps.MapTarget

enum class MapsApp(val key: String, val label: String) {
    Apple("apple", "Open in Apple Maps"),
    Google("google", "Open in Google Maps"),
}

/** Platform hand-off to a maps app. URLs come from `maps/MapLinks.kt`; the
 *  actuals only decide what this device can open and open it. */
expect object MapsLauncher {
    /** Apps this device can open, in menu order. Never empty. */
    fun availableApps(): List<MapsApp>
    fun open(app: MapsApp, target: MapTarget)
    fun copyAddress(address: String)
}
