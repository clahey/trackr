package com.trackr.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTest {

    private val parent = Category.MetaCategory(
        id = "parent",
        name = "Fitness",
        emoji = "🏃",
        color = 0xFFE53935L,
        valueType = ValueType.Number,
        unit = null,
        allowEmptyText = true,
        sortOrder = 0,
    )

    // @spec DM-PROC-018
    @Test fun `MetaCategory resolvedEmoji returns own emoji`() {
        assertEquals("🏃", parent.resolvedEmoji)
    }

    // @spec DM-PROC-018
    @Test fun `MetaCategory resolvedColor returns own color`() {
        assertEquals(0xFFE53935L, parent.resolvedColor)
    }

    // @spec DM-PROC-018
    @Test fun `MetaCategory resolvedValueType returns own valueType`() {
        assertEquals(ValueType.Number, parent.resolvedValueType)
    }

    // @spec DM-PROC-018
    @Test fun `SubCategory resolvedEmoji returns own emoji when non-null`() {
        val child = makeSubCategory(emoji = "🚶", color = null, valueType = null)
        assertEquals("🚶", child.resolvedEmoji)
    }

    // @spec DM-PROC-018
    @Test fun `SubCategory resolvedEmoji returns parent emoji when null`() {
        val child = makeSubCategory(emoji = null, color = null, valueType = null)
        assertEquals("🏃", child.resolvedEmoji)
    }

    // @spec DM-PROC-018
    @Test fun `SubCategory resolvedColor returns own color when non-null`() {
        val child = makeSubCategory(emoji = null, color = 0xFF1E88E5L, valueType = null)
        assertEquals(0xFF1E88E5L, child.resolvedColor)
    }

    // @spec DM-PROC-018
    @Test fun `SubCategory resolvedColor returns parent color when null`() {
        val child = makeSubCategory(emoji = null, color = null, valueType = null)
        assertEquals(0xFFE53935L, child.resolvedColor)
    }

    // @spec DM-PROC-018
    @Test fun `SubCategory resolvedValueType returns own type when non-null`() {
        val child = makeSubCategory(emoji = null, color = null, valueType = ValueType.Text)
        assertEquals(ValueType.Text, child.resolvedValueType)
    }

    // @spec DM-PROC-018
    @Test fun `SubCategory resolvedValueType returns parent type when null`() {
        val child = makeSubCategory(emoji = null, color = null, valueType = null)
        assertEquals(ValueType.Number, child.resolvedValueType)
    }

    private fun makeSubCategory(
        emoji: String?,
        color: Long?,
        valueType: ValueType?,
    ) = Category.SubCategory(
        id = "child",
        name = "Child",
        emoji = emoji,
        color = color,
        valueType = valueType,
        unit = null,
        allowEmptyText = true,
        sortOrder = 1,
        parent = parent,
    )
}
