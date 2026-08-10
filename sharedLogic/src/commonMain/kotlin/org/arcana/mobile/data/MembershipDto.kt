package org.arcana.mobile.data

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MembershipMeDto(
    val member: MemberDto,
    val membership: MembershipBriefDto,
    @SerialName("current_period") val currentPeriod: CurrentPeriodDto? = null,
    // Beta-only: the next month's wallet, present only when the member has
    // bought it while still in the current month. Always null for rolling
    // subscriptions. When null, the app shows a single plain "credits" count.
    @SerialName("upcoming_period") val upcomingPeriod: CurrentPeriodDto? = null,
)

@Serializable
data class MemberDto(
    val id: Int,
    @SerialName("member_number") val memberNumber: String? = null,
    val email: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_initials") val avatarInitials: String = "",
    @SerialName("member_since") val memberSince: String? = null,
    @SerialName("lifetime_sessions") val lifetimeSessions: Int = 0,
    @SerialName("week_streak") val weekStreak: Int = 0,
)

@Serializable
data class MembershipBriefDto(
    val id: Int,
    val status: String,
    val tier: TierDto,
)

@Serializable
data class TierDto(
    val slug: String,
    val name: String,
    @SerialName("credits_per_period") val creditsPerPeriod: Int,
)

@Serializable
data class CurrentPeriodDto(
    @SerialName("payment_id") val paymentId: Int,
    @SerialName("credits_granted") val creditsGranted: Int,
    @SerialName("credits_used") val creditsUsed: Int,
    @SerialName("credits_remaining") val creditsRemaining: Int,
    @SerialName("can_browse") val canBrowse: Boolean,
    @SerialName("can_book") val canBook: Boolean,
    // Sane wallet name for beta cohorts, e.g. "July Beta" / "August Influencer".
    // Null for rolling/cohort-less wallets (the app shows a plain "credits" count).
    val label: String? = null,
    @SerialName("window_start") val windowStart: String? = null,
    @SerialName("window_end") val windowEnd: String? = null,
) {
    /** The wallet's calendar month for display, e.g. "July" from "July Beta". */
    val monthName: String? get() = label?.substringBefore(' ')?.takeIf { it.isNotBlank() }
}

/** Pick the wallet whose window contains [classStartIso] (ISO-8601). Used so a
 *  two-wallet beta member sees the balance of the wallet that will actually pay
 *  for a specific class. Falls back to the current period. */
fun MembershipMeDto.periodForClass(classStartIso: String): CurrentPeriodDto? {
    val start = runCatching { Instant.parse(classStartIso) }.getOrNull() ?: return currentPeriod
    val match = listOfNotNull(currentPeriod, upcomingPeriod).firstOrNull { p ->
        val ws = p.windowStart?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val we = p.windowEnd?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ws != null && we != null && start >= ws && start < we
    }
    return match ?: currentPeriod
}

/** The wallet that actually *covers* a class starting at [classStartIso], or
 *  null when the member holds wallet(s) but none covers that date (e.g. a
 *  July-only member viewing an August class). Unlike [periodForClass], this does
 *  NOT fall back to the current period — the null return is what lets the UI say
 *  "your plan doesn't include this month" instead of pretending the class is
 *  bookable off the wrong wallet.
 *
 *  A rolling (unbounded-window) wallet covers every date, so rolling
 *  subscribers always get a covering wallet and never hit the out-of-window
 *  state. On an unparseable date we stay lenient and fall back to the current
 *  period (never a false "outside your membership"). */
fun MembershipMeDto.coveringPeriodForClass(classStartIso: String): CurrentPeriodDto? {
    val wallets = listOfNotNull(currentPeriod, upcomingPeriod)
    // A rolling wallet (no bounded window) covers all dates — prefer the current.
    wallets.firstOrNull { it.windowStart == null || it.windowEnd == null }?.let { return it }
    val start = runCatching { Instant.parse(classStartIso) }.getOrNull() ?: return currentPeriod
    return wallets.firstOrNull { p ->
        val ws = p.windowStart?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val we = p.windowEnd?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ws != null && we != null && start >= ws && start < we
    }
}

/** The class's cohort month for member-facing copy, e.g. "August" — resolved in
 *  Eastern Time so it agrees with how the beta cohort windows are defined
 *  server-side. Null on an unparseable date. */
fun classCohortMonthName(classStartIso: String): String? {
    val tz = TimeZone.of("America/New_York")
    val local = runCatching { Instant.parse(classStartIso).toLocalDateTime(tz) }.getOrNull() ?: return null
    return local.month.name.lowercase().replaceFirstChar { it.titlecase() }
}

/** The covered month(s) phrase for the concierge popup, e.g. "July" or
 *  "July and August". Null when the member has no live wallet. */
fun MembershipMeDto.coveredMonthsPhrase(): String? {
    val months = listOfNotNull(currentPeriod?.monthName, upcomingPeriod?.monthName).distinct()
    return when (months.size) {
        0 -> null
        1 -> months[0]
        else -> months.dropLast(1).joinToString(", ") + " and " + months.last()
    }
}
