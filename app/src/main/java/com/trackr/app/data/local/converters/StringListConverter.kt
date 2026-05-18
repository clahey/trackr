package com.trackr.app.data.local.converters

import androidx.room.TypeConverter
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// @spec LS-BE-053
object StringListConverter {
    private val serializer = ListSerializer(String.serializer())

    @TypeConverter
    fun encode(value: List<String>): String = Json.encodeToString(serializer, value)

    @TypeConverter
    fun decode(value: String): List<String> = try {
        Json.decodeFromString(serializer, value)
    } catch (e: SerializationException) {
        emptyList()
    }
}
