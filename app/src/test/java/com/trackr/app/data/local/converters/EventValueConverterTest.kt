package com.trackr.app.data.local.converters

import com.trackr.app.domain.ErrorKind
import com.trackr.app.domain.EventValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class EventValueConverterTest {

    // @spec DM-PROC-001
    @Test fun `encode null returns null`() {
        assertNull(EventValueConverter.encode(null))
    }

    // @spec DM-PROC-002
    @Test fun `encode ErrorValue writes raw verbatim`() {
        val raw = """{"type":"FutureType","data":42}"""
        val result = EventValueConverter.encode(EventValue.ErrorValue(ErrorKind.UNRECOGNIZED_TYPE, raw))
        assertEquals(raw, result)
    }

    // @spec DM-PROC-002
    @Test fun `encode ErrorValue from future version round-trips unchanged`() {
        val futureJson = """{"type":"NewFancyType","value":99}"""
        val encoded = EventValueConverter.encode(EventValue.ErrorValue(ErrorKind.UNRECOGNIZED_TYPE, futureJson))
        assertEquals(futureJson, encoded)
    }

    // @spec DM-PROC-003
    @Test fun `encode Scale produces JSON with type discriminator`() {
        val result = EventValueConverter.encode(EventValue.Scale(7))!!
        assertTrue(result.contains("\"type\""))
        assertTrue(result.contains("7"))
    }

    // @spec DM-PROC-004
    @Test fun `decode null returns null`() {
        assertNull(EventValueConverter.decode(null))
    }

    // @spec DM-PROC-005
    @Test fun `decode JSON with unknown type discriminator returns UNRECOGNIZED_TYPE with inferredType`() {
        val raw = """{"type":"UnknownFutureType","value":1}"""
        val result = EventValueConverter.decode(raw)
        assertTrue(result is EventValue.ErrorValue)
        val error = result as EventValue.ErrorValue
        assertEquals(ErrorKind.UNRECOGNIZED_TYPE, error.kind)
        assertEquals(raw, error.raw)
        assertEquals("UnknownFutureType", error.inferredType)
    }

    // @spec DM-PROC-005
    @Test fun `decode JSON with type field that cannot be extracted as string leaves inferredType null`() {
        val raw = """{"type":42,"value":1}"""
        val result = EventValueConverter.decode(raw)
        assertTrue(result is EventValue.ErrorValue)
        val error = result as EventValue.ErrorValue
        assertEquals(ErrorKind.UNRECOGNIZED_TYPE, error.kind)
        assertNull(error.inferredType)
    }

    // @spec DM-PROC-006
    @Test fun `decode unparsable JSON returns UNPARSABLE`() {
        val raw = "not json at all"
        val result = EventValueConverter.decode(raw)
        assertTrue(result is EventValue.ErrorValue)
        assertEquals(ErrorKind.UNPARSABLE, (result as EventValue.ErrorValue).kind)
    }

    // @spec DM-PROC-007
    @Test fun `decode Scale below 1 returns OUT_OF_RANGE`() {
        val raw = """{"type":"Scale","value":0}"""
        val result = EventValueConverter.decode(raw)
        assertTrue(result is EventValue.ErrorValue)
        assertEquals(ErrorKind.OUT_OF_RANGE, (result as EventValue.ErrorValue).kind)
    }

    // @spec DM-PROC-007
    @Test fun `decode Scale above 10 returns OUT_OF_RANGE`() {
        val raw = """{"type":"Scale","value":11}"""
        val result = EventValueConverter.decode(raw)
        assertTrue(result is EventValue.ErrorValue)
        assertEquals(ErrorKind.OUT_OF_RANGE, (result as EventValue.ErrorValue).kind)
    }

    // @spec DM-PROC-008
    @Test fun `decode negative DurationValue returns OUT_OF_RANGE`() {
        val raw = """{"type":"DurationValue","duration":-60}"""
        val result = EventValueConverter.decode(raw)
        assertTrue(result is EventValue.ErrorValue)
        assertEquals(ErrorKind.OUT_OF_RANGE, (result as EventValue.ErrorValue).kind)
    }

    // @spec DM-PROC-008b
    @Test fun `decode ExerciseValue with sets 0 returns OUT_OF_RANGE`() {
        val raw = """{"type":"ExerciseValue","sets":0,"reps":10}"""
        val result = EventValueConverter.decode(raw)
        assertTrue(result is EventValue.ErrorValue)
        assertEquals(ErrorKind.OUT_OF_RANGE, (result as EventValue.ErrorValue).kind)
    }

    // @spec DM-PROC-008b
    @Test fun `decode ExerciseValue with reps 0 returns OUT_OF_RANGE`() {
        val raw = """{"type":"ExerciseValue","sets":3,"reps":0}"""
        val result = EventValueConverter.decode(raw)
        assertTrue(result is EventValue.ErrorValue)
        assertEquals(ErrorKind.OUT_OF_RANGE, (result as EventValue.ErrorValue).kind)
    }

    @Test fun `encode then decode round-trips ExerciseValue`() {
        val original = EventValue.ExerciseValue(3, 15)
        val result = EventValueConverter.decode(EventValueConverter.encode(original))
        assertEquals(original, result)
    }

    // @spec DM-PROC-009
    @Test fun `decode never throws exception for any input`() {
        val inputs = listOf(null, "", "{}", "null", "[]", """{"type":"X"}""", "true", "12345")
        for (input in inputs) {
            try {
                EventValueConverter.decode(input)
            } catch (e: Exception) {
                throw AssertionError("decode threw for input '$input': $e")
            }
        }
    }

    @Test fun `encode then decode round-trips Scale`() {
        val original = EventValue.Scale(5)
        val result = EventValueConverter.decode(EventValueConverter.encode(original))
        assertEquals(original, result)
    }

    @Test fun `encode then decode round-trips DurationValue`() {
        val original = EventValue.DurationValue(90.minutes)
        val result = EventValueConverter.decode(EventValueConverter.encode(original))
        assertEquals(original, result)
    }

    // @spec DM-PROC-002, DM-PROC-005
    @Test fun `UNRECOGNIZED_TYPE error survives encode-decode round-trip`() {
        val raw = """{"type":"FutureType","data":42}"""
        val first = EventValueConverter.decode(raw) as EventValue.ErrorValue
        assertEquals(ErrorKind.UNRECOGNIZED_TYPE, first.kind)
        assertEquals(raw, first.raw)
        val second = EventValueConverter.decode(EventValueConverter.encode(first))
        assertEquals(first, second)
    }

    // @spec DM-PROC-002, DM-PROC-006
    @Test fun `UNPARSABLE error survives encode-decode round-trip`() {
        val raw = "not json at all"
        val first = EventValueConverter.decode(raw) as EventValue.ErrorValue
        assertEquals(ErrorKind.UNPARSABLE, first.kind)
        assertEquals(raw, first.raw)
        val second = EventValueConverter.decode(EventValueConverter.encode(first))
        assertEquals(first, second)
    }
}
