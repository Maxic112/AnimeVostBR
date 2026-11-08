package com.animevostbr.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** `at` is the release time in ms; `episode` is e.g. "6 серия" when the title names it. */
data class ScheduleEntry(val title: Title, val at: Long, val episode: String?)

data class ScheduleDay(val date: LocalDate, val entries: List<ScheduleEntry>)

/** Upcoming episodes of the favorites, by day. */
object Schedule {

    private val NEXT_NUMBER = Regex("""\[(\d+) серия - """)

    /** An episode released a little while ago still shows: the voice-over is often late. */
    private const val GRACE_MS = 2 * 60 * 60 * 1000L

    /** Days in time order, and the titles with no upcoming date (finished or not announced). */
    fun days(titles: List<Title>, nowMs: Long, zone: ZoneId): Pair<List<ScheduleDay>, List<Title>> {
        val (dated, undated) = titles.partition { (it.timer ?: 0L) * 1000 > nowMs - GRACE_MS }
        val days = dated
            .map { t ->
                ScheduleEntry(t, t.timer!! * 1000, NEXT_NUMBER.find(t.title)?.groupValues?.get(1)?.let { "$it серия" })
            }
            .sortedBy { it.at }
            .groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
            .map { (date, entries) -> ScheduleDay(date, entries) }
        return days to undated
    }
}
