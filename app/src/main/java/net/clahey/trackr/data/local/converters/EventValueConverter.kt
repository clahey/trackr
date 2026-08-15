package net.clahey.trackr.data.local.converters

import androidx.room.TypeConverter
import net.clahey.trackr.domain.ErrorKind
import net.clahey.trackr.domain.EventValue
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// @spec LS-BE-050
// @spec DM-PROC-001, DM-PROC-002, DM-PROC-003, DM-PROC-004, DM-PROC-005, DM-PROC-006, DM-PROC-007, DM-PROC-008, DM-PROC-008b, DM-PROC-009
object EventValueConverter {
    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    @TypeConverter
    fun encode(value: EventValue?): String? {
        if (value == null) return null
        if (value is EventValue.ErrorValue) return value.raw
        return json.encodeToString(EventValue.serializer(), value)
    }

    @TypeConverter
    fun decode(raw: String?): EventValue? {
        if (raw == null) return null
        val value = try {
            json.decodeFromString<EventValue>(raw)
        } catch (e: SerializationException) {
            return try {
                val element = Json.parseToJsonElement(raw)
                val inferredType = (element as? JsonObject)
                    ?.get("type")
                    ?.let { it as? JsonPrimitive }
                    ?.takeIf { it.isString }
                    ?.content
                EventValue.ErrorValue(ErrorKind.UNRECOGNIZED_TYPE, raw, inferredType = inferredType)
            } catch (e2: SerializationException) {
                EventValue.ErrorValue(ErrorKind.UNPARSABLE, raw)
            }
        }
        return when (value) {
            is EventValue.Scale ->
                if (value.value in 1..10) value
                else EventValue.ErrorValue(ErrorKind.OUT_OF_RANGE, raw)
            is EventValue.DurationValue ->
                if (value.duration >= kotlin.time.Duration.ZERO) value
                else EventValue.ErrorValue(ErrorKind.OUT_OF_RANGE, raw)
            is EventValue.ExerciseValue ->
                if (value.sets >= 1 && value.reps >= 1) value
                else EventValue.ErrorValue(ErrorKind.OUT_OF_RANGE, raw)
            else -> value
        }
    }
}
