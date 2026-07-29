package net.clahey.trackr.reminders

import net.clahey.trackr.data.AlarmScheduler
import java.time.Instant

class FakeAlarmScheduler(private var exactAvailable: Boolean = true) : AlarmScheduler {
    val armed = mutableMapOf<String, Instant>()
    val armCalls = mutableListOf<Pair<String, Instant>>()
    val cancelCalls = mutableListOf<String>()

    override fun canScheduleExact(): Boolean = exactAvailable

    override fun arm(categoryId: String, fireAt: Instant) {
        armed[categoryId] = fireAt
        armCalls += categoryId to fireAt
    }

    override fun cancel(categoryId: String) {
        armed.remove(categoryId)
        cancelCalls += categoryId
    }

    fun setExactAvailable(value: Boolean) {
        exactAvailable = value
    }
}
