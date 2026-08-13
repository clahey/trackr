package net.clahey.trackr.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

enum class ReminderMode { FIXED, RANDOM }

// @spec REM-DATA-001, REM-DATA-002, REM-DATA-003, REM-DATA-004, REM-DATA-005
data class Reminder(
    val categoryId: String,
    val enabled: Boolean,
    val mode: ReminderMode,
    val times: List<LocalTime>,
    val windowStart: LocalTime, // RANDOM only; preserved but unused while mode == FIXED (REM-DATA-002)
    val windowEnd: LocalTime, // RANDOM only; preserved but unused while mode == FIXED (REM-DATA-002)
    val occurrencesPerDay: Int, // RANDOM only; preserved but unused while mode == FIXED (REM-DATA-002)
    val daysActive: Set<DayOfWeek>,
    val showCategoryInNotification: Boolean,
    val nextFireAt: Instant?,
)
