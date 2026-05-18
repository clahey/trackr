package com.trackr.app.data.local.converters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringListConverterTest {

    // @spec LS-BE-053
    @Test fun `encode empty list produces JSON array string`() {
        val result = StringListConverter.encode(emptyList())
        assertEquals("[]", result)
    }

    // @spec LS-BE-053
    @Test fun `encode list produces JSON array`() {
        val result = StringListConverter.encode(listOf("/images/a.jpg", "/images/b.jpg"))
        assertTrue(result.contains("a.jpg"))
        assertTrue(result.contains("b.jpg"))
    }

    // @spec LS-BE-053
    @Test fun `decode valid JSON array produces list`() {
        val result = StringListConverter.decode("""["/images/a.jpg","/images/b.jpg"]""")
        assertEquals(listOf("/images/a.jpg", "/images/b.jpg"), result)
    }

    // @spec LS-BE-053
    @Test fun `decode failure returns empty list`() {
        val result = StringListConverter.decode("not valid json")
        assertTrue(result.isEmpty())
    }

    // @spec LS-BE-053
    @Test fun `decode empty array returns empty list`() {
        assertTrue(StringListConverter.decode("[]").isEmpty())
    }

    @Test fun `round-trip preserves list`() {
        val original = listOf("/path/a.jpg", "/path/b.png", "/path/c.jpg")
        assertEquals(original, StringListConverter.decode(StringListConverter.encode(original)))
    }
}
