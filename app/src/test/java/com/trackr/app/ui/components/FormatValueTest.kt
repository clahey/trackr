package com.trackr.app.ui.components

import com.trackr.app.domain.EventValue
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatValueTest {

    // @spec EL-UI-060
    @Test fun `formatValue for ExerciseValue uses unicode times sign`() {
        assertEquals("3 × 15", formatValue(EventValue.ExerciseValue(3, 15)))
    }

    // @spec EL-UI-060
    @Test fun `formatValue for ExerciseValue with single set and rep`() {
        assertEquals("1 × 1", formatValue(EventValue.ExerciseValue(1, 1)))
    }

    // region describeValue — verbose human-readable format for banner button labels

    // @spec EL-UI-063, EL-UI-064
    @Test fun `describeValue for Scale uses fraction notation`() {
        assertEquals("7/10", describeValue(EventValue.Scale(7)))
        assertEquals("1/10", describeValue(EventValue.Scale(1)))
    }

    // @spec EL-UI-063, EL-UI-064
    @Test fun `describeValue for BooleanValue uses Yes and No`() {
        assertEquals("Yes", describeValue(EventValue.BooleanValue(true)))
        assertEquals("No", describeValue(EventValue.BooleanValue(false)))
    }

    // @spec EL-UI-063, EL-UI-064
    @Test fun `describeValue for NumberValue with unit appends unit`() {
        assertEquals("3.5 kg", describeValue(EventValue.NumberValue(3.5, "kg")))
    }

    // @spec EL-UI-063, EL-UI-064
    @Test fun `describeValue for NumberValue without unit omits unit`() {
        assertEquals("3.5", describeValue(EventValue.NumberValue(3.5, null)))
    }

    // @spec EL-UI-063, EL-UI-064
    @Test fun `describeValue for ExerciseValue uses verbose sets and reps format`() {
        assertEquals("2 sets × 5 reps", describeValue(EventValue.ExerciseValue(2, 5)))
        assertEquals("1 sets × 1 reps", describeValue(EventValue.ExerciseValue(1, 1)))
    }

    // @spec EL-UI-063, EL-UI-064
    @Test fun `describeValue for TextValue returns text verbatim`() {
        assertEquals("hello world", describeValue(EventValue.TextValue("hello world")))
        assertEquals("", describeValue(EventValue.TextValue("")))
    }

    // @spec EL-UI-063, EL-UI-064
    @Test fun `describeValue for DurationValue uses duration toString`() {
        val duration = kotlin.time.Duration.parse("1h 30m")
        assertEquals(duration.toString(), describeValue(EventValue.DurationValue(duration)))
    }

    // endregion
}
