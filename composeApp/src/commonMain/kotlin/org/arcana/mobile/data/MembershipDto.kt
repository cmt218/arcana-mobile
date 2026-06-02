package org.arcana.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MembershipMeDto(
    val member: MemberDto,
    val membership: MembershipBriefDto,
    @SerialName("current_period") val currentPeriod: CurrentPeriodDto? = null,
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
)
