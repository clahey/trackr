package net.clahey.trackr.data.local

import net.clahey.trackr.data.local.converters.StringListConverter
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Reminder
import net.clahey.trackr.domain.ReminderMode
import net.clahey.trackr.domain.ValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

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

    // A row that decodes cleanly. Every field differs from Reminder.default's, so an equality check
    // against the default proves nothing from the row survived.
    private fun reminderEntity(
        enabled: Boolean = true,
        mode: String = "random",
        times: String? = StringListConverter.encode(listOf("08:00")),
        windowStart: String = "09:00",
        windowEnd: String = "21:00",
        occurrencesPerDay: Int = 4,
        daysActive: List<String> = listOf("MONDAY", "WEDNESDAY"),
    ) = ReminderEntity(
        categoryId = "cat1",
        enabled = enabled,
        mode = mode,
        times = times,
        windowStart = windowStart,
        windowEnd = windowEnd,
        occurrencesPerDay = occurrencesPerDay,
        daysActive = StringListConverter.encode(daysActive),
        showCategoryInNotification = false,
        nextFireAt = null,
    )

    // @spec REM-DATA-010
    @Test fun `a row that cannot produce a schedulable reminder decodes as the default`() {
        val cases = mapOf(
            "unrecognized mode" to reminderEntity(mode = "weekly"),
            "unrecognized weekday" to reminderEntity(daysActive = listOf("FUNDAY")),
            "time that is not HH:mm" to reminderEntity(times = StringListConverter.encode(listOf("25:00"))),
            "empty windowStart" to reminderEntity(windowStart = ""),
            "unparseable windowEnd" to reminderEntity(windowEnd = "nope"),
            "no active days" to reminderEntity(daysActive = emptyList()),
            "no active days on an already-disabled row" to reminderEntity(enabled = false, daysActive = emptyList()),
            "FIXED with no times" to reminderEntity(mode = "fixed", times = null),
            "occurrencesPerDay below 1" to reminderEntity(occurrencesPerDay = 0),
        )
        for ((name, entity) in cases) {
            assertEquals(name, Reminder.default("cat1"), entity.toDomain())
        }
    }

    // @spec REM-DATA-002, REM-DATA-010
    @Test fun `a row that can produce a schedulable reminder decodes unchanged`() {
        val random = reminderEntity().toDomain()
        assertTrue(random.enabled)
        assertEquals(ReminderMode.RANDOM, random.mode)
        assertEquals(LocalTime.of(9, 0), random.windowStart)
        assertEquals(LocalTime.of(21, 0), random.windowEnd)
        assertEquals(4, random.occurrencesPerDay)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), random.daysActive)

        // Lowercase is the legacy encoding, accepted on decode.
        val fixed = reminderEntity(mode = "fixed").toDomain()
        assertEquals(ReminderMode.FIXED, fixed.mode)
        assertEquals(listOf(LocalTime.of(8, 0)), fixed.times)

        // occurrencesPerDay is RANDOM's divisor; FIXED never reads it, so it cannot make a row unusable.
        val fixedZeroOccurrences = reminderEntity(mode = "fixed", occurrencesPerDay = 0).toDomain()
        assertEquals(ReminderMode.FIXED, fixedZeroOccurrences.mode)
        assertEquals(listOf(LocalTime.of(8, 0)), fixedZeroOccurrences.times)
    }

    // @spec REM-DATA-010
    @Test fun `a valid but disabled row keeps its own values`() {
        val reminder = reminderEntity(enabled = false).toDomain()
        assertFalse(reminder.enabled)
        assertEquals(ReminderMode.RANDOM, reminder.mode)
        assertEquals(4, reminder.occurrencesPerDay)
    }
}
