package com.trackr.app.ui.home

import androidx.lifecycle.SavedStateHandle
import com.trackr.app.FakeImageStore
import com.trackr.app.FakeTrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.ConversionOutcome
import com.trackr.app.domain.ErrorKind
import com.trackr.app.domain.EventValue
import com.trackr.app.domain.ValueType
import com.trackr.app.ui.SaveResult
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
import com.trackr.app.domain.Event

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
    ) = Category(
        id = id, name = id, emoji = "📌", color = 0xFFE53935L,
        valueType = valueType, unit = null, allowEmptyText = true, sortOrder = 0,
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

    private fun makeVm(eventId: String) = EventEditViewModel(repo, imageStore, SavedStateHandle(mapOf("eventId" to eventId)))

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

    // @spec EL-UI-043
    @Test fun `event with ErrorValue has isValueEditable false`() = runTest {
        val event = makeEvent("e1", "c1", value = EventValue.ErrorValue(ErrorKind.UNPARSABLE, "bad"))
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertFalse(vm.isValueEditable.value)
    }

    // @spec EL-UI-043
    @Test fun `event with normal value has isValueEditable true`() = runTest {
        val event = makeEvent("e1", "c1", value = EventValue.Scale(7))
        repo.setCategories(makeCategory("c1"))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertTrue(vm.isValueEditable.value)
    }

    // @spec EL-UI-043
    @Test fun `category with Unknown valueType has isValueEditable false`() = runTest {
        val event = makeEvent("e1", "c1")
        repo.setCategories(makeCategory("c1", valueType = ValueType.Unknown("future_type")))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertFalse(vm.isValueEditable.value)
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
        vm.notes.value = "new note"
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.Success)
        val saved = repo.getEventById("e1").first()
        assertEquals("new note", saved?.notes)
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

    // region Value action banner (EL-UI-062 through EL-UI-067)

    // @spec EL-UI-062
    @Test fun `banner shown when value does not match category type`() = runTest {
        val event = makeEvent("e1", "c1", value = EventValue.TextValue("hello"))
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertNotNull(vm.conversionOutcome.value)
    }

    // @spec EL-UI-062
    @Test fun `banner not shown when value matches category type`() = runTest {
        val event = makeEvent("e1", "c1", value = EventValue.Scale(7))
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertNull(vm.conversionOutcome.value)
    }

    // @spec EL-UI-062
    @Test fun `banner not shown when value is null and category is None type`() = runTest {
        val event = makeEvent("e1", "c1", value = null)
        repo.setCategories(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertNull(vm.conversionOutcome.value)
    }

    // @spec EL-UI-062
    @Test fun `banner shown for ErrorValue on concrete category type`() = runTest {
        val event = makeEvent("e1", "c1", value = EventValue.ErrorValue(ErrorKind.UNPARSABLE, "bad"))
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertNotNull(vm.conversionOutcome.value)
    }

    // @spec EL-UI-062
    @Test fun `banner not shown when ErrorValue inferredType matches Unknown category`() = runTest {
        val error = EventValue.ErrorValue(ErrorKind.UNRECOGNIZED_TYPE, """{"type":"future_type"}""", inferredType = "future_type")
        val event = makeEvent("e1", "c1", value = error)
        repo.setCategories(makeCategory("c1", valueType = ValueType.Unknown("future_type")))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertNull(vm.conversionOutcome.value)
    }

    // @spec EL-UI-063
    @Test fun `conversionOutcome is Converted when value is genuinely convertible`() = runTest {
        val event = makeEvent("e1", "c1", value = EventValue.TextValue("7"))
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertTrue(vm.conversionOutcome.value is ConversionOutcome.Converted)
        assertEquals(EventValue.Scale(7), (vm.conversionOutcome.value as ConversionOutcome.Converted).value)
    }

    // @spec EL-UI-064
    @Test fun `conversionOutcome is UsedDefault when value is not convertible`() = runTest {
        val event = makeEvent("e1", "c1", value = EventValue.TextValue("not-a-number"))
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertTrue(vm.conversionOutcome.value is ConversionOutcome.UsedDefault)
        assertEquals(EventValue.Scale(5), (vm.conversionOutcome.value as ConversionOutcome.UsedDefault).value)
    }

    // @spec EL-UI-064
    @Test fun `conversionOutcome is UsedDefault for ErrorValue on concrete category type`() = runTest {
        val event = makeEvent("e1", "c1", value = EventValue.ErrorValue(ErrorKind.UNPARSABLE, "bad"))
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(event)
        vm = makeVm("e1")
        val outcome = vm.conversionOutcome.value
        assertTrue(outcome is ConversionOutcome.UsedDefault)
        assertEquals(EventValue.Scale(5), (outcome as ConversionOutcome.UsedDefault).value)
    }

    // @spec EL-UI-065
    @Test fun `conversionOutcome is Discard when non-null value stored against None-type category`() = runTest {
        val event = makeEvent("e1", "c1", value = EventValue.Scale(7))
        repo.setCategories(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertEquals(ConversionOutcome.Discard, vm.conversionOutcome.value)
    }

    // @spec EL-UI-065
    @Test fun `conversionOutcome is null when no banner is shown`() = runTest {
        val event = makeEvent("e1", "c1", value = EventValue.Scale(7))
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertNull(vm.conversionOutcome.value)
    }

    // @spec EL-UI-066
    @Test fun `applyConversion sets value to converted result and clears banner`() = runTest {
        val event = makeEvent("e1", "c1", value = EventValue.TextValue("7"))
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(event)
        vm = makeVm("e1")
        vm.applyConversion()
        assertEquals(EventValue.Scale(7), vm.value.value)
        assertNull(vm.conversionOutcome.value)
    }

    // @spec EL-UI-066
    @Test fun `applyConversion with UsedDefault sets value to default and clears banner`() = runTest {
        val event = makeEvent("e1", "c1", value = EventValue.TextValue("not-a-number"))
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(event)
        vm = makeVm("e1")
        vm.applyConversion()
        assertEquals(EventValue.Scale(5), vm.value.value)
        assertNull(vm.conversionOutcome.value)
    }

    // @spec EL-UI-066
    @Test fun `applyConversion with UsedDefault from ErrorValue sets value to default and makes field editable`() = runTest {
        val event = makeEvent("e1", "c1", value = EventValue.ErrorValue(ErrorKind.UNPARSABLE, "bad"))
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(event)
        vm = makeVm("e1")
        vm.applyConversion()
        assertEquals(EventValue.Scale(5), vm.value.value)
        assertNull(vm.conversionOutcome.value)
        assertTrue(vm.isValueEditable.value)
    }

    // @spec EL-UI-066
    @Test fun `applyConversion with Discard sets value to null and clears banner`() = runTest {
        // Non-null value stored against a None-type category triggers Discard
        val event = makeEvent("e1", "c1", value = EventValue.Scale(7))
        repo.setCategories(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(event)
        vm = makeVm("e1")
        vm.applyConversion()
        assertNull(vm.value.value)
        assertNull(vm.conversionOutcome.value)
    }

    // @spec EL-UI-067
    @Test fun `banner clears reactively when value changes to matching type`() = runTest {
        val event = makeEvent("e1", "c1", value = EventValue.TextValue("hello"))
        repo.setCategories(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(event)
        vm = makeVm("e1")
        assertNotNull(vm.conversionOutcome.value)
        vm.value.value = EventValue.Scale(7)
        assertNull(vm.conversionOutcome.value)
    }

    // endregion
}
