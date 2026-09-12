package org.arcana.mobile.networking

import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.CancelBookingResponse
import org.arcana.mobile.data.MembershipMeDto
import org.arcana.mobile.data.MyBookingsDto
import org.arcana.mobile.data.MyPastDto
import org.arcana.mobile.data.MyUpcomingDto

class BookingError(val code: String) : Exception(code)

interface BookingApi {
    suspend fun createBooking(
        sessionId: Int,
        requestedSpotId: Int?,
        studioVisitedBefore: Boolean? = null,
        spotPreference: String? = null,
    ): BookingDto
    suspend fun myBookings(): MyBookingsDto
    suspend fun myUpcoming(): MyUpcomingDto
    suspend fun myPast(cursor: String?, limit: Int = PAST_PAGE_SIZE): MyPastDto
    suspend fun cancelBooking(bookingId: Int): CancelBookingResponse

    companion object {
        const val PAST_PAGE_SIZE = 20
    }
}

interface MembershipApi {
    suspend fun membershipMe(): MembershipMeDto
}
