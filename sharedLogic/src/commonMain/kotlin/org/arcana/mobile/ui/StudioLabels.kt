package org.arcana.mobile.ui

/**
 * Display-friendly location label: the studio prefix is stripped so a row
 * reads "Williamsburg", not "YO BK Williamsburg" under the YO BK card. Shared
 * by the favorites manager, the schedule filter, and ScheduleViewModel so all
 * label identically. (Title Case — distinct from
 * `org.arcana.mobile.schedule.locationShortLabel`, which uppercases for the
 * row meta line.)
 *
 * Lives in :sharedLogic (package kept as `ui` so existing imports stay valid) —
 * it is pure string logic with no Compose dependency.
 */
fun studioLocationLabel(studioName: String, locationName: String): String {
    val raw = locationName.removePrefix(studioName).trim()
        .removePrefix("·").trim()
        .removePrefix("-").trim()
    return raw.ifEmpty { locationName }
}
