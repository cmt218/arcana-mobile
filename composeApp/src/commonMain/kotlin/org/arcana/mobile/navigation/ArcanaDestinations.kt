package org.arcana.mobile.navigation

import kotlinx.serialization.Serializable

sealed interface ArcanaDestination {
    @Serializable data object Home : ArcanaDestination
    @Serializable data object Schedule : ArcanaDestination
    @Serializable data object Profile : ArcanaDestination

    @Serializable data object StudioSelection : ArcanaDestination

    @Serializable data object MyBookings : ArcanaDestination

    @Serializable data object EditProfile : ArcanaDestination

    @Serializable data object ConciergeRequest : ArcanaDestination

    // Detail nav arg — pass the integer ClassSession id from Schedule.
    @Serializable data class ClassDetail(val id: Int) : ArcanaDestination
}
