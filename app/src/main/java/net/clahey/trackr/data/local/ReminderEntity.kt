package net.clahey.trackr.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

// @spec REM-DATA-001, REM-DATA-002
@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ReminderEntity(
    @PrimaryKey val categoryId: String,
    val enabled: Boolean,
    val mode: String, // "fixed" / "random"
    val times: String?, // JSON list of "HH:mm" strings; FIXED only
    val windowStart: String?, // "HH:mm"; RANDOM only
    val windowEnd: String?, // "HH:mm"; RANDOM only
    val occurrencesPerDay: Int?, // RANDOM only
    val daysActive: String, // JSON list of DayOfWeek names
    val showCategoryInNotification: Boolean,
    val nextFireAt: Long?, // epoch millis; null when disabled or not yet armed
)
