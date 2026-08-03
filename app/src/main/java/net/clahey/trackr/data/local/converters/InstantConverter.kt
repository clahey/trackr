package net.clahey.trackr.data.local.converters

import androidx.room.TypeConverter
import java.time.Instant

// @spec LS-BE-051
object InstantConverter {
    @TypeConverter
    fun encode(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun decode(value: Long): Instant = Instant.ofEpochMilli(value)
}
