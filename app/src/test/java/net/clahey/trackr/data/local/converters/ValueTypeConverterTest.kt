package net.clahey.trackr.data.local.converters

import net.clahey.trackr.domain.ValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValueTypeConverterTest {

    // @spec DM-DATA-004
    @Test fun `encode known variants produce fixed lowercase names`() {
        assertEquals("none", ValueTypeConverter.encode(ValueType.None))
        assertEquals("scale", ValueTypeConverter.encode(ValueType.Scale))
        assertEquals("boolean", ValueTypeConverter.encode(ValueType.Boolean))
        assertEquals("number", ValueTypeConverter.encode(ValueType.Number))
        assertEquals("text", ValueTypeConverter.encode(ValueType.Text))
        assertEquals("duration", ValueTypeConverter.encode(ValueType.Duration))
        assertEquals("exercise", ValueTypeConverter.encode(ValueType.Exercise))
    }

    // @spec DM-DATA-003
    @Test fun `encode Unknown writes raw verbatim`() {
        val raw = "some_future_type"
        assertEquals(raw, ValueTypeConverter.encode(ValueType.Unknown(raw)))
    }

    // @spec DM-DATA-002
    @Test fun `decode unknown string produces Unknown with raw preserved`() {
        val raw = "future_type_xyz"
        val result = ValueTypeConverter.decode(raw)
        assertTrue(result is ValueType.Unknown)
        assertEquals(raw, (result as ValueType.Unknown).raw)
    }

    @Test fun `decode all known variant names`() {
        assertEquals(ValueType.None, ValueTypeConverter.decode("none"))
        assertEquals(ValueType.Scale, ValueTypeConverter.decode("scale"))
        assertEquals(ValueType.Boolean, ValueTypeConverter.decode("boolean"))
        assertEquals(ValueType.Number, ValueTypeConverter.decode("number"))
        assertEquals(ValueType.Text, ValueTypeConverter.decode("text"))
        assertEquals(ValueType.Duration, ValueTypeConverter.decode("duration"))
        assertEquals(ValueType.Exercise, ValueTypeConverter.decode("exercise"))
    }

    @Test fun `Unknown round-trips verbatim`() {
        val raw = "exotic_future_type"
        val encoded = ValueTypeConverter.encode(ValueType.Unknown(raw))
        val decoded = ValueTypeConverter.decode(encoded)
        assertTrue(decoded is ValueType.Unknown)
        assertEquals(raw, (decoded as ValueType.Unknown).raw)
    }
}
