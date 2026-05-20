package com.trackr.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValueTypeConversionTest {

    // region matchesValueType

    // @spec DM-PROC-013
    @Test fun `matchesValueType null on None is true`() {
        assertTrue(matchesValueType(null, ValueType.None))
    }

    // @spec DM-PROC-013
    @Test fun `matchesValueType null on non-None is false`() {
        assertFalse(matchesValueType(null, ValueType.Scale))
        assertFalse(matchesValueType(null, ValueType.Boolean))
        assertFalse(matchesValueType(null, ValueType.Exercise))
    }

    // @spec DM-PROC-013
    @Test fun `matchesValueType correct concrete types are true`() {
        assertTrue(matchesValueType(EventValue.Scale(7), ValueType.Scale))
        assertTrue(matchesValueType(EventValue.BooleanValue(false), ValueType.Boolean))
        assertTrue(matchesValueType(EventValue.NumberValue(1.0, null), ValueType.Number))
        assertTrue(matchesValueType(EventValue.TextValue("x"), ValueType.Text))
        assertTrue(matchesValueType(EventValue.DurationValue(kotlin.time.Duration.parse("1h")), ValueType.Duration))
        assertTrue(matchesValueType(EventValue.ExerciseValue(4, 12), ValueType.Exercise))
    }

    // @spec DM-PROC-013
    @Test fun `matchesValueType wrong concrete type is false`() {
        assertFalse(matchesValueType(EventValue.Scale(5), ValueType.Number))
        assertFalse(matchesValueType(EventValue.ExerciseValue(3, 15), ValueType.Text))
        assertFalse(matchesValueType(EventValue.BooleanValue(true), ValueType.Scale))
    }

    // @spec DM-PROC-013
    @Test fun `matchesValueType ErrorValue with matching inferredType and Unknown type is true`() {
        val error = EventValue.ErrorValue(ErrorKind.UNRECOGNIZED_TYPE, """{"type":"future_type"}""", inferredType = "future_type")
        assertTrue(matchesValueType(error, ValueType.Unknown("future_type")))
    }

    // @spec DM-PROC-013
    @Test fun `matchesValueType ErrorValue with null inferredType on Unknown type is false`() {
        assertFalse(matchesValueType(EventValue.ErrorValue(ErrorKind.UNRECOGNIZED_TYPE, "x", inferredType = null), ValueType.Unknown("x")))
    }

    // @spec DM-PROC-013
    @Test fun `matchesValueType ErrorValue with non-matching inferredType on Unknown type is false`() {
        val error = EventValue.ErrorValue(ErrorKind.UNRECOGNIZED_TYPE, """{"type":"strength"}""", inferredType = "strength")
        assertFalse(matchesValueType(error, ValueType.Unknown("mood")))
    }

    // @spec DM-PROC-013
    @Test fun `matchesValueType ErrorValue without matching inferredType is false`() {
        assertFalse(matchesValueType(EventValue.ErrorValue(ErrorKind.UNPARSABLE, "x"), ValueType.Scale))
        assertFalse(matchesValueType(EventValue.ErrorValue(ErrorKind.UNRECOGNIZED_TYPE, "x"), ValueType.None))
        assertFalse(matchesValueType(EventValue.ErrorValue(ErrorKind.OUT_OF_RANGE, "x"), ValueType.Unknown("x")))
    }

    // @spec DM-PROC-013
    @Test fun `matchesValueType Unknown category type is false for non-ErrorValue`() {
        assertFalse(matchesValueType(EventValue.Scale(5), ValueType.Unknown("future_type")))
        assertFalse(matchesValueType(null, ValueType.Unknown("future_type")))
        assertFalse(matchesValueType(EventValue.TextValue("x"), ValueType.Unknown("future_type")))
    }

    // @spec DM-PROC-013
    @Test fun `matchesValueType non-null value on None type is false`() {
        assertFalse(matchesValueType(EventValue.Scale(5), ValueType.None))
        assertFalse(matchesValueType(EventValue.TextValue(""), ValueType.None))
    }

    // endregion

    // region convertOrDefault — Discard cases

    // @spec DM-PROC-014
    @Test fun `convertOrDefault returns Discard for None target type`() {
        assertEquals(ConversionOutcome.Discard, convertOrDefault(EventValue.Scale(5), ValueType.None))
    }

    // @spec DM-PROC-014
    @Test fun `convertOrDefault returns Discard for Unknown target type`() {
        assertEquals(ConversionOutcome.Discard, convertOrDefault(EventValue.Scale(5), ValueType.Unknown("future")))
    }

    // @spec DM-PROC-016
    @Test fun `convertOrDefault returns UsedDefault for ErrorValue on concrete target type`() {
        val result = convertOrDefault(EventValue.ErrorValue(ErrorKind.UNPARSABLE, "x"), ValueType.Scale)
        assertTrue(result is ConversionOutcome.UsedDefault)
        assertEquals(EventValue.Scale(5), (result as ConversionOutcome.UsedDefault).value)
    }

    // @spec DM-PROC-016
    @Test fun `convertOrDefault returns UsedDefault for ErrorValue with exercise target`() {
        val result = convertOrDefault(EventValue.ErrorValue(ErrorKind.UNRECOGNIZED_TYPE, "{}"), ValueType.Exercise)
        assertTrue(result is ConversionOutcome.UsedDefault)
        assertEquals(EventValue.ExerciseValue(3, 15), (result as ConversionOutcome.UsedDefault).value)
    }

    // endregion

    // region convertOrDefault — Converted cases

    // @spec DM-PROC-015
    @Test fun `convertOrDefault returns Converted when scale converts to number`() {
        val result = convertOrDefault(EventValue.Scale(3), ValueType.Number)
        assertTrue(result is ConversionOutcome.Converted)
        assertEquals(EventValue.NumberValue(3.0, null), (result as ConversionOutcome.Converted).value)
    }

    // @spec DM-PROC-015
    @Test fun `convertOrDefault returns Converted when parseable text converts to scale`() {
        val result = convertOrDefault(EventValue.TextValue("7"), ValueType.Scale)
        assertTrue(result is ConversionOutcome.Converted)
        assertEquals(EventValue.Scale(7), (result as ConversionOutcome.Converted).value)
    }

    // @spec DM-PROC-015
    @Test fun `convertOrDefault returns Converted when parseable text converts to exercise`() {
        val result = convertOrDefault(EventValue.TextValue("2 × 5"), ValueType.Exercise)
        assertTrue(result is ConversionOutcome.Converted)
        assertEquals(EventValue.ExerciseValue(2, 5), (result as ConversionOutcome.Converted).value)
    }

    // @spec DM-PROC-015
    @Test fun `convertOrDefault returns Converted when boolean converts to text`() {
        val result = convertOrDefault(EventValue.BooleanValue(true), ValueType.Text)
        assertTrue(result is ConversionOutcome.Converted)
        assertEquals(EventValue.TextValue("Yes"), (result as ConversionOutcome.Converted).value)
    }

    // endregion

    // region convertOrDefault — UsedDefault cases

    // @spec DM-PROC-016
    @Test fun `convertOrDefault returns UsedDefault when text is not parseable as scale`() {
        val result = convertOrDefault(EventValue.TextValue("hello"), ValueType.Scale)
        assertTrue(result is ConversionOutcome.UsedDefault)
        assertEquals(EventValue.Scale(5), (result as ConversionOutcome.UsedDefault).value)
    }

    // @spec DM-PROC-016
    @Test fun `convertOrDefault returns UsedDefault when text is not parseable as exercise`() {
        val result = convertOrDefault(EventValue.TextValue("bad"), ValueType.Exercise)
        assertTrue(result is ConversionOutcome.UsedDefault)
        assertEquals(EventValue.ExerciseValue(3, 15), (result as ConversionOutcome.UsedDefault).value)
    }

    // @spec DM-PROC-016
    @Test fun `convertOrDefault returns UsedDefault when no conversion path exists`() {
        val result = convertOrDefault(EventValue.DurationValue(), ValueType.Scale)
        assertTrue(result is ConversionOutcome.UsedDefault)
        assertEquals(EventValue.Scale(5), (result as ConversionOutcome.UsedDefault).value)
    }

    // @spec DM-PROC-016
    @Test fun `convertOrDefault returns UsedDefault with correct default for number target`() {
        val result = convertOrDefault(EventValue.BooleanValue(false), ValueType.Number)
        assertTrue(result is ConversionOutcome.UsedDefault)
        assertEquals(EventValue.NumberValue(0.0, null), (result as ConversionOutcome.UsedDefault).value)
    }

    // endregion

    // region defaultForType

    // @spec DM-PROC-016
    @Test fun `defaultForType returns null for None and Unknown`() {
        assertNull(defaultForType(ValueType.None))
        assertNull(defaultForType(ValueType.Unknown("x")))
    }

    // @spec DM-PROC-016
    @Test fun `defaultForType returns zero-arg constructor defaults for known types`() {
        assertEquals(EventValue.Scale(5), defaultForType(ValueType.Scale))
        assertEquals(EventValue.BooleanValue(true), defaultForType(ValueType.Boolean))
        assertEquals(EventValue.NumberValue(0.0, null), defaultForType(ValueType.Number))
        assertEquals(EventValue.TextValue(""), defaultForType(ValueType.Text))
        assertEquals(EventValue.DurationValue(), defaultForType(ValueType.Duration))
        assertEquals(EventValue.ExerciseValue(3, 15), defaultForType(ValueType.Exercise))
    }

    // endregion
}
