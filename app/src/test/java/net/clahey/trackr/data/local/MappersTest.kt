package net.clahey.trackr.data.local

import net.clahey.trackr.data.local.converters.StringListConverter
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.ValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class MappersTest {

    // @spec DM-PROC-022
    @Test fun `toDomainList surfaces orphaned SubCategory as MetaCategory`() {
        val orphan = CategoryEntity(
            id = "child", name = "Child", emoji = null, color = null, valueType = null,
            defaultValue = null, allowEmptyText = true, sortOrder = 1, parentId = "missing-parent",
        )
        val result = listOf(orphan).toDomainList()
        assertEquals(1, result.size)
        assertTrue("orphaned SubCategory should surface as MetaCategory", result[0] is Category.MetaCategory)
        assertEquals("child", result[0].id)
    }

    // @spec DM-PROC-022
    @Test fun `toDomainList uses null-field fallbacks when surfacing orphaned SubCategory`() {
        val orphan = CategoryEntity(
            id = "child", name = "Child", emoji = null, color = null, valueType = null,
            defaultValue = null, allowEmptyText = true, sortOrder = 1, parentId = "missing-parent",
        )
        val result = listOf(orphan).toDomainList()
        val meta = result[0] as Category.MetaCategory
        assertEquals("", meta.emoji)
        assertEquals(0xFFE53935L, meta.color)
        assertEquals(ValueType.None, meta.valueType)
    }

    // @spec DM-PROC-022
    @Test fun `toDomainList uses entity emoji when orphaned SubCategory has explicit emoji`() {
        val orphan = CategoryEntity(
            id = "child", name = "Child", emoji = "🎯", color = 0xFF123456L, valueType = "boolean",
            defaultValue = null, allowEmptyText = true, sortOrder = 1, parentId = "missing-parent",
        )
        val result = listOf(orphan).toDomainList()
        val meta = result[0] as Category.MetaCategory
        assertEquals("🎯", meta.emoji)
        assertEquals(0xFF123456L, meta.color)
        assertEquals(ValueType.Boolean, meta.valueType)
    }

    // @spec DM-PROC-017
    @Test fun `toDomainList correctly links SubCategory to its parent`() {
        val parentEntity = CategoryEntity(
            id = "parent", name = "Parent", emoji = "📌", color = 0xFFE53935L, valueType = "none",
            defaultValue = null, allowEmptyText = true, sortOrder = 0, parentId = null,
        )
        val childEntity = CategoryEntity(
            id = "child", name = "Child", emoji = null, color = null, valueType = null,
            defaultValue = null, allowEmptyText = true, sortOrder = 1, parentId = "parent",
        )
        val result = listOf(parentEntity, childEntity).toDomainList()
        assertEquals(2, result.size)
        val sub = result.filterIsInstance<Category.SubCategory>().single()
        assertEquals("parent", sub.parent.id)
    }

    private fun reminderEntity(enabled: Boolean, daysActive: List<String>) = ReminderEntity(
        categoryId = "cat1",
        enabled = enabled,
        mode = "fixed",
        times = StringListConverter.encode(listOf("08:00")),
        windowStart = null,
        windowEnd = null,
        occurrencesPerDay = null,
        daysActive = StringListConverter.encode(daysActive),
        showCategoryInNotification = false,
        nextFireAt = null,
    )

    // @spec REM-DATA-002
    @Test fun `toDomain preserves an enabled reminder with a non-empty daysActive`() {
        val entity = reminderEntity(enabled = true, daysActive = listOf("MONDAY", "WEDNESDAY"))
        val reminder = entity.toDomain()
        assertTrue(reminder.enabled)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), reminder.daysActive)
    }

    // @spec REM-DATA-002
    @Test fun `toDomain decodes an empty daysActive as disabled with all days set`() {
        val entity = reminderEntity(enabled = true, daysActive = emptyList())
        val reminder = entity.toDomain()
        assertFalse("a malformed empty daysActive should decode as disabled", reminder.enabled)
        assertEquals(DayOfWeek.entries.toSet(), reminder.daysActive)
    }

    // @spec REM-DATA-002
    @Test fun `toDomain normalizes daysActive to all days even when already disabled`() {
        val entity = reminderEntity(enabled = false, daysActive = emptyList())
        val reminder = entity.toDomain()
        assertFalse(reminder.enabled)
        assertEquals(DayOfWeek.entries.toSet(), reminder.daysActive)
    }
}
