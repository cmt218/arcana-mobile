package org.arcana.mobile.networking

import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.CancelBookingResponse
import org.arcana.mobile.data.MembershipMeDto
import org.arcana.mobile.data.MyBookingsDto

class BookingError(val code: String) : Exception(code)

interface BookingApi {
    suspend fun createBooking(
        sessionId: Int,
        requestedSpotId: Int?,
        studioVisitedBefore: Boolean? = null,
        spotPreference: String? = null,
    ): BookingDto
    suspend fun myBookings(): MyBookingsDto
    suspend fun cancelBooking(bookingId: Int): CancelBookingResponse
}

interface MembershipApi {
    suspend fun membershipMe(): MembershipMeDto
}
