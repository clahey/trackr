package net.clahey.trackr.data

import java.time.Instant

// @spec REM-SCHED-009, REM-SCHED-010, REM-SCHED-012, REM-SCHED-021
interface AlarmScheduler {
    fun canScheduleExact(): Boolean
    fun arm(categoryId: String, fireAt: Instant)
    fun cancel(categoryId: String)
}
