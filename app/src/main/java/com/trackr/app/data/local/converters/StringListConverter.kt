package com.trackr.app.data.local.converters

import androidx.room.TypeConverter

object StringListConverter {
    @TypeConverter
    fun encode(value: List<String>): String = TODO()

    @TypeConverter
    fun decode(value: String): List<String> = TODO()
}
