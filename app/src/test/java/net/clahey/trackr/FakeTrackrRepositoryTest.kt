package net.clahey.trackr

import app.cash.turbine.test
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.CategoryHasChildrenException
import net.clahey.trackr.domain.Event
import net.clahey.trackr.domain.Reminder
import net.clahey.trackr.domain.ReminderMode
import net.clahey.trackr.domain.ValueType
import java.time.DayOfWeek
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FakeTrackrRepositoryTest {

    // @spec LS-BE-010
    @Test fun `getCategories returns categories sorted by sortOrder ascending`() = runTest {
        val repo = FakeTrackrRepository()
        repo.setCategories(makeCategory("c3", 3), makeCategory("c1", 1), makeCategory("c2", 2))
        val result = repo.getCategories().first()
        assertEquals(listOf("c1", "c2", "c3"), result.map { it.id })
    }

    // @spec LS-BE-081
    @Test fun `getAndIncrementNextCategoryColorIndex cycles within palette size`() = runTest {
        val repo = FakeTrackrRepository()
        val results = (0 until 5).map { repo.getAndIncrementNextCategoryColorIndex(3) }
        assertEquals(listOf(0, 1, 2, 0, 1), results)
    }

    // @spec LS-BE-010
    @Test fun `updating a category preserves sortOrder position not insertion order`() = runTest {
        val repo = FakeTrackrRepository()
        repo.setCategories(makeCategory("c1", 1), makeCategory("c2", 2), makeCategory("c3", 3))
        repo.saveCategory(makeCategory("c2", 2).copy(name = "updated"))
        val result = repo.getCategories().first()
        assertEquals(listOf("c1", "c2", "c3"), result.map { it.id })
    }

    // @spec DM-DATA-028
    @Test fun `saveCategory throws when nesting a MetaCategory that already has SubCategory children`() = runTest {
        val repo = FakeTrackrRepository()
        val parent = makeCategory("parent")
        val newParent = makeCategory("newParent")
        val child = makeSubCategory("child", parent = parent)
        repo.setCategories(parent, newParent, child)
        try {
            repo.saveCategory(makeSubCategory("parent", parent = newParent))
            fail("Expected CategoryHasChildrenException")
        } catch (e: CategoryHasChildrenException) {
            // expected
        }
    }

    // @spec DM-DATA-028
    @Test fun `saveCategory succeeds when nesting a category that has no children`() = runTest {
        val repo = FakeTrackrRepository()
        val parent = makeCategory("parent")
        val target = makeCategory("target")
        repo.setCategories(parent, target)
        repo.saveCategory(makeSubCategory("target", parent = parent))
        val result = repo.getCategories().first()
        assertEquals(1, result.filterIsInstance<Category.SubCategory>().size)
    }

    // @spec DM-DATA-028
    @Test fun `saveCategory with migrateEvents throws when nesting a category that has children`() = runTest {
        val repo = FakeTrackrRepository()
        val parent = makeCategory("parent")
        val newParent = makeCategory("newParent")
        val child = makeSubCategory("child", parent = parent)
        repo.setCategories(parent, newParent, child)
        try {
            repo.saveCategory(
                makeSubCategory("parent", parent = newParent),
                migrateEvents = true,
            )
            fail("Expected CategoryHasChildrenException")
        } catch (e: CategoryHasChildrenException) {
            // expected
        }
    }

    // @spec CAT-UI-080
    @Test fun `saveCategory reindexes only the destination sibling group leaving other categories untouched`() = runTest {
        val repo = FakeTrackrRepository()
        val groupA = makeCategory("groupA", sortOrder = 0)
        val a1 = makeSubCategory("a1", parent = groupA, sortOrder = 5)
        val a2 = makeSubCategory("a2", parent = groupA, sortOrder = 9)
        val groupB = makeCategory("groupB", sortOrder = 1)
        repo.setCategories(groupA, a1, a2, groupB)
        // Reorder a1/a2 within groupA — groupB's own sortOrder must be unaffected.
        repo.saveCategory(a2, orderedSiblingIds = listOf("a2", "a1"))
        val cats = repo.getCategories().first().associateBy { it.id }
        assertEquals(0, (cats["a2"] as Category.SubCategory).sortOrder)
        assertEquals(1, (cats["a1"] as Category.SubCategory).sortOrder)
        assertEquals(1, (cats["groupB"] as Category.MetaCategory).sortOrder)
    }

    // @spec CAT-UI-080
    @Test fun `saveCategory reparents a SubCategory to a new MetaCategory and reindexes the destination group`() = runTest {
        val repo = FakeTrackrRepository()
        val oldParent = makeCategory("oldParent")
        val newParent = makeCategory("newParent")
        val existingChild = makeSubCategory("existing", parent = newParent, sortOrder = 0)
        val moved = makeSubCategory("moved", parent = oldParent, sortOrder = 0)
        repo.setCategories(oldParent, newParent, existingChild, moved)
        repo.saveCategory(moved.copy(parent = newParent), orderedSiblingIds = listOf("existing", "moved"))
        val cats = repo.getCategories().first().associateBy { it.id }
        val movedResult = cats["moved"] as Category.SubCategory
        assertEquals("newParent", movedResult.parent.id)
        assertEquals(1, movedResult.sortOrder)
        assertEquals(0, (cats["existing"] as Category.SubCategory).sortOrder)
    }

    // @spec CAT-UI-080, CAT-UI-081
    @Test fun `saveCategory reindexes siblings and converts the category's own events`() = runTest {
        val repo = FakeTrackrRepository()
        val oldParent = makeCategory("oldParent")
        val newParent = makeCategory("newParent").copy(valueType = ValueType.Text)
        val moved = makeSubCategory("moved", parent = oldParent, sortOrder = 0)  // inherits None -> Text
        repo.setCategories(oldParent, newParent, moved)
        val anchor = Instant.parse("2024-01-15T12:00:00Z")
        repo.setEvents(Event("e1", "moved", anchor, null, null, emptyList(), anchor))
        repo.saveCategory(
            moved.copy(parent = newParent),
            migrateEvents = true,
            orderedSiblingIds = listOf("moved"),
        )
        val movedResult = repo.getCategoryById("moved").first() as Category.SubCategory
        assertEquals("newParent", movedResult.parent.id)
        assertEquals(0, movedResult.sortOrder)
    }

    // @spec CAT-UI-080, CAT-UI-083
    @Test fun `saveCategory reindexes the destination group's live members, not the stale hint`() = runTest {
        val repo = FakeTrackrRepository()
        // Snapshot the top-level order [m1, m2, m3], then a concurrent create adds m4 at the
        // top (sortOrder min-1, per CAT-UI-041) before the drop of m3-to-front is applied.
        val m1 = makeCategory("m1", sortOrder = 0)
        val m2 = makeCategory("m2", sortOrder = 1)
        val m3 = makeCategory("m3", sortOrder = 2)
        val m4 = makeCategory("m4", sortOrder = -1)
        repo.setCategories(m1, m2, m3, m4)
        // The widget's stale snapshot never saw m4.
        repo.saveCategory(m3, orderedSiblingIds = listOf("m3", "m1", "m2"))
        val cats = repo.getCategories().first()
        // m4 (unknown to the hint, currently ahead of all) stays at the front; the user's
        // arranged [m3, m1, m2] follows it — m4 is not stranded at a colliding sortOrder.
        assertEquals(listOf("m4", "m3", "m1", "m2"), cats.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), cats.map { it.sortOrder })
    }

    // @spec CAT-UI-080, CAT-UI-083
    @Test fun `saveCategory drops a hint id that is no longer a member of the destination group`() = runTest {
        val repo = FakeTrackrRepository()
        val parent = makeCategory("parent", sortOrder = 0)
        val a1 = makeSubCategory("a1", parent = parent, sortOrder = 0)
        val a3 = makeSubCategory("a3", parent = parent, sortOrder = 2)
        // a2 was in the snapshot but got deleted/reparented away before the drop applied.
        repo.setCategories(parent, a1, a3)
        repo.saveCategory(a3, orderedSiblingIds = listOf("a3", "a2", "a1"))
        val cats = repo.getCategories().first().associateBy { it.id }
        assertEquals(0, (cats["a3"] as Category.SubCategory).sortOrder)
        assertEquals(1, (cats["a1"] as Category.SubCategory).sortOrder)
    }

    // @spec CAT-UI-001
    @Test fun `getCategories sorts SubCategories after parent even when SubCategory sortOrder is lower`() = runTest {
        val repo = FakeTrackrRepository()
        val parent = makeCategory("parent", sortOrder = 10)
        val child = makeSubCategory("child", parent = parent, sortOrder = 1)
        val other = makeCategory("other", sortOrder = 5)
        repo.setCategories(parent, child, other)
        val result = repo.getCategories().first()
        assertEquals(listOf("other", "parent", "child"), result.map { it.id })
    }

    // @spec CAT-UI-006
    @Test fun `deleteCategory on MetaCategory promotes SubCategories and deletes parent events only`() = runTest {
        val repo = FakeTrackrRepository()
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent = parent)
        repo.setCategories(parent, child)
        val anchor = Instant.parse("2024-01-15T12:00:00Z")
        repo.setEvents(
            Event("e_parent", "parent", anchor, null, null, emptyList(), anchor),
            Event("e_child", "child", anchor, null, null, emptyList(), anchor),
        )
        repo.deleteCategory("parent")
        val cats = repo.getCategories().first()
        assertFalse("parent should be deleted", cats.any { it.id == "parent" })
        assertTrue("child should be promoted to MetaCategory", cats.any { it.id == "child" && it is Category.MetaCategory })
        assertNotNull("child's event should survive", repo.getEventById("e_child").first())
        assertNull("parent's event should be deleted", repo.getEventById("e_parent").first())
    }

    // @spec DM-PROC-022
    @Test fun `getCategories surfaces orphaned SubCategory as MetaCategory when parent is deleted`() = runTest {
        val repo = FakeTrackrRepository()
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent = parent)
        repo.setCategories(parent, child)
        repo.deleteCategory("parent")
        val result = repo.getCategories().first()
        assertEquals(1, result.size)
        assertTrue("orphaned SubCategory should surface as MetaCategory", result[0] is Category.MetaCategory)
        assertEquals("child", result[0].id)
    }

    // @spec DM-PROC-022
    @Test fun `getCategories uses null-field fallbacks when surfacing orphaned SubCategory`() = runTest {
        val repo = FakeTrackrRepository()
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent = parent)  // emoji=null, color=null, valueType=null
        repo.setCategories(parent, child)
        repo.deleteCategory("parent")
        val result = repo.getCategories().first()
        val meta = result[0] as Category.MetaCategory
        assertEquals("orphaned SubCategory emoji fallback", parent.emoji, meta.emoji)
        assertEquals("orphaned SubCategory color fallback", parent.color, meta.color)
        assertEquals("orphaned SubCategory valueType fallback", parent.valueType, meta.valueType)
    }

    // @spec REM-DATA-008
    @Test fun `saveCategory ignores the passed nextFireAt and preserves the store's current value`() = runTest {
        val repo = FakeTrackrRepository()
        val category = makeCategory("cat1")
        repo.setCategories(category)
        val armedAt = Instant.parse("2024-01-15T08:00:00Z")
        repo.setReminders(makeReminder("cat1", nextFireAt = armedAt))
        // Simulate onAlarmFired having already advanced nextFireAt while the edit screen was open.
        val staleFromScreen = Instant.parse("2024-01-15T20:00:00Z")
        repo.saveCategory(category, makeReminder("cat1", nextFireAt = staleFromScreen))
        val result = repo.getReminderForCategory("cat1").first()
        assertEquals(armedAt, result!!.nextFireAt)
    }

    // @spec REM-DATA-006
    @Test fun `saveCategory without a reminder leaves the stored one untouched`() = runTest {
        val repo = FakeTrackrRepository()
        val category = makeCategory("cat1")
        repo.setCategories(category)
        val stored = makeReminder("cat1")
        repo.setReminders(stored)
        repo.saveCategory(category.copy(name = "renamed"))
        assertEquals(stored, repo.getReminderForCategory("cat1").first())
    }

    // @spec REM-DATA-006
    @Test fun `saveCategory without a reminder writes no row for a category that has none`() = runTest {
        val repo = FakeTrackrRepository()
        val category = makeCategory("cat1")
        repo.setCategories(category)
        repo.saveCategory(category.copy(name = "renamed"))
        assertNull(repo.getReminderForCategory("cat1").first())
    }

    // @spec REM-DATA-007
    @Test fun `getAllEnabledRemindersOnce returns only enabled reminders`() = runTest {
        val repo = FakeTrackrRepository()
        repo.setReminders(
            makeReminder("enabled1", enabled = true),
            makeReminder("disabled1", enabled = false),
        )
        val result = repo.getAllEnabledRemindersOnce()
        assertEquals(listOf("enabled1"), result.map { it.categoryId })
    }

    // The double has to model REM-DATA-001's CASCADE, or code that assumes a reminder cannot
    // outlive its category passes here while failing against the real database.
    // @spec REM-DATA-001
    @Test fun `deleting a category deletes its reminder`() = runTest {
        val repo = FakeTrackrRepository()
        repo.setCategories(makeCategory("cat1"))
        repo.setReminders(makeReminder("cat1"))
        repo.deleteCategory("cat1")
        assertNull(repo.getReminderForCategory("cat1").first())
        assertTrue(repo.getAllEnabledRemindersOnce().isEmpty())
    }

    // Promotion is a reparent, not a delete, so the child keeps its own reminder.
    // @spec REM-DATA-001
    @Test fun `deleting a MetaCategory keeps the reminders of the SubCategories it promotes`() = runTest {
        val repo = FakeTrackrRepository()
        val parent = makeCategory("parent")
        repo.setCategories(parent, makeSubCategory("child", parent))
        repo.setReminders(makeReminder("parent"), makeReminder("child"))
        repo.deleteCategory("parent")
        assertNull(repo.getReminderForCategory("parent").first())
        assertNotNull(repo.getReminderForCategory("child").first())
    }

    // One collector held open across the write: re-subscribing would re-read and mask the
    // failure this pins, which is the answer going stale while someone is watching.
    // @spec REM-DATA-009
    @Test fun `hasEnabledReminder re-emits when a reminder is enabled without any category change`() = runTest {
        val repo = FakeTrackrRepository()
        val category = makeCategory("cat1")
        repo.setCategories(category)
        repo.hasEnabledReminder().test {
            assertFalse(awaitItem())
            repo.saveCategory(category, makeReminder("cat1"))
            assertTrue(awaitItem())
        }
    }

    // Held open across the write for the same reason: a re-subscribe would re-read and hide a
    // stream that had gone stale under a collector.
    // @spec REM-DATA-011
    @Test fun `getReminderForCategory re-emits when the stored reminder changes`() = runTest {
        val repo = FakeTrackrRepository()
        val category = makeCategory("cat1")
        repo.setCategories(category)
        repo.getReminderForCategory("cat1").test {
            assertNull(awaitItem())
            repo.saveCategory(category, makeReminder("cat1"))
            assertEquals("cat1", awaitItem()!!.categoryId)
        }
    }

    // @spec REM-DATA-009
    @Test fun `hasEnabledReminder ignores a disabled reminder`() = runTest {
        val repo = FakeTrackrRepository()
        repo.setReminders(makeReminder("cat1", enabled = false))
        assertFalse(repo.hasEnabledReminder().first())
    }

    // @spec REM-DATA-009, REM-DATA-001
    @Test fun `hasEnabledReminder goes false when the last category holding one is deleted`() = runTest {
        val repo = FakeTrackrRepository()
        repo.setCategories(makeCategory("cat1"))
        repo.setReminders(makeReminder("cat1"))
        repo.hasEnabledReminder().test {
            assertTrue(awaitItem())
            repo.deleteCategory("cat1")
            assertFalse(awaitItem())
        }
    }

    private fun makeSubCategory(id: String, parent: Category.MetaCategory) = Category.SubCategory(
        id = id, name = id, emoji = null, color = null,
        valueType = null, defaultValue = null, allowEmptyText = true, sortOrder = 0, parent = parent,
    )

    private fun makeReminder(
        categoryId: String,
        enabled: Boolean = true,
        nextFireAt: Instant? = null,
    ) = Reminder(
        categoryId = categoryId,
        enabled = enabled,
        mode = ReminderMode.FIXED,
        times = listOf(java.time.LocalTime.of(8, 0)),
        windowStart = java.time.LocalTime.MIDNIGHT,
        windowEnd = java.time.LocalTime.MIDNIGHT,
        occurrencesPerDay = 1,
        daysActive = DayOfWeek.entries.toSet(),
        showCategoryInNotification = false,
        nextFireAt = nextFireAt,
    )

    private fun makeCategory(id: String, sortOrder: Int = 0) = Category.MetaCategory(
        id = id, name = id, emoji = "📌", color = 0xFFE53935L,
        valueType = ValueType.None, defaultValue = null, allowEmptyText = true, sortOrder = sortOrder,
    )

    private fun makeSubCategory(id: String, parent: Category.MetaCategory, sortOrder: Int = 0) =
        Category.SubCategory(
            id = id, name = id, emoji = null, color = null, valueType = null,
            defaultValue = null, allowEmptyText = true, sortOrder = sortOrder, parent = parent,
        )
}
