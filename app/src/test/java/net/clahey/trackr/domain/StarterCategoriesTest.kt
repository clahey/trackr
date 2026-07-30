package net.clahey.trackr.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StarterCategoriesTest {

    private val specs = listOf(
        StarterCategoryInput("Mood", "🙂", 1L, ValueType.Scale, null),
        StarterCategoryInput("Water", "💧", 2L, ValueType.Number, EventValue.NumberValue(0.0, "glasses")),
    )

    private fun expectedMood(sortOrder: Int, id: String = "id") = Category.MetaCategory(
        id = id, name = "Mood", emoji = "🙂", color = 1L, valueType = ValueType.Scale,
        defaultValue = null, allowEmptyText = true, sortOrder = sortOrder,
    )

    private fun expectedWater(sortOrder: Int, id: String = "id") = Category.MetaCategory(
        id = id, name = "Water", emoji = "💧", color = 2L, valueType = ValueType.Number,
        defaultValue = EventValue.NumberValue(0.0, "glasses"), allowEmptyText = true, sortOrder = sortOrder,
    )

    // @spec CAT-UI-090
    @Test fun `inserts all specs when none exist`() {
        val result = starterCategoriesToInsert(emptyList(), null, specs, newId = { "id" })
        assertEquals(listOf(expectedMood(-2), expectedWater(-1)), result)
    }

    // @spec CAT-UI-090
    @Test fun `skips specs whose name already exists case-insensitively`() {
        val result = starterCategoriesToInsert(listOf("  mood "), 0, specs, newId = { "id" })
        assertEquals(listOf(expectedWater(-1)), result)
    }

    // @spec CAT-UI-090
    @Test fun `returns empty when all present`() {
        val result = starterCategoriesToInsert(listOf("Mood", "Water"), 0, specs, newId = { "id" })
        assertEquals(emptyList<Category.MetaCategory>(), result)
    }

    // @spec CAT-UI-090
    @Test fun `places the block above existing min in listed order`() {
        val result = starterCategoriesToInsert(listOf("Existing"), 5, specs, newId = { "id" })
        // base=5, size=2 -> sortOrders 3,4; Mood(3) sits above Water(4), both above existing(5)
        assertEquals(listOf(expectedMood(3), expectedWater(4)), result)
    }

    // @spec CAT-UI-090
    @Test fun `handles null minSortOrder`() {
        val result = starterCategoriesToInsert(emptyList(), null, specs, newId = { "id" })
        assertEquals(listOf(expectedMood(-2), expectedWater(-1)), result)
    }
}
