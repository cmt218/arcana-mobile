package org.arcana.mobile.navigation

import kotlinx.serialization.Serializable

sealed interface ArcanaDestination {
    @Serializable data object Home : ArcanaDestination
    @Serializable data object Schedule : ArcanaDestination
    @Serializable data object Profile : ArcanaDestination

    @Serializable data object StudioSelection : ArcanaDestination

    // Origin = the Book-tab search pill's bounds in root px, so the Search
    // screen's container-transform reveal starts exactly where it was tapped.
    // Negative values (the defaults) mean "unknown — use the fallback corner".
    @Serializable data class Search(
        val originLeft: Float = -1f,
        val originTop: Float = -1f,
        val originRight: Float = -1f,
        val originBottom: Float = -1f,
    ) : ArcanaDestination

    @Serializable data object MyBookings : ArcanaDestination

    @Serializable data object EditProfile : ArcanaDestination

    @Serializable data object ConciergeRequest : ArcanaDestination

    // Detail nav arg — pass the integer ClassSession id from Schedule.
    @Serializable data class ClassDetail(val id: Int) : ArcanaDestination
}
