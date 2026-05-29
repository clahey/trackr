package com.trackr.app.ui.components

import com.trackr.app.domain.Category
import com.trackr.app.domain.ConversionOutcome
import com.trackr.app.domain.EventValue
import com.trackr.app.domain.ValueType
import com.trackr.app.domain.convertOrDefault
import com.trackr.app.domain.defaultForType
import com.trackr.app.domain.matchesValueType
import kotlin.time.Duration.Companion.seconds

// @spec EL-UI-050, EL-UI-051, EL-UI-051b, EL-UI-052, EL-UI-052c, EL-UI-053, EL-UI-055,
// EL-UI-055c, EL-UI-055d, EL-UI-056, EL-UI-059, EL-UI-059b, EL-UI-062, EL-UI-068
sealed class ValueUIState {
    data object None : ValueUIState()
    data class Number(val text: String, val unit: String) : ValueUIState()
    data class Text(val text: String) : ValueUIState()
    data class Scale(val value: Int) : ValueUIState()
    data class Bool(val selected: Boolean?) : ValueUIState()
    data class Duration(
        val hoursText: String,
        val minutesText: String,
        val secondsText: String,
    ) : ValueUIState()
    data class Exercise(val setsText: String, val repsText: String) : ValueUIState()
    data class ReadOnly(val displayText: String, val originalValue: EventValue) : ValueUIState()
    data class Mismatched(
        val originalValue: EventValue,
        val targetType: ValueType,
        val editableState: ValueUIState?,
    ) : ValueUIState() {
        // @spec EL-UI-063, EL-UI-064
        val outcome: ConversionOutcome get() {
            val ev = editableState?.toEventValue()
            return when {
                ev != null -> convertOrDefault(ev, targetType)
                editableState != null ->
                    defaultForType(targetType)?.let { ConversionOutcome.UsedDefault(it) }
                        ?: ConversionOutcome.Discard
                else -> convertOrDefault(originalValue, targetType)
            }
        }
    }
}

// @spec EL-UI-050, EL-UI-051, EL-UI-052, EL-UI-053, EL-UI-055, EL-UI-055d, EL-UI-056, EL-UI-059
fun EventValue.toValueUIState(): ValueUIState = when (this) {
    is EventValue.Scale -> ValueUIState.Scale(value)
    is EventValue.BooleanValue -> ValueUIState.Bool(value)
    is EventValue.NumberValue -> ValueUIState.Number(
        text = value.toString(),
        unit = unit ?: "",
    )
    is EventValue.TextValue -> ValueUIState.Text(text)
    is EventValue.DurationValue -> duration.toComponents { hours, minutes, seconds, _ ->
        durationToUIState(hours, minutes, seconds)
    }
    is EventValue.ExerciseValue -> ValueUIState.Exercise(
        setsText = sets.toString(),
        repsText = reps.toString(),
    )
    is EventValue.ErrorValue -> ValueUIState.ReadOnly(
        displayText = formatValue(this),
        originalValue = this,
    )
}

// @spec EL-UI-055d
fun durationToUIState(hours: Long, minutes: Int, seconds: Int): ValueUIState.Duration {
    val hoursText = if (hours > 0) hours.toString() else ""
    val minutesText = if (hours > 0 || minutes > 0) minutes.toString() else ""
    val secondsText = seconds.toString()
    return ValueUIState.Duration(hoursText, minutesText, secondsText)
}

// @spec EL-UI-062, EL-UI-067
fun EventValue?.toValueUIState(valueType: ValueType): ValueUIState {
    if (this == null) return defaultValueUIStateForType(valueType)
    if (matchesValueType(this, valueType)) return toValueUIState()
    return ValueUIState.Mismatched(
        originalValue = this,
        targetType = valueType,
        editableState = editableStateFor(this, valueType),
    )
}

fun editableStateFor(value: EventValue, valueType: ValueType): ValueUIState? {
    val outcome = convertOrDefault(value, valueType)
    return if (value is EventValue.ErrorValue || outcome is ConversionOutcome.Discard) null
    else value.toValueUIState()
}

// @spec EL-UI-050, EL-UI-051b, EL-UI-052b, EL-UI-052c, EL-UI-055c, EL-UI-059b
fun ValueUIState.toEventValue(): EventValue? {
    return when (this) {
        ValueUIState.None -> null
        is ValueUIState.Number -> text.toDoubleOrNull()?.let { d ->
            EventValue.NumberValue(d, unit.takeIf { it.isNotBlank() })
        }
        is ValueUIState.Text -> EventValue.TextValue(text)
        is ValueUIState.Scale -> EventValue.Scale(value)
        is ValueUIState.Bool -> selected?.let { EventValue.BooleanValue(it) }
        is ValueUIState.Duration -> {
            val h = if (hoursText.isEmpty()) 0L else hoursText.toLongOrNull() ?: return null
            val m = if (minutesText.isEmpty()) 0 else minutesText.toIntOrNull() ?: return null
            val s = if (secondsText.isEmpty()) 0 else secondsText.toIntOrNull() ?: return null
            if (h < 0 || m < 0 || s < 0 || m >= 60 || s >= 60) return null
            EventValue.DurationValue((h * 3600 + m * 60 + s).seconds)
        }
        is ValueUIState.Exercise -> {
            val s = setsText.toIntOrNull()?.takeIf { it >= 1 } ?: return null
            val r = repsText.toIntOrNull()?.takeIf { it >= 1 } ?: return null
            EventValue.ExerciseValue(s, r)
        }
        is ValueUIState.ReadOnly -> originalValue
        is ValueUIState.Mismatched -> editableState?.toEventValue() ?: originalValue
    }
}

// @spec EL-UI-051b, EL-UI-052, EL-UI-055d, EL-UI-059
fun defaultValueUIStateForType(type: ValueType): ValueUIState = when (type) {
    ValueType.None, is ValueType.Unknown -> ValueUIState.None
    ValueType.Scale -> ValueUIState.Scale(5)
    ValueType.Boolean -> ValueUIState.Bool(null)
    ValueType.Number -> ValueUIState.Number("", "")
    ValueType.Text -> ValueUIState.Text("")
    ValueType.Duration -> ValueUIState.Duration("", "", "0")
    ValueType.Exercise -> ValueUIState.Exercise("3", "15")
}

// @spec EL-UI-057
fun validateValueForSave(value: ValueUIState, category: Category): String? {
    val ev = value.toEventValue()
    if (ev == null && value !is ValueUIState.None) return "value"
    if (category.resolvedValueType == ValueType.Text && !category.allowEmptyText &&
        ev is EventValue.TextValue && ev.text.isEmpty()
    ) return "value"
    return null
}

// @spec EL-UI-068b
fun ValueUIState.matchesType(type: ValueType): Boolean = when (type) {
    ValueType.None, is ValueType.Unknown -> this is ValueUIState.None
    ValueType.Scale -> this is ValueUIState.Scale
    ValueType.Boolean -> this is ValueUIState.Bool
    ValueType.Number -> this is ValueUIState.Number
    ValueType.Text -> this is ValueUIState.Text
    ValueType.Duration -> this is ValueUIState.Duration
    ValueType.Exercise -> this is ValueUIState.Exercise
}
