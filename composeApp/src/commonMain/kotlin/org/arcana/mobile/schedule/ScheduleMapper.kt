package org.arcana.mobile.schedule

import org.arcana.mobile.data.LocationBriefDto
import org.arcana.mobile.data.SchedulePageDto
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.logWarning

/**
 * Rejoin a normalized session page into the nested [ScheduleSessionDto] graph
 * the row UI already renders — pagination changes the wire shape, not the UI.
 * A session referencing a missing lookup key is skipped with a warning rather
 * than crashing the schedule (defensive: only possible via a server bug).
 */
fun SchedulePageDto.toSessions(): List<ScheduleSessionDto> = sessions.mapNotNull { flat ->
    val template = templates[flat.templateId.toString()]
    val location = locations[flat.locationId.toString()]
    val studio = location?.let { studios[it.studioId.toString()] }
    if (template == null || location == null || studio == null) {
        logWarning("ScheduleMapper", "session ${flat.id} references missing lookup; dropped")
        return@mapNotNull null
    }
    ScheduleSessionDto(
        id = flat.id,
        startAt = flat.startAt,
        endAt = flat.endAt,
        durationMinutes = flat.durationMinutes,
        status = flat.status,
        platformCapacity = flat.platformCapacity,
        platformBooked = flat.platformBooked,
        arcanaSpotsOffered = flat.arcanaSpotsOffered,
        arcanaSpotsAvailable = flat.arcanaSpotsAvailable,
        template = template,
        instructors = flat.instructorIds.mapNotNull { instructors[it.toString()] },
        location = LocationBriefDto(
            id = location.id,
            name = location.name,
            timezone = location.timezone,
            studio = studio,
        ),
        bookableAt = flat.bookableAt,
    )
}
