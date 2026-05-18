package com.trackr.app.data.local.converters

import androidx.room.TypeConverter
import com.trackr.app.domain.ErrorKind
import com.trackr.app.domain.EventValue
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object EventValueConverter {
    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    @TypeConverter
    fun encode(value: EventValue?): String? = TODO()

    @TypeConverter
    fun decode(raw: String?): EventValue? = TODO()
}
