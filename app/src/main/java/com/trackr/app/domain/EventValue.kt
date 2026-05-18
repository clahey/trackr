package com.trackr.app.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
enum class ErrorKind { UNPARSABLE, UNRECOGNIZED_TYPE, OUT_OF_RANGE }

@Serializable
sealed class EventValue {
    @Serializable
    @SerialName("Scale")
    data class Scale(val value: Int) : EventValue()

    @Serializable
    @SerialName("BooleanValue")
    data class BooleanValue(val value: kotlin.Boolean) : EventValue()

    @Serializable
    @SerialName("NumberValue")
    data class NumberValue(val value: Double, val unit: String?) : EventValue()

    @Serializable
    @SerialName("TextValue")
    data class TextValue(val text: String) : EventValue()

    @Serializable
    @SerialName("DurationValue")
    data class DurationValue(
        @Serializable(with = DurationAsSecondsSerializer::class)
        val duration: Duration,
    ) : EventValue()

    @Serializable
    @SerialName("ErrorValue")
    data class ErrorValue(val kind: ErrorKind, val raw: String) : EventValue()
}

object DurationAsSecondsSerializer : KSerializer<Duration> {
    override val descriptor = PrimitiveSerialDescriptor("Duration", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeLong(value.inWholeSeconds)
    override fun deserialize(decoder: Decoder): Duration = decoder.decodeLong().seconds
}
