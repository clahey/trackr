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

private fun computeNextFixedFireTime(reminder: Reminder, after: Instant, zone: ZoneId): Instant =
    computeFixedFireTime(reminder, after, zone, forward = true)

// Used only by shouldSuppressFixedNotification, to size its lookback window relative to this
// reminder's own schedule (REM-SCHED-020).
private fun computePreviousFixedFireTime(reminder: Reminder, before: Instant, zone: ZoneId): Instant =
    computeFixedFireTime(reminder, before, zone, forward = false)

private fun computeFixedFireTime(reminder: Reminder, pivot: Instant, zone: ZoneId, forward: Boolean): Instant {
    val sortedTimes = reminder.times.sorted().let { if (forward) it else it.reversed() }
    val today = pivot.atZone(zone).toLocalDate()
    if (reminder.daysActive.contains(today.dayOfWeek)) {
        for (time in sortedTimes) {
            val candidate = today.atTime(time).atZone(zone).toInstant()
            val qualifies = if (forward) candidate.isAfter(pivot) else candidate.isBefore(pivot)
            if (qualifies) return candidate
        }
    }
    val fallbackDate = if (forward) {
        nextActiveDay(reminder, today.plusDays(1))
    } else {
        previousActiveDay(reminder, today.minusDays(1))
    }
    return fallbackDate.atTime(sortedTimes.first()).atZone(zone).toInstant()
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
        val index = indexOfFirstStartAfter(reminder, today, zone, after)
        if (index < reminder.occurrencesPerDay) return drawWithin(subWindowAt(reminder, today, zone, index), random)
    }
    val nextDate = nextActiveDay(reminder, today.plusDays(1))
    return drawWithin(subWindowAt(reminder, nextDate, zone, 0), random)
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

// A day's RANDOM-mode window start and per-box step (subNanos), the two date/zone-dependent values
// the box functions below need; occurrencesPerDay is already on Reminder, no need to carry it here.
// subNanos truncates totalNanos / occurrencesPerDay, so the last box can end up to
// occurrencesPerDay - 1 nanoseconds short of the literal window end — irrelevant at any real
// clock's millisecond-scale precision. A fixed per-box step keeps "which box contains an instant" a
// single division, the exact inverse of the boundary formula below.
private fun windowStep(reminder: Reminder, date: LocalDate, zone: ZoneId): Pair<Instant, Long> {
    val windowStart = reminder.windowStart
    val windowEnd = reminder.windowEnd
    val startInstant = date.atTime(windowStart).atZone(zone).toInstant()
    // A windowEnd of midnight is the end-of-day sentinel (REM-UI-010), never literal start-of-day.
    val endInstant = if (windowEnd == LocalTime.MIDNIGHT) {
        date.plusDays(1).atStartOfDay(zone).toInstant()
    } else {
        date.atTime(windowEnd).atZone(zone).toInstant()
    }
    val totalNanos = Duration.between(startInstant, endInstant).toNanos()
    return startInstant to totalNanos / reminder.occurrencesPerDay
}

private fun subWindowAt(reminder: Reminder, date: LocalDate, zone: ZoneId, index: Int): SubWindow {
    val (startInstant, subNanos) = windowStep(reminder, date, zone)
    return SubWindow(startInstant.plusNanos(subNanos * index), startInstant.plusNanos(subNanos * (index + 1)))
}

// The box containing `instant`, or occurrencesPerDay (a sentinel — there is no such box) once
// `instant` reaches the last box's end. `instant` before this window's start is box 0 (the
// not-yet-started first box counts as current — REM-SCHED-018).
private fun indexContaining(reminder: Reminder, date: LocalDate, zone: ZoneId, instant: Instant): Int =
    boxIndex(reminder, date, zone, instant, offset = 0)

// The smallest index whose box starts strictly after `instant` — always one more than
// indexContaining, since a box's own start never counts as "after" itself — or occurrencesPerDay
// (a sentinel — no box today qualifies).
private fun indexOfFirstStartAfter(reminder: Reminder, date: LocalDate, zone: ZoneId, instant: Instant): Int =
    boxIndex(reminder, date, zone, instant, offset = 1)

private fun boxIndex(reminder: Reminder, date: LocalDate, zone: ZoneId, instant: Instant, offset: Int): Int {
    val (startInstant, subNanos) = windowStep(reminder, date, zone)
    val offsetNanos = Duration.between(startInstant, instant).toNanos()
    if (offsetNanos < 0) return 0
    val occurrencesPerDay = reminder.occurrencesPerDay
    val index = offsetNanos / subNanos + offset
    return if (index >= occurrencesPerDay) occurrencesPerDay else index.toInt()
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
    val currentBox = subWindowAt(reminder, date, zone, index)
    val nextBox = if (index + 1 < reminder.occurrencesPerDay) {
        subWindowAt(reminder, date, zone, index + 1)
    } else {
        subWindowAt(reminder, nextActiveDay(reminder, date.plusDays(1)), zone, 0)
    }
    return currentBox to nextBox
}

private fun currentBoxLocation(reminder: Reminder, now: Instant, zone: ZoneId): Pair<LocalDate, Int> {
    val today = now.atZone(zone).toLocalDate()
    if (reminder.daysActive.contains(today.dayOfWeek)) {
        val index = indexContaining(reminder, today, zone, now)
        if (index < reminder.occurrencesPerDay) return today to index
    }
    return nextActiveDay(reminder, today.plusDays(1)) to 0
}
