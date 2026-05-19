package com.trackr.app.domain

import kotlin.time.Duration

// @spec CAT-UI-032, CAT-UI-033, CAT-UI-034, CAT-UI-035
fun convertEventValue(value: EventValue?, from: ValueType, to: ValueType): EventValue? {
    if (from == to) return value
    if (from == ValueType.None && value == null) return when (to) {
        ValueType.Number -> EventValue.NumberValue(0.0, null)
        ValueType.Scale -> EventValue.Scale(5)
        ValueType.Boolean -> EventValue.BooleanValue(true)
        ValueType.Text -> EventValue.TextValue("")
        ValueType.Duration -> EventValue.DurationValue(Duration.ZERO)
        else -> null
    }
    if (value == null) return null
    return when {
        value is EventValue.Scale && to == ValueType.Number ->
            EventValue.NumberValue(value.value.toDouble(), null)
        value is EventValue.Scale && to == ValueType.Text ->
            EventValue.TextValue(value.value.toString())
        value is EventValue.BooleanValue && to == ValueType.Text ->
            EventValue.TextValue(if (value.value) "Yes" else "No")
        value is EventValue.NumberValue && to == ValueType.Text ->
            EventValue.TextValue(buildString {
                append(value.value)
                value.unit?.let { append(" $it") }
            })
        value is EventValue.DurationValue && to == ValueType.Text ->
            EventValue.TextValue(value.duration.toString())
        value is EventValue.TextValue && to == ValueType.Boolean -> when (value.text) {
            "Yes" -> EventValue.BooleanValue(true)
            "No" -> EventValue.BooleanValue(false)
            else -> value
        }
        value is EventValue.TextValue && to == ValueType.Number -> {
            val parts = value.text.split(" ", limit = 2)
            val d = parts[0].toDoubleOrNull()
            if (d != null) EventValue.NumberValue(d, parts.getOrNull(1)?.takeIf { it.isNotBlank() })
            else value
        }
        value is EventValue.TextValue && to == ValueType.Scale -> {
            val n = value.text.toIntOrNull()
            if (n != null && n in 1..10) EventValue.Scale(n) else value
        }
        value is EventValue.TextValue && to == ValueType.None ->
            if (value.text.isEmpty()) null else value
        else -> value
    }
}
