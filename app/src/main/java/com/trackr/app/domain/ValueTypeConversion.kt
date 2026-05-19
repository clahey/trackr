package com.trackr.app.domain

// @spec CAT-UI-032, CAT-UI-033, CAT-UI-034, CAT-UI-035, CAT-UI-044, CAT-UI-045, CAT-UI-046
fun convertEventValue(value: EventValue?, from: ValueType, to: ValueType): EventValue? {
    if (from == to) return value
    if (from == ValueType.None && value == null) return when (to) {
        ValueType.Number -> EventValue.NumberValue()
        ValueType.Scale -> EventValue.Scale()
        ValueType.Boolean -> EventValue.BooleanValue()
        ValueType.Text -> EventValue.TextValue()
        ValueType.Duration -> EventValue.DurationValue()
        ValueType.Exercise -> EventValue.ExerciseValue()
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
        value is EventValue.ExerciseValue && to == ValueType.Text ->
            EventValue.TextValue("${value.sets} × ${value.reps}")
        value is EventValue.TextValue && to == ValueType.Exercise -> {
            val parts = value.text.split(Regex("\\s*[×x]\\s*"), limit = 2)
            val s = parts.getOrNull(0)?.trim()?.toIntOrNull()
            val r = parts.getOrNull(1)?.trim()?.toIntOrNull()
            if (s != null && r != null && s >= 1 && r >= 1) EventValue.ExerciseValue(s, r) else value
        }
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
