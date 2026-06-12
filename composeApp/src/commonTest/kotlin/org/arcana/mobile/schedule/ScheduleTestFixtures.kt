@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.schedule

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDate
import org.arcana.mobile.data.FavoriteLocationDto
import org.arcana.mobile.data.FavoriteStudioDto
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.InstructorBriefDto
import org.arcana.mobile.data.LocationFlatDto
import org.arcana.mobile.data.OverviewLocationDto
import org.arcana.mobile.data.OverviewStudioDto
import org.arcana.mobile.data.ScheduleOverviewDto
import org.arcana.mobile.data.SchedulePageDto
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.data.SessionFlatDto
import org.arcana.mobile.data.StudioBriefDto
import org.arcana.mobile.data.StudioDto
import org.arcana.mobile.data.TemplateBriefDto
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.CancelBookingResponse
import org.arcana.mobile.data.CancelPolicyDto
import org.arcana.mobile.data.MyBookingsDto
import org.arcana.mobile.data.SessionBriefDto
import org.arcana.mobile.networking.BookingApi
import org.arcana.mobile.networking.FavoritesApi
import org.arcana.mobile.networking.ScheduleApi
import kotlin.test.assertTrue

/**
 * Shared fakes + fixture builders for the ScheduleViewModel test suites
 * (`ScheduleViewModelFavoritesTest`, `ScheduleViewModelPagingTest`).
 */

/** Records every overview/page call (INCLUDING the cursor param) and returns
 *  configurable results. The legacy full-window endpoint throws — the paged
 *  ViewModel must never touch it. */
internal class FakeScheduleApi : ScheduleApi {
    data class OverviewCall(
        val from: LocalDate,
        val to: LocalDate,
        val studioSlugs: List<String>?,
        val locationIds: List<Int>?,
        val availableOnly: Boolean,
    )

    data class PageCall(
        val date: LocalDate,
        val studioSlugs: List<String>?,
        val locationIds: List<Int>?,
        val availableOnly: Boolean,
        val cursor: String?,
    )

    val overviewCalls = mutableListOf<OverviewCall>()
    val pageCalls = mutableListOf<PageCall>()

    /** Suspending so tests can gate a fetch (in-flight / staleness scenarios). */
    var overviewResult: suspend (OverviewCall) -> ScheduleOverviewDto = { ScheduleOverviewDto() }
    var pageResult: suspend (PageCall) -> SchedulePageDto = { SchedulePageDto() }

    override suspend fun fetchSchedule(
        from: LocalDate,
        to: LocalDate,
        studioSlugs: List<String>?,
        locationIds: List<Int>?,
        modality: String?,
        availableOnly: Boolean,
    ): List<ScheduleSessionDto> =
        throw AssertionError("legacy GET /classes/ endpoint must not be called by the paged ViewModel")

    override suspend fun fetchOverview(
        from: LocalDate,
        to: LocalDate,
        studioSlugs: List<String>?,
        locationIds: List<Int>?,
        modality: String?,
        availableOnly: Boolean,
    ): ScheduleOverviewDto {
        val call = OverviewCall(from, to, studioSlugs, locationIds, availableOnly)
        overviewCalls += call
        return overviewResult(call)
    }

    override suspend fun fetchSessionsPage(
        date: LocalDate,
        studioSlugs: List<String>?,
        locationIds: List<Int>?,
        modality: String?,
        availableOnly: Boolean,
        cursor: String?,
    ): SchedulePageDto {
        val call = PageCall(date, studioSlugs, locationIds, availableOnly, cursor)
        pageCalls += call
        return pageResult(call)
    }
}

internal class FakeFavoritesApi(
    var favoritesResult: FavoritesDto = FavoritesDto(),
    var studiosResult: List<StudioDto> = emptyList(),
) : FavoritesApi {
    override suspend fun fetchStudios(): List<StudioDto> = studiosResult
    override suspend fun fetchFavorites(): FavoritesDto = favoritesResult
    override suspend fun updateFavorites(studioSlugs: List<String>, locationIds: List<Int>): FavoritesDto =
        favoritesResult
}

/** Booking API fake — the schedule VM only ever calls [myBookings].
 *  [myBookingsResult] is suspending so tests can gate or throw to exercise
 *  the best-effort failure path. Defaults to empty so existing suites are
 *  unaffected. */
internal class FakeBookingApi(
    var myBookingsResult: suspend () -> MyBookingsDto = { MyBookingsDto(upcoming = emptyList(), past = emptyList()) },
) : BookingApi {
    var myBookingsCalls: Int = 0

    override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?): BookingDto =
        throw AssertionError("schedule VM must not call createBooking")

    override suspend fun myBookings(): MyBookingsDto {
        myBookingsCalls += 1
        return myBookingsResult()
    }

    override suspend fun cancelBooking(bookingId: Int): CancelBookingResponse =
        throw AssertionError("schedule VM must not call cancelBooking")
}

/** A booking on [sessionId] with the given live [status] (requested/confirmed/…). */
internal fun bookingOn(sessionId: Int, status: String, id: Int = sessionId) = BookingDto(
    id = id,
    status = status,
    session = SessionBriefDto(
        id = sessionId,
        startAt = "2026-06-11T09:00:00-04:00",
        endAt = "2026-06-11T09:50:00-04:00",
        name = "Foundation 50",
        studio = "SolidCore",
    ),
    cancelPolicy = CancelPolicyDto(willForfeitCredit = false),
)

internal fun myBookings(vararg upcoming: BookingDto) =
    MyBookingsDto(upcoming = upcoming.toList(), past = emptyList())

internal fun favStudio(slug: String = "barrys", locationIds: List<Int>) = FavoriteStudioDto(
    id = 1, slug = slug, name = "Barry's", locationIds = locationIds,
)

internal fun favLocation(id: Int) = FavoriteLocationDto(
    id = id, name = "YO BK Williamsburg", studioSlug = "yo-bk", studioName = "YO BK",
)

internal fun ScheduleViewModel.success(): ScheduleUiState.Success {
    val s = uiState.value
    assertTrue(s is ScheduleUiState.Success, "expected Success but was $s")
    return s
}

/** Advance virtual time past the filter debounce so the settled refetch runs. */
internal fun TestScope.settleFilters() {
    advanceTimeBy(ScheduleViewModel.FILTER_DEBOUNCE_MS + 1)
    runCurrent()
}

// ── Page/overview fixture graph (mirrors ScheduleMapperTest's fixtures) ─────

internal val testTemplate = TemplateBriefDto(
    id = 311, name = "Foundation 50", modality = "pilates",
    heroImageUrl = "", spotSelectionMode = "none",
)
internal val testStudio = StudioBriefDto(
    id = 3, slug = "solidcore", name = "SolidCore", logoUrl = "",
    primaryColor = "#1A1A1A", lastSuccessfulSyncAt = "2026-06-10T07:32:11-04:00",
)
internal val testLocation = LocationFlatDto(
    id = 41, name = "SolidCore Williamsburg", timezone = "America/New_York", studioId = 3,
)
internal val testInstructor = InstructorBriefDto(id = 77, name = "Maya R", photoUrl = "")

internal fun flatSession(id: Int) = SessionFlatDto(
    id = id,
    startAt = "2026-06-11T09:00:00-04:00",
    endAt = "2026-06-11T09:50:00-04:00",
    durationMinutes = 50,
    status = "scheduled",
    platformCapacity = 20,
    platformBooked = 14,
    arcanaSpotsOffered = 20,
    arcanaSpotsAvailable = 6,
    templateId = 311,
    locationId = 41,
    instructorIds = listOf(77),
)

/** A minimal valid page: [sessionIds] sessions all resolving against the
 *  single template/location/studio/instructor fixture graph. */
internal fun pageOf(vararg sessionIds: Int, nextCursor: String? = null) = SchedulePageDto(
    sessions = sessionIds.map(::flatSession),
    templates = mapOf("311" to testTemplate),
    locations = mapOf("41" to testLocation),
    studios = mapOf("3" to testStudio),
    instructors = mapOf("77" to testInstructor),
    nextCursor = nextCursor,
)

/** A chip-rail studio entry for the overview. */
internal fun overviewStudio(
    slug: String,
    name: String = slug,
    locationIds: List<Int> = emptyList(),
) = OverviewStudioDto(
    id = slug.hashCode(), slug = slug, name = name,
    locations = locationIds.map { OverviewLocationDto(id = it, name = "loc$it") },
)

/** Overview = the window's chip-rail studios (filter-independent). */
internal fun overviewOf(vararg studios: OverviewStudioDto) =
    ScheduleOverviewDto(studios = studios.toList())
