package com.trackr.app.ui.home

import app.cash.turbine.test
import com.trackr.app.FakeTrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
import com.trackr.app.domain.ErrorKind
import com.trackr.app.domain.EventValue
import com.trackr.app.domain.ValueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeTrackrRepository
    private lateinit var vm: HomeViewModel

    companion object {
        val anchor: Instant = Instant.parse("2024-01-15T12:00:00Z")
    }

    private fun makeMeta(id: String, name: String = id) = Category.MetaCategory(
        id = id, name = name, emoji = "📌", color = 0xFFE53935L,
        valueType = ValueType.None, unit = null, allowEmptyText = true, sortOrder = 0,
    )

    private fun makeCategory(id: String, name: String = id) = makeMeta(id, name)

    private fun makeSub(id: String, parent: Category.MetaCategory) = Category.SubCategory(
        id = id, name = id, emoji = null, color = null, valueType = null,
        unit = null, allowEmptyText = true, sortOrder = 0, parent = parent,
    )

    private fun makeEvent(
        id: String,
        categoryId: String,
        timestamp: Instant = anchor,
        createdAt: Instant = anchor,
    ) = Event(id = id, categoryId = categoryId, timestamp = timestamp, value = null,
        notes = null, imagePaths = emptyList(), createdAt = createdAt)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTrackrRepository()
        vm = HomeViewModel(repo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // @spec EL-UI-001
    @Test fun `events grouped by local date most recent first`() = runTest {
        val today = anchor
        val yesterday = anchor.minusSeconds(86400)
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        repo.setEvents(
            makeEvent("e1", "c1", timestamp = today),
            makeEvent("e2", "c1", timestamp = yesterday),
        )
        vm.dayGroups.test {
            val groups = awaitItem()
            assertEquals(2, groups.size)
            val todayDate = today.atZone(ZoneId.systemDefault()).toLocalDate()
            assertEquals(todayDate, groups[0].date)
        }
    }

    // @spec EL-UI-011
    @Test fun `TopLevel filter shows events from MetaCategory and its SubCategories`() = runTest {
        val meta = makeMeta("m1")
        val sub = makeSub("s1", parent = meta)
        val other = makeMeta("m2")
        repo.setCategories(meta, sub, other)
        repo.setEvents(
            makeEvent("e_meta", "m1"),
            makeEvent("e_sub", "s1"),
            makeEvent("e_other", "m2"),
        )
        vm.setFilter(ActiveFilter.TopLevel(meta))
        vm.dayGroups.test {
            val ids = awaitItem().flatMap { it.events }.filterIsInstance<DayEntry.Entry>().map { it.event.id }
            assertTrue(ids.contains("e_meta"))
            assertTrue(ids.contains("e_sub"))
            assertTrue(ids.none { it == "e_other" })
        }
    }

    // @spec EL-UI-011
    @Test fun `Sub filter shows only SubCategory events`() = runTest {
        val meta = makeMeta("m1")
        val sub = makeSub("s1", parent = meta)
        repo.setCategories(meta, sub)
        repo.setEvents(makeEvent("e_meta", "m1"), makeEvent("e_sub", "s1"))
        vm.setFilter(ActiveFilter.Sub(meta, sub))
        vm.dayGroups.test {
            val ids = awaitItem().flatMap { it.events }.filterIsInstance<DayEntry.Entry>().map { it.event.id }
            assertEquals(listOf("e_sub"), ids)
        }
    }

    // @spec EL-UI-011
    @Test fun `active TopLevel filter shows only events from that MetaCategory and its children`() = runTest {
        val cat1 = makeCategory("c1")
        val cat2 = makeCategory("c2")
        repo.setCategories(cat1, cat2)
        repo.setEvents(makeEvent("e1", "c1"), makeEvent("e2", "c2"))
        vm.setFilter(ActiveFilter.TopLevel(cat1))
        vm.dayGroups.test {
            val groups = awaitItem()
            val allEntries = groups.flatMap { it.events }.filterIsInstance<DayEntry.Entry>()
            assertTrue(allEntries.all { it.event.categoryId == "c1" })
        }
    }

    // @spec EL-UI-012
    @Test fun `clearing filter shows all events`() = runTest {
        val cat1 = makeCategory("c1")
        val cat2 = makeCategory("c2")
        repo.setCategories(cat1, cat2)
        repo.setEvents(makeEvent("e1", "c1"), makeEvent("e2", "c2"))
        vm.setFilter(ActiveFilter.TopLevel(cat1))
        vm.setFilter(ActiveFilter.All)
        assertEquals(ActiveFilter.All, vm.activeFilter.value)
        val allEntries = vm.dayGroups.value.flatMap { it.events }.filterIsInstance<DayEntry.Entry>()
        assertEquals(listOf("e1", "e2"), allEntries.map { it.event.id })
    }

    // @spec EL-UI-013b
    @Test fun `TopLevel filter cleared to All when MetaCategory is deleted`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        vm.setFilter(ActiveFilter.TopLevel(cat))
        repo.deleteCategory("c1")
        vm.activeFilter.test { assertEquals(ActiveFilter.All, awaitItem()) }
    }

    // @spec EL-UI-013b
    @Test fun `Sub filter cleared to All when SubCategory is deleted`() = runTest {
        val meta = makeMeta("m1")
        val sub = makeSub("s1", parent = meta)
        repo.setCategories(meta, sub)
        vm.setFilter(ActiveFilter.Sub(meta, sub))
        repo.deleteCategory("s1")
        vm.activeFilter.test { assertEquals(ActiveFilter.All, awaitItem()) }
    }

    // @spec EL-UI-013b
    @Test fun `Sub filter promoted to TopLevel when parent MetaCategory is deleted`() = runTest {
        val meta = makeMeta("m1")
        val sub = makeSub("s1", parent = meta)
        repo.setCategories(meta, sub)
        vm.setFilter(ActiveFilter.Sub(meta, sub))
        // Simulate UI flow: sub promoted to MetaCategory first, then parent deleted
        repo.saveCategory(makeMeta("s1"))
        repo.deleteCategory("m1")
        vm.activeFilter.test {
            val filter = awaitItem()
            assertTrue(filter is ActiveFilter.TopLevel && filter.category.id == "s1")
        }
    }

    // @spec EL-UI-017
    @Test fun `pre-filter position recorded on first filter apply`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        vm.setFilter(ActiveFilter.TopLevel(cat))
        repo.setEvents(makeEvent("e1", "c1"))
        vm.setFilter(ActiveFilter.All)
        vm.setFilter(ActiveFilter.TopLevel(cat))
        vm.preFilterTopDay.test {
            val recorded = awaitItem()
            assertTrue(recorded != null || vm.dayGroups.value.isEmpty())
        }
    }

    // @spec EL-UI-018
    @Test fun `switching between filters preserves pre-filter record`() = runTest {
        val cat1 = makeCategory("c1")
        val cat2 = makeCategory("c2")
        repo.setCategories(cat1, cat2)
        repo.setEvents(makeEvent("e1", "c1", timestamp = anchor), makeEvent("e2", "c2", timestamp = anchor))
        vm.setFilter(ActiveFilter.TopLevel(cat1))
        val recordedAfterFirst = vm.preFilterTopDay.value
        vm.setFilter(ActiveFilter.TopLevel(cat2))
        assertEquals("switching filters must not update pre-filter record", recordedAfterFirst, vm.preFilterTopDay.value)
    }

    // @spec EL-UI-019
    @Test fun `manual scroll discards pre-filter record`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        repo.setEvents(makeEvent("e1", "c1"))
        vm.setFilter(ActiveFilter.TopLevel(cat))
        vm.onUserScrolled()
        assertNull(vm.preFilterTopDay.value)
    }

    // @spec EL-UI-019b
    @Test fun `clearing filter restores pre-filter position when record not discarded`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        repo.setEvents(makeEvent("e1", "c1"))
        vm.setFilter(ActiveFilter.TopLevel(cat))
        vm.setFilter(ActiveFilter.All)
        assertNull(vm.preFilterTopDay.value)
    }

    // @spec EL-UI-020
    @Test fun `swipe delete removes event from dayGroups immediately`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        val event = makeEvent("e1", "c1")
        repo.setEvents(event)
        vm.swipeDelete(event)
        val entries = vm.dayGroups.value.flatMap { it.events }.filterIsInstance<DayEntry.Entry>()
        assertTrue(entries.none { it.event.id == "e1" })
    }

    // @spec EL-UI-020
    @Test fun `swipe delete replaces row with undo placeholder`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        val event = makeEvent("e1", "c1")
        repo.setEvents(event)
        vm.swipeDelete(event)
        val placeholders = vm.dayGroups.value.flatMap { it.events }.filterIsInstance<DayEntry.UndoPlaceholder>()
        assertTrue(placeholders.any { it.event.id == "e1" })
    }

    // @spec EL-UI-021
    @Test fun `undo placeholder appears at the position of the deleted event`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        val e1 = makeEvent("e1", "c1", timestamp = anchor)
        val e2 = makeEvent("e2", "c1", timestamp = anchor.minusSeconds(10))
        val e3 = makeEvent("e3", "c1", timestamp = anchor.minusSeconds(20))
        repo.setEvents(e1, e2, e3)
        vm.swipeDelete(e2)
        val entries = vm.dayGroups.value.flatMap { it.events }
        assertTrue(entries[0] is DayEntry.Entry && (entries[0] as DayEntry.Entry).event.id == "e1")
        assertTrue(entries[1] is DayEntry.UndoPlaceholder && (entries[1] as DayEntry.UndoPlaceholder).event.id == "e2")
        assertTrue(entries[2] is DayEntry.Entry && (entries[2] as DayEntry.Entry).event.id == "e3")
    }

    // @spec EL-UI-022
    @Test fun `undo delete restores event and removes placeholder`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        val event = makeEvent("e1", "c1")
        repo.setEvents(event)
        vm.swipeDelete(event)
        vm.undoDelete()
        val entries = vm.dayGroups.value.flatMap { it.events }.filterIsInstance<DayEntry.Entry>()
        assertTrue(entries.any { it.event.id == "e1" })
        val placeholders = vm.dayGroups.value.flatMap { it.events }.filterIsInstance<DayEntry.UndoPlaceholder>()
        assertTrue(placeholders.isEmpty())
    }

    // @spec EL-UI-022
    @Test fun `undo restores event to its original position`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        val e1 = makeEvent("e1", "c1", timestamp = anchor)
        val e2 = makeEvent("e2", "c1", timestamp = anchor.minusSeconds(10))
        val e3 = makeEvent("e3", "c1", timestamp = anchor.minusSeconds(20))
        repo.setEvents(e1, e2, e3)
        vm.swipeDelete(e2)
        vm.undoDelete()
        val entries = vm.dayGroups.value.flatMap { it.events }.filterIsInstance<DayEntry.Entry>()
        assertEquals(listOf("e1", "e2", "e3"), entries.map { it.event.id })
    }

    // @spec EL-UI-023
    @Test fun `saving a new event clears undo placeholder`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        val event = makeEvent("e1", "c1")
        repo.setEvents(event)
        vm.swipeDelete(event)
        repo.saveEvent(makeEvent("e2", "c1", timestamp = anchor.minusSeconds(60)))
        val placeholders = vm.dayGroups.value.flatMap { it.events }.filterIsInstance<DayEntry.UndoPlaceholder>()
        assertTrue(placeholders.isEmpty())
        assertNull(vm.pendingDelete.value)
    }

    // @spec EL-UI-023
    @Test fun `swipe deleting another event clears first undo placeholder`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        val e1 = makeEvent("e1", "c1", timestamp = anchor)
        val e2 = makeEvent("e2", "c1", timestamp = anchor.minusSeconds(10))
        repo.setEvents(e1, e2)
        vm.swipeDelete(e1)
        vm.swipeDelete(e2)
        val placeholders = vm.dayGroups.value.flatMap { it.events }.filterIsInstance<DayEntry.UndoPlaceholder>()
        assertTrue("Only one placeholder should remain", placeholders.size <= 1)
        assertTrue("Remaining placeholder should be e2", placeholders.all { it.event.id == "e2" })
    }

    // @spec EL-UI-023b
    @Test fun `undo placeholder dismissed when its category is deleted`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        val event = makeEvent("e1", "c1")
        repo.setEvents(event)
        vm.swipeDelete(event)
        repo.deleteCategory("c1")
        val placeholders = vm.dayGroups.value.flatMap { it.events }.filterIsInstance<DayEntry.UndoPlaceholder>()
        assertTrue("Placeholder should be dismissed when category deleted", placeholders.isEmpty())
        assertNull(vm.pendingDelete.value)
    }

    // @spec EL-UI-002
    @Test fun `DayEntry Entry carries the matching category`() = runTest {
        val cat = makeCategory("c1", name = "Steps")
        repo.setCategories(cat)
        repo.setEvents(makeEvent("e1", "c1"))
        vm.dayGroups.test {
            val groups = awaitItem()
            val entry = groups.flatMap { it.events }.filterIsInstance<DayEntry.Entry>().first()
            assertEquals(cat, entry.category)
        }
    }

    // @spec EL-UI-002
    @Test fun `DayEntry Entry has null category for orphaned event`() = runTest {
        repo.setEvents(makeEvent("e1", "c1"))
        vm.dayGroups.test {
            val groups = awaitItem()
            val entry = groups.flatMap { it.events }.filterIsInstance<DayEntry.Entry>().first()
            assertNull(entry.category)
        }
    }

    // @spec EL-UI-061
    @Test fun `DayEntry Entry hasMismatch is true when event value does not match category type`() = runTest {
        val cat = Category.MetaCategory(
            id = "c1", name = "Scale Cat", emoji = "📌", color = 0xFFE53935L,
            valueType = ValueType.Scale, unit = null, allowEmptyText = true, sortOrder = 0,
        )
        repo.setCategories(cat)
        val event = Event(
            id = "e1", categoryId = "c1", timestamp = anchor,
            value = EventValue.TextValue("hello"),
            notes = null, imagePaths = emptyList(), createdAt = anchor,
        )
        repo.setEvents(event)
        vm.dayGroups.test {
            val entry = awaitItem().flatMap { it.events }.filterIsInstance<DayEntry.Entry>().first()
            assertTrue(entry.hasMismatch)
        }
    }

    // @spec EL-UI-061
    @Test fun `DayEntry Entry hasMismatch is false when value matches category type`() = runTest {
        val cat = Category.MetaCategory(
            id = "c1", name = "Scale Cat", emoji = "📌", color = 0xFFE53935L,
            valueType = ValueType.Scale, unit = null, allowEmptyText = true, sortOrder = 0,
        )
        repo.setCategories(cat)
        val event = Event(
            id = "e1", categoryId = "c1", timestamp = anchor,
            value = EventValue.Scale(7),
            notes = null, imagePaths = emptyList(), createdAt = anchor,
        )
        repo.setEvents(event)
        vm.dayGroups.test {
            val entry = awaitItem().flatMap { it.events }.filterIsInstance<DayEntry.Entry>().first()
            assertFalse(entry.hasMismatch)
        }
    }

    // @spec EL-UI-061
    @Test fun `DayEntry Entry hasMismatch is false when ErrorValue inferredType matches Unknown category`() = runTest {
        val cat = Category.MetaCategory(
            id = "c1", name = "Future Cat", emoji = "📌", color = 0xFFE53935L,
            valueType = ValueType.Unknown("future_type"), unit = null, allowEmptyText = true, sortOrder = 0,
        )
        repo.setCategories(cat)
        val event = Event(
            id = "e1", categoryId = "c1", timestamp = anchor,
            value = EventValue.ErrorValue(ErrorKind.UNRECOGNIZED_TYPE, """{"type":"future_type"}""", inferredType = "future_type"),
            notes = null, imagePaths = emptyList(), createdAt = anchor,
        )
        repo.setEvents(event)
        vm.dayGroups.test {
            val entry = awaitItem().flatMap { it.events }.filterIsInstance<DayEntry.Entry>().first()
            assertFalse(entry.hasMismatch)
        }
    }

    // @spec EL-UI-061
    @Test fun `DayEntry Entry hasMismatch is false for orphaned event with no category`() = runTest {
        val event = Event(
            id = "e1", categoryId = "nonexistent", timestamp = anchor,
            value = EventValue.Scale(7),
            notes = null, imagePaths = emptyList(), createdAt = anchor,
        )
        repo.setEvents(event)
        vm.dayGroups.test {
            val entry = awaitItem().flatMap { it.events }.filterIsInstance<DayEntry.Entry>().first()
            assertFalse(entry.hasMismatch)
        }
    }
}
