package org.arcana.mobile.navigation

import kotlinx.serialization.Serializable

sealed interface ArcanaDestination {
    @Serializable data object Home : ArcanaDestination
    @Serializable data object Schedule : ArcanaDestination
    @Serializable data object Discover : ArcanaDestination
    @Serializable data object Profile : ArcanaDestination

    // Discover studio page. `source` is the entry point for telemetry.
    // `locationId` is the location the member came from (a map pin), 0 for none:
    // the page leads with it and its Book button scopes to it.
    @Serializable data class StudioPage(
        val brandSlug: String,
        val source: String = "directory",
        val locationId: Int = 0,
    ) : ArcanaDestination

    // Member feedback feed. One screen for every scope: `scopeType` is
    // all | brand | location | class_type | instructor, `scopeValue` its id or
    // slug ("" for all), `label` the header while the page loads, `source` the
    // entry point for telemetry.
    @Serializable data class FeedbackFeed(
        val scopeType: String = "all",
        val scopeValue: String = "",
        val label: String = "",
        val source: String = "discover",
    ) : ArcanaDestination

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

    // Reservations. `source` is the entry point for telemetry: "home" or "you".
    @Serializable data class MyBookings(val source: String = "home") : ArcanaDestination

    @Serializable data object EditProfile : ArcanaDestination

    @Serializable data object ConciergeRequest : ArcanaDestination

    // Detail nav arg — pass the integer ClassSession id from Schedule.
    @Serializable data class ClassDetail(val id: Int) : ArcanaDestination
}
