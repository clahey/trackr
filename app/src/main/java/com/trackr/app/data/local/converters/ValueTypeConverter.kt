package com.trackr.app.data.local.converters

import androidx.room.TypeConverter
import com.trackr.app.domain.ValueType

object ValueTypeConverter {
    @TypeConverter
    fun encode(value: ValueType): String = TODO()

    @TypeConverter
    fun decode(raw: String): ValueType = TODO()
}
