package net.clahey.trackr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.Instant
import kotlin.random.Random

class ReminderSchedulingTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val allDays = DayOfWeek.entries.toSet()
    private val monday = LocalDate.of(2024, 1, 1) // known Monday

    private fun instant(date: LocalDate, time: LocalTime): Instant = date.atTime(time).atZone(zone).toInstant()

    private fun fixedReminder(
        times: List<LocalTime>,
        daysActive: Set<DayOfWeek> = allDays,
    ) = Reminder(
        categoryId = "cat1",
        enabled = true,
        mode = ReminderMode.FIXED,
        times = times,
        windowStart = null,
        windowEnd = null,
        occurrencesPerDay = null,
        daysActive = daysActive,
        showCategoryInNotification = false,
        nextFireAt = null,
    )

    private fun randomReminder(
        windowStart: LocalTime,
        windowEnd: LocalTime,
        occurrencesPerDay: Int,
        daysActive: Set<DayOfWeek> = allDays,
    ) = Reminder(
        categoryId = "cat1",
        enabled = true,
        mode = ReminderMode.RANDOM,
        times = emptyList(),
        windowStart = windowStart,
        windowEnd = windowEnd,
        occurrencesPerDay = occurrencesPerDay,
        daysActive = daysActive,
        showCategoryInNotification = false,
        nextFireAt = null,
    )

    // ---- FIXED mode (REM-SCHED-006) ----

    // @spec REM-SCHED-006
    @Test fun `FIXED mode returns earliest time later than after on the same day`() {
        val reminder = fixedReminder(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val after = instant(monday, LocalTime.of(9, 0))
        val result = computeNextFireTime(reminder, after, zone)
        assertEquals(instant(monday, LocalTime.of(20, 0)), result)
    }

    // @spec REM-SCHED-001, REM-SCHED-006
    @Test fun `FIXED mode does not select a time equal to after, only strictly later`() {
        val reminder = fixedReminder(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val after = instant(monday, LocalTime.of(8, 0))
        val result = computeNextFireTime(reminder, after, zone)
        assertEquals(instant(monday, LocalTime.of(20, 0)), result)
    }

    // @spec REM-SCHED-006
    @Test fun `FIXED mode advances to next active day when after is past every time today`() {
        val reminder = fixedReminder(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val after = instant(monday, LocalTime.of(21, 0))
        val result = computeNextFireTime(reminder, after, zone)
        assertEquals(instant(monday.plusDays(1), LocalTime.of(8, 0)), result)
    }

    // @spec REM-SCHED-006
    @Test fun `FIXED mode skips inactive days when advancing`() {
        val reminder = fixedReminder(
            times = listOf(LocalTime.of(8, 0)),
            daysActive = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )
        val after = instant(monday, LocalTime.of(9, 0))
        val result = computeNextFireTime(reminder, after, zone)
        assertEquals(instant(monday.plusDays(2), LocalTime.of(8, 0)), result)
    }

    // @spec REM-SCHED-006
    @Test fun `FIXED mode finds earliest time regardless of times list order`() {
        val reminder = fixedReminder(times = listOf(LocalTime.of(20, 0), LocalTime.of(8, 0), LocalTime.of(12, 0)))
        val after = instant(monday, LocalTime.of(0, 0))
        val result = computeNextFireTime(reminder, after, zone)
        assertEquals(instant(monday, LocalTime.of(8, 0)), result)
    }

    // ---- RANDOM mode (REM-SCHED-007) ----

    // @spec REM-SCHED-007
    @Test fun `RANDOM mode draws within the first sub-window when after precedes the window`() {
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 2)
        // sub-windows: [08:00,14:00), [14:00,20:00)
        val after = instant(monday, LocalTime.of(0, 0))
        val result = computeNextFireTime(reminder, after, zone, random = Random(42))
        assertTrue(!result.isBefore(instant(monday, LocalTime.of(8, 0))) && result.isBefore(instant(monday, LocalTime.of(14, 0))))
    }

    // @spec REM-SCHED-007
    @Test fun `RANDOM mode skips the sub-window after falls inside`() {
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 2)
        // sub-windows: [08:00,14:00), [14:00,20:00) — after sits inside the first
        val after = instant(monday, LocalTime.of(9, 0))
        val result = computeNextFireTime(reminder, after, zone, random = Random(42))
        assertTrue(!result.isBefore(instant(monday, LocalTime.of(14, 0))) && result.isBefore(instant(monday, LocalTime.of(20, 0))))
    }

    // @spec REM-SCHED-007
    @Test fun `RANDOM mode advances to next active day when after is past every sub-window today`() {
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 2)
        val after = instant(monday, LocalTime.of(21, 0))
        val result = computeNextFireTime(reminder, after, zone, random = Random(42))
        assertTrue(
            !result.isBefore(instant(monday.plusDays(1), LocalTime.of(8, 0))) &&
                result.isBefore(instant(monday.plusDays(1), LocalTime.of(14, 0))),
        )
    }

    // @spec REM-SCHED-007, REM-UI-010
    @Test fun `RANDOM mode treats windowEnd of midnight as end-of-day, not start-of-day`() {
        val reminder = randomReminder(LocalTime.of(0, 0), LocalTime.of(0, 0), occurrencesPerDay = 1)
        val after = instant(monday.minusDays(1), LocalTime.of(23, 0)) // Sunday night, before Monday's window opens
        val result = computeNextFireTime(reminder, after, zone, random = Random(42))
        assertTrue(!result.isBefore(instant(monday, LocalTime.of(0, 0))) && result.isBefore(instant(monday.plusDays(1), LocalTime.of(0, 0))))
    }

    // @spec REM-SCHED-007, REM-UI-010
    @Test fun `RANDOM mode with midnight windowEnd divides the full day into equal sub-windows`() {
        val reminder = randomReminder(LocalTime.of(0, 0), LocalTime.of(0, 0), occurrencesPerDay = 2)
        // effective window is the full day; sub-windows: [00:00,12:00), [12:00, end-of-day)
        val after = instant(monday, LocalTime.of(13, 0)) // inside the second sub-window
        val result = computeNextFireTime(reminder, after, zone, random = Random(42))
        assertTrue(
            !result.isBefore(instant(monday.plusDays(1), LocalTime.of(0, 0))) &&
                result.isBefore(instant(monday.plusDays(1), LocalTime.of(12, 0))),
        )
    }

    // @spec REM-SCHED-001, REM-SCHED-007
    @Test fun `RANDOM mode is deterministic for a given seed`() {
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 1)
        val after = instant(monday, LocalTime.of(0, 0))
        val r1 = computeNextFireTime(reminder, after, zone, random = Random(7))
        val r2 = computeNextFireTime(reminder, after, zone, random = Random(7))
        assertEquals(r1, r2)
    }

    // ---- Edit-time nextFireAt validity (REM-SCHED-018) ----

    // @spec REM-SCHED-018
    @Test fun `isNextFireAtValid is false for FIXED mode regardless of nextFireAt`() {
        val reminder = fixedReminder(times = listOf(LocalTime.of(8, 0)))
        val now = instant(monday, LocalTime.of(7, 0))
        val nextFireAt = instant(monday, LocalTime.of(8, 0))
        assertFalse(isNextFireAtValid(reminder, nextFireAt, now, zone))
    }

    // @spec REM-SCHED-018
    @Test fun `isNextFireAtValid is false when nextFireAt is null`() {
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 2)
        val now = instant(monday, LocalTime.of(9, 0))
        assertFalse(isNextFireAtValid(reminder, null, now, zone))
    }

    // @spec REM-SCHED-018
    @Test fun `isNextFireAtValid is true when nextFireAt is in the box containing now`() {
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 2)
        // sub-windows: [08:00,14:00), [14:00,20:00)
        val now = instant(monday, LocalTime.of(9, 0))
        val nextFireAt = instant(monday, LocalTime.of(10, 30))
        assertTrue(isNextFireAtValid(reminder, nextFireAt, now, zone))
    }

    // @spec REM-SCHED-018
    @Test fun `isNextFireAtValid is true when nextFireAt is in the box immediately after now's box`() {
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 2)
        val now = instant(monday, LocalTime.of(9, 0)) // box [08:00,14:00)
        val nextFireAt = instant(monday, LocalTime.of(16, 0)) // box [14:00,20:00)
        assertTrue(isNextFireAtValid(reminder, nextFireAt, now, zone))
    }

    // @spec REM-SCHED-018
    @Test fun `isNextFireAtValid is false when nextFireAt is two or more boxes ahead of now`() {
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 4)
        // sub-windows of 3h each: [08,11) [11,14) [14,17) [17,20)
        val now = instant(monday, LocalTime.of(9, 0)) // box 0
        val nextFireAt = instant(monday, LocalTime.of(15, 0)) // box 2
        assertFalse(isNextFireAtValid(reminder, nextFireAt, now, zone))
    }

    // @spec REM-SCHED-018
    @Test fun `isNextFireAtValid is false when nextFireAt is a box that has already fully elapsed`() {
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 2)
        val now = instant(monday, LocalTime.of(15, 0)) // box [14:00,20:00)
        val nextFireAt = instant(monday, LocalTime.of(10, 0)) // box [08:00,14:00), already elapsed
        assertFalse(isNextFireAtValid(reminder, nextFireAt, now, zone))
    }

    // @spec REM-SCHED-018
    @Test fun `isNextFireAtValid treats the not-yet-started first box of today as the current box`() {
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 2)
        val now = instant(monday, LocalTime.of(6, 0)) // before windowStart
        val nextFireAt = instant(monday, LocalTime.of(10, 0)) // today's first box
        assertTrue(isNextFireAtValid(reminder, nextFireAt, now, zone))
    }

    // @spec REM-SCHED-018
    @Test fun `isNextFireAtValid rolls the current box into the next active day when now is past today's window`() {
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 2)
        val now = instant(monday, LocalTime.of(21, 0)) // past today's window
        val nextFireAt = instant(monday.plusDays(1), LocalTime.of(10, 0)) // tomorrow's first box
        assertTrue(isNextFireAtValid(reminder, nextFireAt, now, zone))
    }

    // @spec REM-SCHED-018
    @Test fun `isNextFireAtValid rolls to the next active day when today is no longer an active day`() {
        val reminder = randomReminder(
            LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 2,
            daysActive = setOf(DayOfWeek.WEDNESDAY),
        )
        val now = instant(monday, LocalTime.of(9, 0)) // Monday isn't active
        val nextFireAt = instant(monday.plusDays(2), LocalTime.of(10, 0)) // Wednesday's first box
        assertTrue(isNextFireAtValid(reminder, nextFireAt, now, zone))
    }

    // @spec REM-SCHED-013, REM-SCHED-015, REM-SCHED-018
    @Test fun `isNextFireAtValid re-derives box boundaries from the reminder's current configuration`() {
        // nextFireAt was drawn under a 2-occurrence-per-day config's second box [14:00,20:00);
        // the reminder has since been edited to 4 occurrences per day, so that same clock
        // time now falls in box index 2 of [08,11)[11,14)[14,17)[17,20) — two boxes ahead.
        val editedReminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 4)
        val now = instant(monday, LocalTime.of(9, 0)) // box 0 under the new config
        val nextFireAt = instant(monday, LocalTime.of(16, 0)) // box 2 under the new config
        assertFalse(isNextFireAtValid(editedReminder, nextFireAt, now, zone))
    }
}
