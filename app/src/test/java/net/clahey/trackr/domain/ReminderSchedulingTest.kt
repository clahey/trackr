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
        windowStart = LocalTime.MIDNIGHT,
        windowEnd = LocalTime.MIDNIGHT,
        occurrencesPerDay = 1,
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
    @Test fun `RANDOM mode does not select a sub-window starting exactly at after, only strictly later`() {
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 2)
        // sub-windows: [08:00,14:00), [14:00,20:00) — after lands exactly on the second box's own
        // start, so neither box qualifies (08:00 isn't after 14:00; 14:00 isn't strictly after 14:00)
        val after = instant(monday, LocalTime.of(14, 0))
        val result = computeNextFireTime(reminder, after, zone, random = Random(42))
        assertTrue(
            !result.isBefore(instant(monday.plusDays(1), LocalTime.of(8, 0))) &&
                result.isBefore(instant(monday.plusDays(1), LocalTime.of(14, 0))),
        )
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

    // @spec REM-SCHED-007
    @Test fun `RANDOM mode places an instant well within the last box when occurrencesPerDay does not divide evenly`() {
        // 12h window / 7 boxes doesn't divide evenly; the last box may end up to occurrencesPerDay - 1
        // nanoseconds short of windowEnd (negligible at real clock precision), but well within it
        // should still land in the last box, not fall through to "no box today."
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 7)
        val now = instant(monday, LocalTime.of(19, 0)) // inside the last box
        val nextFireAt = instant(monday, LocalTime.of(19, 30))
        assertTrue(isNextFireAtValid(reminder, nextFireAt, now, zone))
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
    @Test fun `isNextFireAtValid places an instant exactly on an uneven interior boundary in the box that starts there`() {
        // 481s window / 3 doesn't divide evenly: boundary(1) = floor(481e9/3)ns = 08:02:40.333333333.
        // now sits exactly on that boundary, so the current box is box1 [08:02:40.333333333, 08:05:20.666666666),
        // not box0 [08:00:00, 08:02:40.333333333) — box0 has already fully elapsed as of now.
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(8, 8, 1), occurrencesPerDay = 3)
        val now = instant(monday, LocalTime.of(8, 2, 40, 333_333_333))
        val nextFireAtInBox1 = instant(monday, LocalTime.of(8, 3, 0))
        val nextFireAtInBox0 = instant(monday, LocalTime.of(8, 1, 0))
        assertTrue(isNextFireAtValid(reminder, nextFireAtInBox1, now, zone))
        assertFalse(isNextFireAtValid(reminder, nextFireAtInBox0, now, zone))
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

    // ---- Already-logged suppression (REM-SCHED-020) ----

    // @spec REM-SCHED-020
    @Test fun `suppresses when the latest event falls within the lookback window before firing`() {
        // times = [08:00, 20:00]; previous trigger before 20:00 is 08:00 -> 12h gap -> 10% (72min) exceeds
        // the 1h cap, so the effective window is 60min
        val reminder = fixedReminder(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val scheduledAt = instant(monday, LocalTime.of(20, 0))
        val latestEventLoggedAt = instant(monday, LocalTime.of(19, 55))
        assertTrue(shouldSuppressFixedNotification(reminder, scheduledAt, scheduledAt, zone, latestEventLoggedAt))
    }

    // @spec REM-SCHED-020
    @Test fun `does not suppress when the latest event is well outside the lookback window`() {
        val reminder = fixedReminder(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val scheduledAt = instant(monday, LocalTime.of(20, 0))
        val latestEventLoggedAt = instant(monday, LocalTime.of(8, 5)) // ~5min after the AM slot, far before PM's window
        assertFalse(shouldSuppressFixedNotification(reminder, scheduledAt, scheduledAt, zone, latestEventLoggedAt))
    }

    // @spec REM-SCHED-020
    @Test fun `does not suppress when no event has been logged`() {
        val reminder = fixedReminder(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val scheduledAt = instant(monday, LocalTime.of(20, 0))
        assertFalse(shouldSuppressFixedNotification(reminder, scheduledAt, scheduledAt, zone, latestEventLoggedAt = null))
    }

    // @spec REM-SCHED-020
    @Test fun `never suppresses RANDOM mode regardless of a recent log`() {
        val reminder = randomReminder(LocalTime.of(8, 0), LocalTime.of(20, 0), occurrencesPerDay = 2)
        val scheduledAt = instant(monday, LocalTime.of(20, 0))
        val latestEventLoggedAt = instant(monday, LocalTime.of(19, 59))
        assertFalse(shouldSuppressFixedNotification(reminder, scheduledAt, scheduledAt, zone, latestEventLoggedAt))
    }

    // @spec REM-SCHED-020
    @Test fun `caps the lookback window at one hour for a widely-spaced schedule`() {
        // single daily time -> previous trigger is 24h earlier -> uncapped 10% would be 144min
        val reminder = fixedReminder(times = listOf(LocalTime.of(20, 0)))
        val scheduledAt = instant(monday, LocalTime.of(20, 0))
        val latestEventLoggedAt = instant(monday, LocalTime.of(18, 30)) // 90min before, inside 144min but outside the 60min cap
        assertFalse(shouldSuppressFixedNotification(reminder, scheduledAt, scheduledAt, zone, latestEventLoggedAt))
    }

    // @spec REM-SCHED-020
    @Test fun `suppresses within the capped one-hour window for a widely-spaced schedule`() {
        val reminder = fixedReminder(times = listOf(LocalTime.of(20, 0)))
        val scheduledAt = instant(monday, LocalTime.of(20, 0))
        val latestEventLoggedAt = instant(monday, LocalTime.of(19, 30)) // 30min before, inside the 60min cap
        assertTrue(shouldSuppressFixedNotification(reminder, scheduledAt, scheduledAt, zone, latestEventLoggedAt))
    }

    // @spec REM-SCHED-020
    @Test fun `previous scheduled fire time wraps to the prior active day when today has no earlier time`() {
        // active only Mon/Wed; firing Wednesday 08:00 -> previous trigger is Monday 08:00 (48h gap, capped at 1h)
        val reminder = fixedReminder(
            times = listOf(LocalTime.of(8, 0)),
            daysActive = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )
        val scheduledAt = instant(monday.plusDays(2), LocalTime.of(8, 0)) // Wednesday
        val withinCap = instant(monday.plusDays(2), LocalTime.of(7, 30))
        val outsideCap = instant(monday.plusDays(2), LocalTime.of(6, 0))
        assertTrue(shouldSuppressFixedNotification(reminder, scheduledAt, scheduledAt, zone, withinCap))
        assertFalse(shouldSuppressFixedNotification(reminder, scheduledAt, scheduledAt, zone, outsideCap))
    }

    // @spec REM-SCHED-020
    @Test fun `treats a log exactly at the window boundary as suppressing`() {
        // 1h gap between times -> uncapped 10% window of 6min, so the cap doesn't interfere
        val reminder = fixedReminder(times = listOf(LocalTime.of(8, 0), LocalTime.of(9, 0)))
        val scheduledAt = instant(monday, LocalTime.of(9, 0))
        val latestEventLoggedAt = instant(monday, LocalTime.of(8, 54)) // exactly 6min before
        assertTrue(shouldSuppressFixedNotification(reminder, scheduledAt, scheduledAt, zone, latestEventLoggedAt))
    }

    // @spec REM-SCHED-020
    @Test fun `sizes the lookback from the scheduled time even when delivery runs late`() {
        // The regression the on-time tests above structurally cannot catch: every one of them passes a
        // firedAt equal to a scheduled time, the single value at which a pivot on firedAt still skips the
        // current occurrence. Real delivery is always at least slightly late, and pivoting the backward
        // walk there returns 20:00 itself -> a lookback of the jitter rather than the capped hour.
        val reminder = fixedReminder(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val scheduledAt = instant(monday, LocalTime.of(20, 0))
        val firedAt = scheduledAt.plusMillis(400)
        val latestEventLoggedAt = instant(monday, LocalTime.of(19, 55))
        assertTrue(shouldSuppressFixedNotification(reminder, scheduledAt, firedAt, zone, latestEventLoggedAt))
    }

    // @spec REM-SCHED-020
    @Test fun `suppresses a log made while a delayed delivery was still pending`() {
        // Doze can hold an inexact alarm for hours; a log during that stretch should still suppress,
        // which is why the window ends at firedAt rather than at scheduledAt.
        val reminder = fixedReminder(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val scheduledAt = instant(monday, LocalTime.of(20, 0))
        val firedAt = instant(monday, LocalTime.of(22, 30))
        val latestEventLoggedAt = instant(monday, LocalTime.of(21, 0))
        assertTrue(shouldSuppressFixedNotification(reminder, scheduledAt, firedAt, zone, latestEventLoggedAt))
    }

    // @spec REM-SCHED-020
    @Test fun `does not suppress a delayed delivery when the log predates the window`() {
        val reminder = fixedReminder(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val scheduledAt = instant(monday, LocalTime.of(20, 0))
        val firedAt = instant(monday, LocalTime.of(22, 30))
        val latestEventLoggedAt = instant(monday, LocalTime.of(18, 30)) // before 19:00, the window's start
        assertFalse(shouldSuppressFixedNotification(reminder, scheduledAt, firedAt, zone, latestEventLoggedAt))
    }

    // @spec REM-SCHED-020
    @Test fun `does not suppress on an event timestamped after delivery`() {
        // Event timestamps are user-editable, so a future-dated entry must not suppress a reminder it
        // postdates — the window is closed at firedAt, not left open-ended.
        val reminder = fixedReminder(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val scheduledAt = instant(monday, LocalTime.of(20, 0))
        val latestEventLoggedAt = instant(monday.plusDays(1), LocalTime.of(9, 0))
        assertFalse(shouldSuppressFixedNotification(reminder, scheduledAt, scheduledAt, zone, latestEventLoggedAt))
    }
}
