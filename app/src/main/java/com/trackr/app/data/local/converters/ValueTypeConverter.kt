package com.trackr.app.data.local.converters

import androidx.room.TypeConverter
import com.trackr.app.domain.ValueType

// @spec DM-DATA-002, DM-DATA-003, DM-DATA-004
object ValueTypeConverter {
    @TypeConverter
    fun encode(value: ValueType): String = when (value) {
        ValueType.None -> "none"
        ValueType.Scale -> "scale"
        ValueType.Boolean -> "boolean"
        ValueType.Number -> "number"
        ValueType.Text -> "text"
        ValueType.Duration -> "duration"
        ValueType.Exercise -> "exercise"
        is ValueType.Unknown -> value.raw
    }

    @TypeConverter
    fun decode(raw: String): ValueType = when (raw) {
        "none" -> ValueType.None
        "scale" -> ValueType.Scale
        "boolean" -> ValueType.Boolean
        "number" -> ValueType.Number
        "text" -> ValueType.Text
        "duration" -> ValueType.Duration
        "exercise" -> ValueType.Exercise
        else -> ValueType.Unknown(raw)
    }
}
