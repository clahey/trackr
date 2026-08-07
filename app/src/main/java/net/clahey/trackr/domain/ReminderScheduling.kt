package net.clahey.trackr.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

// @spec REM-SCHED-001, REM-SCHED-002, REM-SCHED-006, REM-SCHED-007
fun computeNextFireTime(
    reminder: Reminder,
    after: Instant,
    zone: ZoneId,
    random: Random = Random.Default,
): Instant = when (reminder.mode) {
    ReminderMode.FIXED -> computeNextFixedFireTime(reminder, after, zone)
    ReminderMode.RANDOM -> computeNextRandomFireTime(reminder, after, zone, random)
}

private fun computeNextFixedFireTime(reminder: Reminder, after: Instant, zone: ZoneId): Instant {
    val sortedTimes = reminder.times.sorted()
    val today = after.atZone(zone).toLocalDate()
    if (reminder.daysActive.contains(today.dayOfWeek)) {
        for (time in sortedTimes) {
            val candidate = today.atTime(time).atZone(zone).toInstant()
            if (candidate.isAfter(after)) return candidate
        }
    }
    val nextDate = nextActiveDay(reminder, today.plusDays(1))
    return nextDate.atTime(sortedTimes.first()).atZone(zone).toInstant()
}

// Mirror image of computeNextFixedFireTime, walking times/daysActive backward — used only by
// shouldSuppressFixedNotification to size its lookback window relative to this reminder's own
// schedule (REM-SCHED-020).
private fun computePreviousFixedFireTime(reminder: Reminder, before: Instant, zone: ZoneId): Instant {
    val sortedTimes = reminder.times.sorted()
    val today = before.atZone(zone).toLocalDate()
    if (reminder.daysActive.contains(today.dayOfWeek)) {
        for (time in sortedTimes.reversed()) {
            val candidate = today.atTime(time).atZone(zone).toInstant()
            if (candidate.isBefore(before)) return candidate
        }
    }
    val prevDate = previousActiveDay(reminder, today.minusDays(1))
    return prevDate.atTime(sortedTimes.last()).atZone(zone).toInstant()
}

private val MAX_SUPPRESSION_LOOKBACK: Duration = Duration.ofHours(1)

// @spec REM-SCHED-020
fun shouldSuppressFixedNotification(
    reminder: Reminder,
    firedAt: Instant,
    zone: ZoneId,
    latestEventLoggedAt: Instant?,
): Boolean {
    if (reminder.mode != ReminderMode.FIXED || latestEventLoggedAt == null) return false
    val previousTrigger = computePreviousFixedFireTime(reminder, firedAt, zone)
    val tenPercent = Duration.between(previousTrigger, firedAt).dividedBy(10)
    val lookback = minOf(tenPercent, MAX_SUPPRESSION_LOOKBACK)
    return !latestEventLoggedAt.isBefore(firedAt.minus(lookback))
}

private fun computeNextRandomFireTime(reminder: Reminder, after: Instant, zone: ZoneId, random: Random): Instant {
    val today = after.atZone(zone).toLocalDate()
    if (reminder.daysActive.contains(today.dayOfWeek)) {
        val box = subWindowsFor(reminder, today, zone).firstOrNull { it.start.isAfter(after) }
        if (box != null) return drawWithin(box, random)
    }
    val nextDate = nextActiveDay(reminder, today.plusDays(1))
    return drawWithin(subWindowsFor(reminder, nextDate, zone).first(), random)
}

// @spec REM-SCHED-018
fun isNextFireAtValid(
    reminder: Reminder,
    nextFireAt: Instant?,
    now: Instant,
    zone: ZoneId,
): Boolean {
    if (nextFireAt == null || reminder.mode != ReminderMode.RANDOM) return false
    val (currentBox, nextBox) = currentAndNextBox(reminder, now, zone)
    return currentBox.contains(nextFireAt) || nextBox.contains(nextFireAt)
}

private data class SubWindow(val start: Instant, val end: Instant) {
    fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)
}

private fun subWindowsFor(reminder: Reminder, date: LocalDate, zone: ZoneId): List<SubWindow> {
    val windowStart = reminder.windowStart!!
    val windowEnd = reminder.windowEnd!!
    val occurrencesPerDay = reminder.occurrencesPerDay!!
    val startInstant = date.atTime(windowStart).atZone(zone).toInstant()
    // A windowEnd of midnight is the end-of-day sentinel (REM-UI-010), never literal start-of-day.
    val endInstant = if (windowEnd == LocalTime.MIDNIGHT) {
        date.plusDays(1).atStartOfDay(zone).toInstant()
    } else {
        date.atTime(windowEnd).atZone(zone).toInstant()
    }
    val subNanos = Duration.between(startInstant, endInstant).toNanos() / occurrencesPerDay
    return (0 until occurrencesPerDay).map { i ->
        val subStart = startInstant.plusNanos(subNanos * i)
        val subEnd = if (i == occurrencesPerDay - 1) endInstant else startInstant.plusNanos(subNanos * (i + 1))
        SubWindow(subStart, subEnd)
    }
}

private fun drawWithin(box: SubWindow, random: Random): Instant {
    val rangeNanos = Duration.between(box.start, box.end).toNanos()
    if (rangeNanos <= 0) return box.start
    return box.start.plusNanos(random.nextLong(rangeNanos))
}

private fun nextActiveDay(reminder: Reminder, from: LocalDate): LocalDate {
    var date = from
    while (!reminder.daysActive.contains(date.dayOfWeek)) date = date.plusDays(1)
    return date
}

private fun previousActiveDay(reminder: Reminder, from: LocalDate): LocalDate {
    var date = from
    while (!reminder.daysActive.contains(date.dayOfWeek)) date = date.minusDays(1)
    return date
}

// The earliest sub-window (today, or the next active day) that has not yet fully elapsed as of `now`,
// paired with the sub-window immediately following it — the pair a normal post-fire reschedule and an
// edit-time validity check (REM-SCHED-018) both reason about.
private fun currentAndNextBox(reminder: Reminder, now: Instant, zone: ZoneId): Pair<SubWindow, SubWindow> {
    val (date, index) = currentBoxLocation(reminder, now, zone)
    val boxesForDate = subWindowsFor(reminder, date, zone)
    val currentBox = boxesForDate[index]
    val nextBox = if (index + 1 < boxesForDate.size) {
        boxesForDate[index + 1]
    } else {
        subWindowsFor(reminder, nextActiveDay(reminder, date.plusDays(1)), zone).first()
    }
    return currentBox to nextBox
}

private fun currentBoxLocation(reminder: Reminder, now: Instant, zone: ZoneId): Pair<LocalDate, Int> {
    val today = now.atZone(zone).toLocalDate()
    if (reminder.daysActive.contains(today.dayOfWeek)) {
        val index = subWindowsFor(reminder, today, zone).indexOfFirst { now.isBefore(it.end) }
        if (index >= 0) return today to index
    }
    return nextActiveDay(reminder, today.plusDays(1)) to 0
}
