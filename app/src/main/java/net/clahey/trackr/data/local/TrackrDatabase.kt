package net.clahey.trackr.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.clahey.trackr.data.local.converters.EventValueConverter
import net.clahey.trackr.data.local.converters.InstantConverter
import net.clahey.trackr.data.local.converters.StringListConverter
import net.clahey.trackr.data.local.converters.ValueTypeConverter

@Database(entities = [CategoryEntity::class, EventEntity::class, ReminderEntity::class], version = 4, exportSchema = true)
@TypeConverters(EventValueConverter::class, InstantConverter::class, StringListConverter::class, ValueTypeConverter::class)
abstract class TrackrDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun eventDao(): EventDao
    abstract fun reminderDao(): ReminderDao
}
