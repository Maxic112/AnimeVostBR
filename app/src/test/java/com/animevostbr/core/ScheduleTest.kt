package com.animevostbr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ScheduleTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    // 2026-11-08 12:00 Moscow
    private val now = java.time.ZonedDateTime.of(2026, 11, 8, 12, 0, 0, 0, moscow).toInstant().toEpochMilli()

    private fun title(id: Int, name: String, timer: Long?) = Title(id = id, title = name, timer = timer)

    @Test
    fun days_groupUpcomingEpisodesByLocalDate() {
        val tomorrowEvening = (now + 30 * 3600_000L) / 1000 // 9 Nov 18:00
        val laterToday = (now + 3 * 3600_000L) / 1000      // 8 Nov 15:00
        val titles = listOf(
            title(1, "Б / B [1-5 из 12+] [6 серия - 9 ноября]", tomorrowEvening),
            title(2, "А / A [1-3 из 12]", laterToday),
            title(3, "Вышел целиком / Finished [1-12 из 12]", null),
            title(4, "Давно / Long ago", (now - 5 * 3600_000L) / 1000)
        )
        val (days, undated) = Schedule.days(titles, now, moscow)
        assertEquals(listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 9)), days.map { it.date })
        assertEquals(2, days[0].entries.single().title.id)
        assertNull(days[0].entries.single().episode)
        assertEquals("6 серия", days[1].entries.single().episode)
        assertEquals(listOf(3, 4), undated.map { it.id })
    }

    @Test
    fun days_justReleasedEpisodeStillShows() {
        val hourAgo = (now - 3600_000L) / 1000
        val (days, _) = Schedule.days(listOf(title(1, "X / X", hourAgo)), now, moscow)
        assertEquals(1, days.size)
    }
}
