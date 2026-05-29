package com.trackr.app.ui.components

import com.trackr.app.domain.Category
import com.trackr.app.domain.ConversionOutcome
import com.trackr.app.domain.ErrorKind
import com.trackr.app.domain.EventValue
import com.trackr.app.domain.ValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ValueUIStateTest {

    // ─── EventValue.toValueUIState() ───────────────────────────────────────

    // @spec EL-UI-050
    @Test fun `Scale toValueUIState maps value`() {
        assertEquals(ValueUIState.Scale(7), EventValue.Scale(7).toValueUIState())
    }

    // @spec EL-UI-051
    @Test fun `BooleanValue true toValueUIState maps to Bool true`() {
        assertEquals(ValueUIState.Bool(true), EventValue.BooleanValue(true).toValueUIState())
    }

    // @spec EL-UI-051
    @Test fun `BooleanValue false toValueUIState maps to Bool false`() {
        assertEquals(ValueUIState.Bool(false), EventValue.BooleanValue(false).toValueUIState())
    }

    // @spec EL-UI-052
    @Test fun `NumberValue toValueUIState maps text and unit`() {
        assertEquals(
            ValueUIState.Number("3.5", "kg"),
            EventValue.NumberValue(3.5, "kg").toValueUIState(),
        )
    }

    // @spec EL-UI-052
    @Test fun `NumberValue with null unit toValueUIState maps empty unit string`() {
        assertEquals(
            ValueUIState.Number("3.5", ""),
            EventValue.NumberValue(3.5, null).toValueUIState(),
        )
    }

    // @spec EL-UI-053
    @Test fun `TextValue toValueUIState maps text`() {
        assertEquals(ValueUIState.Text("hello"), EventValue.TextValue("hello").toValueUIState())
    }

    // @spec EL-UI-059
    @Test fun `ExerciseValue toValueUIState maps sets and reps as text`() {
        assertEquals(
            ValueUIState.Exercise("3", "15"),
            EventValue.ExerciseValue(3, 15).toValueUIState(),
        )
    }

    // @spec EL-UI-055, EL-UI-055d
    @Test fun `DurationValue zero toValueUIState produces empty-empty-zero`() {
        assertEquals(
            ValueUIState.Duration("", "", "0"),
            EventValue.DurationValue(0.seconds).toValueUIState(),
        )
    }

    // @spec EL-UI-055d
    @Test fun `DurationValue with only seconds toValueUIState elides hours and minutes`() {
        assertEquals(
            ValueUIState.Duration("", "", "30"),
            EventValue.DurationValue(30.seconds).toValueUIState(),
        )
    }

    // @spec EL-UI-055d
    @Test fun `DurationValue with minutes but no hours shows minutes and seconds, elides hours`() {
        assertEquals(
            ValueUIState.Duration("", "5", "0"),
            EventValue.DurationValue(5.minutes).toValueUIState(),
        )
    }

    // @spec EL-UI-055d
    @Test fun `DurationValue with hours shows all fields including zero minutes and seconds`() {
        assertEquals(
            ValueUIState.Duration("1", "0", "0"),
            EventValue.DurationValue(1.hours).toValueUIState(),
        )
    }

    // @spec EL-UI-055d
    @Test fun `DurationValue with all components shows all fields`() {
        assertEquals(
            ValueUIState.Duration("1", "30", "45"),
            EventValue.DurationValue(1.hours + 30.minutes + 45.seconds).toValueUIState(),
        )
    }

    // @spec EL-UI-056
    @Test fun `ErrorValue toValueUIState maps to ReadOnly`() {
        val ev = EventValue.ErrorValue(ErrorKind.UNPARSABLE, "bad")
        val result = ev.toValueUIState()
        assertTrue(result is ValueUIState.ReadOnly)
    }

    // ─── EventValue?.toValueUIState(ValueType) — null value ───────────────

    // @spec EL-UI-067
    @Test fun `null value with None type toValueUIState with type returns None`() {
        assertEquals(ValueUIState.None, null.toValueUIState(ValueType.None))
    }

    // @spec EL-UI-067
    @Test fun `null value with Number type toValueUIState with type returns empty Number state`() {
        assertEquals(ValueUIState.Number("", ""), null.toValueUIState(ValueType.Number))
    }

    // @spec EL-UI-067
    @Test fun `null value with Scale type toValueUIState with type returns default Scale state`() {
        assertEquals(ValueUIState.Scale(5), null.toValueUIState(ValueType.Scale))
    }

    // ─── EventValue?.toValueUIState(ValueType) — matched ──────────────────

    // @spec EL-UI-062
    @Test fun `matched Scale value toValueUIState with Scale type returns Scale state`() {
        assertEquals(
            ValueUIState.Scale(7),
            EventValue.Scale(7).toValueUIState(ValueType.Scale),
        )
    }

    // @spec EL-UI-062
    @Test fun `matched Number value toValueUIState with Number type returns Number state`() {
        assertEquals(
            ValueUIState.Number("3.5", "kg"),
            EventValue.NumberValue(3.5, "kg").toValueUIState(ValueType.Number),
        )
    }

    // ─── EventValue?.toValueUIState(ValueType) — mismatched ───────────────

    // @spec EL-UI-062
    @Test fun `mismatched value toValueUIState with type returns Mismatched`() {
        val result = EventValue.BooleanValue(true).toValueUIState(ValueType.Scale)
        assertTrue(result is ValueUIState.Mismatched)
    }

    // @spec EL-UI-062
    @Test fun `Mismatched state carries original value`() {
        val ev = EventValue.BooleanValue(true)
        val result = ev.toValueUIState(ValueType.Scale) as ValueUIState.Mismatched
        assertEquals(ev, result.originalValue)
    }

    // @spec EL-UI-062, EL-UI-064
    @Test fun `Mismatched state has UsedDefault outcome when no conversion path`() {
        val result = EventValue.BooleanValue(true).toValueUIState(ValueType.Scale) as ValueUIState.Mismatched
        assertTrue(result.outcome is ConversionOutcome.UsedDefault)
    }

    // @spec EL-UI-062
    @Test fun `Mismatched state has non-null editable state for non-ErrorValue mismatch`() {
        val result = EventValue.BooleanValue(true).toValueUIState(ValueType.Scale) as ValueUIState.Mismatched
        assertEquals(ValueUIState.Bool(true), result.editableState)
    }

    // @spec EL-UI-062, EL-UI-065
    @Test fun `ErrorValue toValueUIState with mismatched type has null editable state`() {
        val ev = EventValue.ErrorValue(ErrorKind.UNPARSABLE, "bad")
        val result = ev.toValueUIState(ValueType.Number) as ValueUIState.Mismatched
        assertNull(result.editableState)
    }

    // @spec EL-UI-062, EL-UI-065
    @Test fun `value with None target type produces Mismatched with Discard outcome`() {
        val result = EventValue.TextValue("hello").toValueUIState(ValueType.None) as ValueUIState.Mismatched
        assertEquals(ConversionOutcome.Discard, result.outcome)
        assertNull(result.editableState)
    }

    // ─── ValueUIState.toEventValue() ──────────────────────────────────────

    // @spec EL-UI-050
    @Test fun `Scale toEventValue returns Scale`() {
        assertEquals(EventValue.Scale(7), ValueUIState.Scale(7).toEventValue())
    }

    // @spec EL-UI-051
    @Test fun `Bool true toEventValue returns BooleanValue true`() {
        assertEquals(EventValue.BooleanValue(true), ValueUIState.Bool(true).toEventValue())
    }

    // @spec EL-UI-051b
    @Test fun `Bool null toEventValue returns null`() {
        assertNull(ValueUIState.Bool(null).toEventValue())
    }

    // @spec EL-UI-052
    @Test fun `Number with valid text toEventValue returns NumberValue`() {
        assertEquals(
            EventValue.NumberValue(3.5, "kg"),
            ValueUIState.Number("3.5", "kg").toEventValue(),
        )
    }

    // @spec EL-UI-052
    @Test fun `Number with empty unit toEventValue returns NumberValue with null unit`() {
        assertEquals(
            EventValue.NumberValue(3.5, null),
            ValueUIState.Number("3.5", "").toEventValue(),
        )
    }

    // @spec EL-UI-052b, EL-UI-052c
    @Test fun `Number with empty text toEventValue returns null`() {
        assertNull(ValueUIState.Number("", "kg").toEventValue())
    }

    // @spec EL-UI-052b, EL-UI-052c
    @Test fun `Number with non-parseable text toEventValue returns null`() {
        assertNull(ValueUIState.Number("abc", "kg").toEventValue())
    }

    // @spec EL-UI-052c
    @Test fun `Number with trailing decimal text toEventValue parses as double (text preserved in UI state)`() {
        assertEquals(
            EventValue.NumberValue(3.0, "kg"),
            ValueUIState.Number("3.", "kg").toEventValue(),
        )
    }

    // @spec EL-UI-053
    @Test fun `Text toEventValue returns TextValue`() {
        assertEquals(EventValue.TextValue("hello"), ValueUIState.Text("hello").toEventValue())
    }

    // @spec EL-UI-055c
    @Test fun `Duration with all empty fields toEventValue returns zero duration`() {
        assertEquals(
            EventValue.DurationValue(0.seconds),
            ValueUIState.Duration("", "", "").toEventValue(),
        )
    }

    // @spec EL-UI-055c
    @Test fun `Duration with empty hours and minutes toEventValue returns seconds only`() {
        assertEquals(
            EventValue.DurationValue(30.seconds),
            ValueUIState.Duration("", "", "30").toEventValue(),
        )
    }

    // @spec EL-UI-055
    @Test fun `Duration with all fields filled toEventValue returns correct duration`() {
        assertEquals(
            EventValue.DurationValue(1.hours + 30.minutes + 45.seconds),
            ValueUIState.Duration("1", "30", "45").toEventValue(),
        )
    }

    // @spec EL-UI-059
    @Test fun `Exercise with valid fields toEventValue returns ExerciseValue`() {
        assertEquals(
            EventValue.ExerciseValue(3, 15),
            ValueUIState.Exercise("3", "15").toEventValue(),
        )
    }

    // @spec EL-UI-059b
    @Test fun `Exercise with empty sets toEventValue returns null`() {
        assertNull(ValueUIState.Exercise("", "15").toEventValue())
    }

    // @spec EL-UI-059b
    @Test fun `Exercise with zero reps toEventValue returns null`() {
        assertNull(ValueUIState.Exercise("3", "0").toEventValue())
    }

    // @spec EL-UI-059b
    @Test fun `Exercise with non-numeric sets toEventValue returns null`() {
        assertNull(ValueUIState.Exercise("abc", "15").toEventValue())
    }

    // @spec EL-UI-062
    @Test fun `Mismatched with non-null editable state toEventValue returns edited value`() {
        val original = EventValue.BooleanValue(true)
        val edited = ValueUIState.Bool(false)
        val state = ValueUIState.Mismatched(
            originalValue = original,
            targetType = ValueType.Scale,
            editableState = edited,
        )
        assertEquals(EventValue.BooleanValue(false), state.toEventValue())
    }

    // @spec EL-UI-062
    @Test fun `Mismatched with null editable state toEventValue returns original value`() {
        val original = EventValue.ErrorValue(ErrorKind.UNPARSABLE, "bad")
        val state = ValueUIState.Mismatched(
            originalValue = original,
            targetType = ValueType.Scale,
            editableState = null,
        )
        assertEquals(original, state.toEventValue())
    }

    // ─── Mismatched.outcome (computed) ────────────────────────────────────

    // @spec EL-UI-063, EL-UI-064
    @Test fun `Mismatched outcome reflects editableState when it yields non-null EventValue`() {
        // Scale(5)->Number gives Converted(5.0); Scale(7)->Number gives Converted(7.0)
        val m = ValueUIState.Mismatched(
            originalValue = EventValue.Scale(5),
            targetType = ValueType.Number,
            editableState = ValueUIState.Scale(7),
        )
        assertEquals(ConversionOutcome.Converted(EventValue.NumberValue(7.0, null)), m.outcome)
    }

    // @spec EL-UI-063, EL-UI-064
    @Test fun `Mismatched outcome falls back to originalValue when editableState is null`() {
        val m = ValueUIState.Mismatched(
            originalValue = EventValue.Scale(5),
            targetType = ValueType.Number,
            editableState = null,
        )
        assertEquals(ConversionOutcome.Converted(EventValue.NumberValue(5.0, null)), m.outcome)
    }

    // @spec EL-UI-063, EL-UI-064
    @Test fun `Mismatched outcome uses default when editableState is non-null but yields null toEventValue`() {
        val m = ValueUIState.Mismatched(
            originalValue = EventValue.Scale(5),
            targetType = ValueType.Number,
            editableState = ValueUIState.Bool(null), // Bool(null).toEventValue() == null
        )
        // partial/invalid input — offer default for target type, not a conversion of originalValue
        assertEquals(ConversionOutcome.UsedDefault(EventValue.NumberValue(0.0, null)), m.outcome)
    }

    // ─── defaultValueUIStateForType ────────────────────────────────────────

    // @spec EL-UI-051b
    @Test fun `defaultValueUIStateForType Boolean returns Bool null`() {
        assertEquals(ValueUIState.Bool(null), defaultValueUIStateForType(ValueType.Boolean))
    }

    // @spec EL-UI-052
    @Test fun `defaultValueUIStateForType Number returns empty text and unit`() {
        assertEquals(ValueUIState.Number("", ""), defaultValueUIStateForType(ValueType.Number))
    }

    // @spec EL-UI-055d
    @Test fun `defaultValueUIStateForType Duration returns empty-empty-zero`() {
        assertEquals(ValueUIState.Duration("", "", "0"), defaultValueUIStateForType(ValueType.Duration))
    }

    // @spec EL-UI-059
    @Test fun `defaultValueUIStateForType Exercise returns 3 and 15`() {
        assertEquals(ValueUIState.Exercise("3", "15"), defaultValueUIStateForType(ValueType.Exercise))
    }

    // @spec EL-UI-050
    @Test fun `defaultValueUIStateForType Scale returns Scale 5`() {
        assertEquals(ValueUIState.Scale(5), defaultValueUIStateForType(ValueType.Scale))
    }

    // ─── ValueUIState.matchesType ──────────────────────────────────────────

    // @spec EL-UI-068b
    @Test fun `Scale matchesType Scale`() = assertTrue(ValueUIState.Scale(5).matchesType(ValueType.Scale))

    // @spec EL-UI-068b
    @Test fun `Number matchesType Number`() = assertTrue(ValueUIState.Number("3", "kg").matchesType(ValueType.Number))

    // @spec EL-UI-068b
    @Test fun `Bool matchesType Boolean`() = assertTrue(ValueUIState.Bool(true).matchesType(ValueType.Boolean))

    // @spec EL-UI-068b
    @Test fun `Text matchesType Text`() = assertTrue(ValueUIState.Text("hi").matchesType(ValueType.Text))

    // @spec EL-UI-068b
    @Test fun `Duration matchesType Duration`() = assertTrue(ValueUIState.Duration("1", "0", "0").matchesType(ValueType.Duration))

    // @spec EL-UI-068b
    @Test fun `Exercise matchesType Exercise`() = assertTrue(ValueUIState.Exercise("3", "15").matchesType(ValueType.Exercise))

    // @spec EL-UI-068b
    @Test fun `None matchesType None`() = assertTrue(ValueUIState.None.matchesType(ValueType.None))

    // @spec EL-UI-068b
    @Test fun `Scale does not matchesType Number`() = assertFalse(ValueUIState.Scale(5).matchesType(ValueType.Number))

    // @spec EL-UI-068b
    @Test fun `None does not matchesType Scale`() = assertFalse(ValueUIState.None.matchesType(ValueType.Scale))

    // @spec EL-UI-068b
    @Test fun `ReadOnly does not matchesType Scale`() {
        val ro = ValueUIState.ReadOnly("bad", EventValue.ErrorValue(ErrorKind.UNPARSABLE, "bad"))
        assertFalse(ro.matchesType(ValueType.Scale))
    }

    // @spec EL-UI-068b
    @Test fun `Mismatched does not matchesType Scale`() {
        val m = ValueUIState.Mismatched(
            originalValue = EventValue.BooleanValue(true),
            targetType = ValueType.Number,
            editableState = ValueUIState.Bool(true),
        )
        assertFalse(m.matchesType(ValueType.Scale))
    }

    // ─── validateValueForSave ──────────────────────────────────────────────

    private fun makeCategory(valueType: ValueType, allowEmptyText: Boolean = true) =
        Category.MetaCategory(
            id = "c1", name = "c1", emoji = "📌", color = 0L,
            valueType = valueType, defaultValue = null, allowEmptyText = allowEmptyText, sortOrder = 0,
        )

    // @spec EL-UI-057
    @Test fun `validateValueForSave None with null toEventValue returns null`() {
        assertNull(validateValueForSave(ValueUIState.None, makeCategory(ValueType.None)))
    }

    // @spec EL-UI-057
    @Test fun `validateValueForSave Scale with valid value returns null`() {
        assertNull(validateValueForSave(ValueUIState.Scale(7), makeCategory(ValueType.Scale)))
    }

    // @spec EL-UI-057
    @Test fun `validateValueForSave Bool null returns field name`() {
        assertEquals("value", validateValueForSave(ValueUIState.Bool(null), makeCategory(ValueType.Boolean)))
    }

    // @spec EL-UI-057
    @Test fun `validateValueForSave Number with empty text returns field name`() {
        assertEquals("value", validateValueForSave(ValueUIState.Number("", "kg"), makeCategory(ValueType.Number)))
    }

    // @spec EL-UI-057
    @Test fun `validateValueForSave Number with valid text returns null`() {
        assertNull(validateValueForSave(ValueUIState.Number("3.5", "kg"), makeCategory(ValueType.Number)))
    }

    // @spec EL-UI-057
    @Test fun `validateValueForSave Text empty with allowEmptyText false returns field name`() {
        assertEquals("value", validateValueForSave(ValueUIState.Text(""), makeCategory(ValueType.Text, allowEmptyText = false)))
    }

    // @spec EL-UI-057
    @Test fun `validateValueForSave Text empty with allowEmptyText true returns null`() {
        assertNull(validateValueForSave(ValueUIState.Text(""), makeCategory(ValueType.Text, allowEmptyText = true)))
    }

    // @spec EL-UI-057
    @Test fun `validateValueForSave Text non-empty with allowEmptyText false returns null`() {
        assertNull(validateValueForSave(ValueUIState.Text("hello"), makeCategory(ValueType.Text, allowEmptyText = false)))
    }

    // @spec EL-UI-057
    @Test fun `validateValueForSave Duration all-empty returns null`() {
        assertNull(validateValueForSave(ValueUIState.Duration("", "", ""), makeCategory(ValueType.Duration)))
    }

    // @spec EL-UI-057
    @Test fun `validateValueForSave Exercise with empty sets returns field name`() {
        assertEquals("value", validateValueForSave(ValueUIState.Exercise("", "15"), makeCategory(ValueType.Exercise)))
    }
}
