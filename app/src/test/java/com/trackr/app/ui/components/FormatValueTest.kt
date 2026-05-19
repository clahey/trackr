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
}
