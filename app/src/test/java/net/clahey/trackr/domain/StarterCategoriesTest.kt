package net.clahey.trackr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterCategoriesTest {

    private val specs = listOf(
        StarterCategoryInput("Mood", "🙂", 1L, ValueType.Scale, null),
        StarterCategoryInput("Water", "💧", 2L, ValueType.Number, EventValue.NumberValue(0.0, "glasses")),
    )

    // @spec CAT-UI-090
    @Test fun `inserts all specs when none exist`() {
        val result = starterCategoriesToInsert(emptyList(), null, specs, newId = { "id" })
        assertEquals(listOf("Mood", "Water"), result.map { it.name })
        assertTrue(result.all { it.allowEmptyText })
        assertEquals(ValueType.Number, result[1].valueType)
        assertEquals(EventValue.NumberValue(0.0, "glasses"), result[1].defaultValue)
    }

    // @spec CAT-UI-090
    @Test fun `skips specs whose name already exists case-insensitively`() {
        val result = starterCategoriesToInsert(listOf("  mood "), 0, specs)
        assertEquals(listOf("Water"), result.map { it.name })
    }

    // @spec CAT-UI-090
    @Test fun `returns empty when all present`() {
        val result = starterCategoriesToInsert(listOf("Mood", "Water"), 0, specs)
        assertTrue(result.isEmpty())
    }

    // @spec CAT-UI-090
    @Test fun `places the block above existing min in listed order`() {
        val result = starterCategoriesToInsert(listOf("Existing"), 5, specs)
        // base=5, size=2 -> sortOrders 3,4; Mood(3) sits above Water(4), both above existing(5)
        assertEquals(listOf(3, 4), result.map { it.sortOrder })
        assertEquals("Mood", result.first().name)
    }

    // @spec CAT-UI-090
    @Test fun `handles null minSortOrder`() {
        val result = starterCategoriesToInsert(emptyList(), null, specs)
        assertEquals(listOf(-2, -1), result.map { it.sortOrder })
    }
}
