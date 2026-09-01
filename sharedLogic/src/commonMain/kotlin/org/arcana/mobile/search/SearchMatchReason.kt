package org.arcana.mobile.search

import org.arcana.mobile.data.ScheduleSessionDto

/** Best-effort "why is this row here" caption for a search result — only when
 *  the class NAME isn't what matched (a name match needs no explanation).
 *  Mirrors the server's per-field contains matching; purely cosmetic, so a
 *  miss just means no caption. Copy carries no em or en dashes. */
fun searchMatchReason(session: ScheduleSessionDto, query: String): String? {
    val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null

    fun matches(text: String) = tokens.any { text.contains(it, ignoreCase = true) }

    if (matches(session.template.name)) return null
    val instructor = session.instructors.firstOrNull { matches(it.name) }
    if (instructor != null) return "Matches instructor ${instructor.name}"
    val studio = session.location.studio
    if (matches(studio.name)) return "Matches studio ${studio.name}"
    if (matches(session.location.name)) return "Matches ${session.location.name}"
    return null
}

/** Per-row captions for a rendered result list: consecutive rows with the
 *  same reason show it once (the first row of the run) — a caption introduces
 *  a run, it doesn't echo under every row. */
fun searchMatchReasons(sessions: List<ScheduleSessionDto>, query: String): List<String?> {
    var previous: String? = null
    return sessions.map { session ->
        val reason = searchMatchReason(session, query)
        val shown = reason.takeIf { it != previous }
        previous = reason
        shown
    }
}
