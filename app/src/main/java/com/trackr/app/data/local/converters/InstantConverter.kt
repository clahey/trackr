package com.trackr.app.data.local.converters

import androidx.room.TypeConverter
import java.time.Instant

object InstantConverter {
    @TypeConverter
    fun encode(value: Instant): Long = TODO()

    @TypeConverter
    fun decode(value: Long): Instant = TODO()
}
