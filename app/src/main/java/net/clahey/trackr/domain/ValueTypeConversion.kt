package net.clahey.trackr.domain

sealed class ConversionOutcome {
    data class Converted(val value: EventValue) : ConversionOutcome()
    data class UsedDefault(val value: EventValue) : ConversionOutcome()
    data object Discard : ConversionOutcome()
}

// @spec DM-PROC-013
fun matchesValueType(value: EventValue?, type: ValueType): kotlin.Boolean = when {
    value is EventValue.ErrorValue -> type is ValueType.Unknown && value.inferredType == type.raw
    type is ValueType.Unknown -> false
    value == null -> type is ValueType.None
    type is ValueType.None -> false
    else -> when (type) {
        ValueType.Scale -> value is EventValue.Scale
        ValueType.Boolean -> value is EventValue.BooleanValue
        ValueType.Number -> value is EventValue.NumberValue
        ValueType.Text -> value is EventValue.TextValue
        ValueType.Duration -> value is EventValue.DurationValue
        ValueType.Exercise -> value is EventValue.ExerciseValue
        else -> false
    }
}

// @spec DM-PROC-016
fun defaultForType(type: ValueType): EventValue? = when (type) {
    ValueType.None, is ValueType.Unknown -> null
    ValueType.Scale -> EventValue.Scale()
    ValueType.Boolean -> EventValue.BooleanValue()
    ValueType.Number -> EventValue.NumberValue()
    ValueType.Text -> EventValue.TextValue()
    ValueType.Duration -> EventValue.DurationValue()
    ValueType.Exercise -> EventValue.ExerciseValue()
}

// @spec DM-PROC-014, DM-PROC-015, DM-PROC-016
fun convertOrDefault(value: EventValue, targetType: ValueType): ConversionOutcome {
    if (targetType is ValueType.None || targetType is ValueType.Unknown) return ConversionOutcome.Discard
    val converted = convertEventValue(value, targetType)
    return if (converted != null && matchesValueType(converted, targetType)) {
        ConversionOutcome.Converted(converted)
    } else {
        val default = defaultForType(targetType) ?: return ConversionOutcome.Discard
        ConversionOutcome.UsedDefault(default)
    }
}

// @spec CAT-UI-032, CAT-UI-033, CAT-UI-034, CAT-UI-035, CAT-UI-039, CAT-UI-044, CAT-UI-045, CAT-UI-046
fun convertEventValue(value: EventValue?, to: ValueType): EventValue? {
    if (matchesValueType(value, to)) return value
    if (value == null) return defaultForType(to)
    return when {
        value is EventValue.Scale && to == ValueType.Number ->
            EventValue.NumberValue(value.value.toDouble(), null)
        value is EventValue.NumberValue && to == ValueType.Scale -> {
            val v = value.value
            if (value.unit.isNullOrBlank() && v % 1.0 == 0.0 && v in 1.0..10.0) EventValue.Scale(v.toInt()) else value
        }
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
