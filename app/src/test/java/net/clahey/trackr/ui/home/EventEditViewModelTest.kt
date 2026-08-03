package net.clahey.trackr.ui.home

import androidx.lifecycle.SavedStateHandle
import net.clahey.trackr.FakeImageStore
import net.clahey.trackr.FakeTrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.ErrorKind
import net.clahey.trackr.domain.EventValue
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.ui.SaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import net.clahey.trackr.domain.Event
import net.clahey.trackr.ui.components.ValueUIState

@OptIn(ExperimentalCoroutinesApi::class)
class EventEditViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeTrackrRepository
    private lateinit var imageStore: FakeImageStore
    private lateinit var vm: EventEditViewModel

    companion object {
        val anchor: Instant = Instant.parse("2024-01-15T12:00:00Z")
    }

    private fun makeCategory(
        id: String,
        valueType: ValueType = ValueType.Scale,
    ) = Category.MetaCategory(
        id = id, name = id, emoji = "📌", color = 0xFFE53935L,
        valueType = valueType, defaultValue = null, allowEmptyText = true, sortOrder = 0,
    )

    private fun makeEvent(
        id: String,
        categoryId: String,
        value: EventValue? = null,
        notes: String? = null,
        imagePaths: List<String> = emptyList(),
        timestamp: Instant = anchor,
    ) = Event(
        id = id, categoryId = categoryId, timestamp = timestamp,
        value = value, notes = notes, imagePaths = imagePaths,
        createdAt = anchor,
    )

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTrackrRepository()
        imageStore = FakeImageStore()
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun makeVm(eventId: String, filterCategoryId: String? = null) = EventEditViewModel(
        repo, imageStore,
        SavedStateHandle(mapOf("eventId" to eventId, "filterCategoryId" to filterCategoryId)),
    )

    // @spec EL-UI-040
    @Test fun `form fields initialized from loaded event`() = runTest {
        val event = makeEvent("e1", "c1", notes = "my note", timestamp = anchor)
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertEquals(anchor, vm.timestamp.value)
        assertEquals("my note", vm.notes.value)
    }

    // @spec EL-UI-040
    @Test fun `imagePaths initialized from loaded event`() = runTest {
        val event = makeEvent("e1", "c1", imagePaths = listOf("/img/a.jpg", "/img/b.jpg"))
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertEquals(listOf("/img/a.jpg", "/img/b.jpg"), vm.imagePaths.value)
    }

    // @spec EL-UI-042
    @Test fun `requestDelete sets pendingDelete true`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        vm.requestDelete()
        assertTrue(vm.pendingDelete.value)
    }

    // @spec EL-UI-042
    @Test fun `cancelDelete sets pendingDelete false`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        vm.requestDelete()
        vm.cancelDelete()
        assertFalse(vm.pendingDelete.value)
    }

    // @spec EL-NAV-006
    @Test fun `confirmDelete deletes event from repository and signals completion`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        vm.requestDelete()
        vm.confirmDelete()
        assertTrue(vm.deleteComplete.value)
        assertNull(repo.getEventById("e1").first())
    }

    // @spec EL-NAV-005
    @Test fun `save writes updated fields to repository`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", notes = "old note"))
        vm = makeVm("e1")
        vm.setNotes("new note")
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.Success)
        val saved = repo.getEventById("e1").first()
        assertEquals("new note", saved?.notes)
    }

    // @spec EL-UI-057
    @Test fun `save blocked when Boolean value is unset`() = runTest {
        repo.setCategories(makeCategory("c1", valueType = ValueType.Boolean))
        repo.saveEvent(makeEvent("e1", "c1", value = null))
        vm = makeVm("e1")
        // loaded as Bool(null) via EL-UI-067
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.ValidationError)
    }

    // @spec EL-UI-057
    @Test fun `save blocked when Number text is empty`() = runTest {
        repo.setCategories(makeCategory("c1", valueType = ValueType.Number))
        repo.saveEvent(makeEvent("e1", "c1", value = null))
        vm = makeVm("e1")
        // loaded as Number("", "") via EL-UI-067
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.ValidationError)
    }

    // @spec EL-UI-057
    @Test fun `save succeeds when Scale value is loaded`() = runTest {
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(makeEvent("e1", "c1", value = EventValue.Scale(7)))
        vm = makeVm("e1")
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.Success)
    }

    // @spec EL-UI-044
    @Test fun `addImage appends path to imagePaths`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        vm.addImage("/img/new.jpg")
        assertEquals(listOf("/img/new.jpg"), vm.imagePaths.value)
    }

    // @spec EL-UI-044
    @Test fun `removeImage removes path from imagePaths`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", imagePaths = listOf("/img/a.jpg", "/img/b.jpg")))
        vm = makeVm("e1")
        vm.removeImage("/img/a.jpg")
        assertEquals(listOf("/img/b.jpg"), vm.imagePaths.value)
    }

    // @spec EL-PROC-002
    @Test fun `cancel deletes newly captured images not in original event`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", imagePaths = listOf("/img/original.jpg")))
        vm = makeVm("e1")
        vm.addImage("/img/new.jpg")
        vm.cancel()
        assertTrue(imageStore.wasDeleted("/img/new.jpg"))
    }

    // @spec EL-PROC-002
    @Test fun `cancel does not delete images from the original event`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", imagePaths = listOf("/img/original.jpg")))
        vm = makeVm("e1")
        vm.cancel()
        assertFalse(imageStore.wasDeleted("/img/original.jpg"))
    }

    // @spec EL-UI-045
    @Test fun `navigateBack emits true when event not found`() = runTest {
        vm = makeVm("nonexistent")
        assertTrue(vm.navigateBack.value)
    }

    // @spec EL-UI-045
    @Test fun `navigateBack stays false when event is found`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        assertFalse(vm.navigateBack.value)
    }

    // @spec EL-UI-046
    @Test fun `category exposes the loaded category`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        assertEquals(cat, vm.category.value)
    }

    // @spec EL-UI-046
    @Test fun `category is null when event exists but its category does not`() = runTest {
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        assertNull(vm.category.value)
    }

    // @spec EL-UI-044a
    @Test fun `createImageFile creates a file tracked by imageStore`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        val path = vm.createImageFile()
        assertTrue(imageStore.allStoredPaths().contains(path))
    }

    // @spec EL-UI-062
    @Test fun `value initialized as matched UIState when stored value matches category type`() = runTest {
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(makeEvent("e1", "c1", value = EventValue.Scale(7)))
        vm = makeVm("e1")
        assertEquals(ValueUIState.Scale(7), vm.value.value)
    }

    // @spec EL-UI-062
    @Test fun `value initialized as Mismatched when stored value type does not match category type`() = runTest {
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(makeEvent("e1", "c1", value = EventValue.BooleanValue(true)))
        vm = makeVm("e1")
        assertTrue(vm.value.value is ValueUIState.Mismatched)
    }

    // @spec EL-UI-062
    @Test fun `value Mismatched carries the original stored EventValue`() = runTest {
        val storedValue = EventValue.BooleanValue(true)
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(makeEvent("e1", "c1", value = storedValue))
        vm = makeVm("e1")
        val mismatched = vm.value.value as ValueUIState.Mismatched
        assertEquals(storedValue, mismatched.originalValue)
    }

    // @spec EL-UI-067
    @Test fun `null stored value with non-None category type initializes to default editable state`() = runTest {
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(makeEvent("e1", "c1", value = null))
        vm = makeVm("e1")
        assertEquals(ValueUIState.Scale(5), vm.value.value)
    }

    // @spec EL-UI-043, EL-UI-062
    @Test fun `ErrorValue produces Mismatched with null editableState`() = runTest {
        val errorValue = EventValue.ErrorValue(ErrorKind.UNPARSABLE, "bad")
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(makeEvent("e1", "c1", value = errorValue))
        vm = makeVm("e1")
        val mismatched = vm.value.value as ValueUIState.Mismatched
        assertNull(mismatched.editableState)
    }

    // @spec EL-UI-044a
    @Test fun `cancelImage deletes the given file`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        val path = vm.createImageFile()
        vm.cancelImage(path)
        assertTrue(imageStore.wasDeleted(path))
    }

    // @spec EL-PROC-002
    @Test fun `cancel deletes a file created but not committed via createImageFile`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        val path = vm.createImageFile()
        vm.addImage(path)
        vm.cancel()
        assertTrue(imageStore.wasDeleted(path))
    }

    // @spec EL-NAV-008
    @Test fun `eventIds loads all events in order when no filter`() = runTest {
        repo.setCategories(makeCategory("c1"), makeCategory("c2"))
        repo.saveEvent(makeEvent("e1", "c1", timestamp = anchor.plusSeconds(2)))
        repo.saveEvent(makeEvent("e2", "c2", timestamp = anchor.plusSeconds(1)))
        repo.saveEvent(makeEvent("e3", "c1", timestamp = anchor))
        vm = makeVm("e1")
        assertEquals(listOf("e1", "e2", "e3"), vm.eventIds.first())
        assertEquals(0, vm.currentIndex.first())
    }

    // @spec EL-NAV-008
    @Test fun `eventIds filtered by category when filterCategoryId set`() = runTest {
        repo.setCategories(makeCategory("c1"), makeCategory("c2"))
        repo.saveEvent(makeEvent("e1", "c1", timestamp = anchor.plusSeconds(2)))
        repo.saveEvent(makeEvent("e2", "c2", timestamp = anchor.plusSeconds(1)))
        repo.saveEvent(makeEvent("e3", "c1", timestamp = anchor))
        vm = makeVm("e1", filterCategoryId = "c1")
        assertEquals(listOf("e1", "e3"), vm.eventIds.first())
        assertEquals(0, vm.currentIndex.first())
    }

    // @spec EL-NAV-008
    @Test fun `currentIndex reflects initial event position in list`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", timestamp = anchor.plusSeconds(1)))
        repo.saveEvent(makeEvent("e2", "c1", timestamp = anchor))
        vm = makeVm("e2")
        assertEquals(1, vm.currentIndex.first())
    }

    // @spec EL-NAV-009
    @Test fun `navigateToAdjacent 1 moves to older event and loads its data`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", notes = "newer", timestamp = anchor.plusSeconds(1)))
        repo.saveEvent(makeEvent("e2", "c1", notes = "older", timestamp = anchor))
        vm = makeVm("e1")
        vm.navigateToAdjacent(1)
        assertEquals(1, vm.currentIndex.first())
        assertEquals("older", vm.notes.value)
    }

    // @spec EL-NAV-009
    @Test fun `navigateToAdjacent -1 moves to newer event and loads its data`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", notes = "newer", timestamp = anchor.plusSeconds(1)))
        repo.saveEvent(makeEvent("e2", "c1", notes = "older", timestamp = anchor))
        vm = makeVm("e2")
        vm.navigateToAdjacent(-1)
        assertEquals(0, vm.currentIndex.first())
        assertEquals("newer", vm.notes.value)
    }

    // @spec EL-NAV-009
    @Test fun `navigateToAdjacent at oldest event is no-op`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", timestamp = anchor.plusSeconds(1)))
        repo.saveEvent(makeEvent("e2", "c1", timestamp = anchor))
        vm = makeVm("e2")
        vm.navigateToAdjacent(1)
        assertEquals(1, vm.currentIndex.first())
    }

    // @spec EL-NAV-009
    @Test fun `navigateToAdjacent at newest event is no-op`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", timestamp = anchor.plusSeconds(1)))
        repo.saveEvent(makeEvent("e2", "c1", timestamp = anchor))
        vm = makeVm("e1")
        vm.navigateToAdjacent(-1)
        assertEquals(0, vm.currentIndex.first())
    }

    // @spec EL-NAV-008
    @Test fun `eventIds includes events from subcategories when filtering by parent`() = runTest {
        val parent = makeCategory("parent")
        val child = Category.SubCategory(
            id = "child", name = "child", emoji = null, color = null,
            valueType = null, defaultValue = null, allowEmptyText = true,
            sortOrder = 0, parent = parent,
        )
        val other = makeCategory("other")
        repo.setCategories(parent, child, other)
        repo.saveEvent(makeEvent("e1", "parent", timestamp = anchor.plusSeconds(2)))
        repo.saveEvent(makeEvent("e2", "child", timestamp = anchor.plusSeconds(1)))
        repo.saveEvent(makeEvent("e3", "other", timestamp = anchor))
        vm = makeVm("e1", filterCategoryId = "parent")
        assertEquals(listOf("e1", "e2"), vm.eventIds.first())
    }

    // @spec EL-NAV-011
    @Test fun `navigation stays within filter scope`() = runTest {
        repo.setCategories(makeCategory("c1"), makeCategory("c2"))
        repo.saveEvent(makeEvent("e1", "c1", timestamp = anchor.plusSeconds(2)))
        repo.saveEvent(makeEvent("e2", "c2", timestamp = anchor.plusSeconds(1)))
        repo.saveEvent(makeEvent("e3", "c1", timestamp = anchor))
        vm = makeVm("e1", filterCategoryId = "c1")
        vm.navigateToAdjacent(1)
        assertEquals("e3", vm.eventIds.first()[vm.currentIndex.first()])
    }

    // @spec EL-NAV-012
    @Test fun `isDirty is false on load`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        assertFalse(vm.isDirty.first())
    }

    // @spec EL-NAV-012
    @Test fun `editing notes sets isDirty`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        vm.setNotes("changed")
        assertTrue(vm.isDirty.first())
    }

    // @spec EL-NAV-012
    @Test fun `setValue sets isDirty`() = runTest {
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        vm.setValue(ValueUIState.Scale(3))
        assertTrue(vm.isDirty.first())
    }

    // @spec EL-UI-040, EL-UI-043
    @Test fun `setTimestamp updates timestamp and sets isDirty`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        val newTimestamp = Instant.parse("2026-01-01T00:00:00Z")
        vm.setTimestamp(newTimestamp)
        assertEquals(newTimestamp, vm.timestamp.first())
        assertTrue(vm.isDirty.first())
    }

    // @spec EL-NAV-012
    @Test fun `addImage sets isDirty`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        vm.addImage("/img/new.jpg")
        assertTrue(vm.isDirty.first())
    }

    // @spec EL-NAV-012
    @Test fun `removeImage sets isDirty`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", imagePaths = listOf("/img/a.jpg")))
        vm = makeVm("e1")
        vm.removeImage("/img/a.jpg")
        assertTrue(vm.isDirty.first())
    }


    // @spec EL-NAV-012
    @Test fun `saveInPlace persists changes and clears isDirty without emitting SaveResult Success`() = runTest {
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(makeEvent("e1", "c1", notes = "old"))
        vm = makeVm("e1")
        vm.setNotes("new")
        vm.saveInPlace()
        val saved = repo.getEventById("e1").first()
        assertEquals("new", saved?.notes)
        assertFalse(vm.isDirty.first())
        assertTrue(vm.saveResult.value is SaveResult.Idle)
    }

    // @spec EL-NAV-012
    @Test fun `saveInPlace updates originalEvent so subsequent discard reverts to saved state`() = runTest {
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(makeEvent("e1", "c1", notes = "original"))
        vm = makeVm("e1")
        vm.setNotes("saved-in-place")
        vm.saveInPlace()
        // Now edit again and discard — should revert to "saved-in-place", not "original"
        vm.setNotes("second edit")
        vm.discardInPlace()
        assertEquals("saved-in-place", vm.notes.value)
    }

    // @spec EL-NAV-012
    @Test fun `discardInPlace reverts notes to original value and clears isDirty`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", notes = "original"))
        vm = makeVm("e1")
        vm.setNotes("edited")
        vm.discardInPlace()
        assertEquals("original", vm.notes.value)
        assertFalse(vm.isDirty.first())
    }

    // @spec EL-NAV-012
    @Test fun `discardInPlace deletes images added during edit session`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", imagePaths = listOf("/img/original.jpg")))
        vm = makeVm("e1")
        vm.addImage("/img/new.jpg")
        vm.discardInPlace()
        assertTrue(imageStore.wasDeleted("/img/new.jpg"))
        assertEquals(listOf("/img/original.jpg"), vm.imagePaths.value)
    }

    // @spec EL-NAV-012
    @Test fun `discardInPlace does not delete images that were part of the original event`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", imagePaths = listOf("/img/original.jpg")))
        vm = makeVm("e1")
        vm.discardInPlace()
        assertFalse(imageStore.wasDeleted("/img/original.jpg"))
    }

    // @spec EL-NAV-012
    @Test fun `scrollEnded shows dialog when dirty`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        vm.setNotes("changed")
        vm.scrollEnded()
        assertTrue(vm.showDiscardDialog.first())
    }

    // @spec EL-NAV-012
    @Test fun `scrollEnded does nothing when not dirty`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        vm.scrollEnded()
        assertFalse(vm.showDiscardDialog.first())
    }

    // @spec EL-NAV-012
    @Test fun `dismissDiscardDialog clears showDiscardDialog`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        vm.setNotes("changed")
        vm.scrollEnded()
        vm.dismissDiscardDialog()
        assertFalse(vm.showDiscardDialog.first())
    }

    // @spec EL-NAV-012
    @Test fun `saveInPlace clears showDiscardDialog`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        vm.setNotes("changed")
        vm.scrollEnded()
        vm.saveInPlace()
        assertFalse(vm.showDiscardDialog.first())
    }

    // @spec EL-NAV-012
    @Test fun `discardInPlace clears showDiscardDialog`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = makeVm("e1")
        vm.setNotes("changed")
        vm.scrollEnded()
        vm.discardInPlace()
        assertFalse(vm.showDiscardDialog.first())
    }

    // @spec EL-NAV-012
    @Test fun `pageSettled navigates when not dirty`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", notes = "newer", timestamp = anchor.plusSeconds(1)))
        repo.saveEvent(makeEvent("e2", "c1", notes = "older", timestamp = anchor))
        vm = makeVm("e1")
        vm.pageSettled(1)
        assertEquals("older", vm.notes.value)
    }

    // @spec EL-NAV-012
    @Test fun `pageSettled does not navigate when dirty`() = runTest {
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1", notes = "newer", timestamp = anchor.plusSeconds(1)))
        repo.saveEvent(makeEvent("e2", "c1", notes = "older", timestamp = anchor))
        vm = makeVm("e1")
        vm.setNotes("edited")
        vm.pageSettled(1)
        assertEquals("edited", vm.notes.value)
    }

}
