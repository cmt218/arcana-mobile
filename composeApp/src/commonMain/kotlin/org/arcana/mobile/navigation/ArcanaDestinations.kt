package org.arcana.mobile.navigation

import kotlinx.serialization.Serializable

sealed interface ArcanaDestination {
    @Serializable data object Home : ArcanaDestination
    @Serializable data object Schedule : ArcanaDestination
    @Serializable data object Profile : ArcanaDestination

    @Serializable data object StudioSelection : ArcanaDestination
}
