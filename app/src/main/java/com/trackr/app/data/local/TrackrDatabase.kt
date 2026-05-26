package com.trackr.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.trackr.app.data.local.converters.EventValueConverter
import com.trackr.app.data.local.converters.InstantConverter
import com.trackr.app.data.local.converters.StringListConverter
import com.trackr.app.data.local.converters.ValueTypeConverter

@Database(entities = [CategoryEntity::class, EventEntity::class], version = 2, exportSchema = true)
@TypeConverters(EventValueConverter::class, InstantConverter::class, StringListConverter::class, ValueTypeConverter::class)
abstract class TrackrDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun eventDao(): EventDao
}
