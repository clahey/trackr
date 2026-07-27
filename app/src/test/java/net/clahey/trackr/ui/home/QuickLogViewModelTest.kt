package net.clahey.trackr.ui.home

import net.clahey.trackr.FakeImageStore
import net.clahey.trackr.FakeTrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.EventValue
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.ui.SaveResult
import net.clahey.trackr.ui.components.ValueUIState
import net.clahey.trackr.ui.components.defaultValueUIStateForType
import kotlin.time.Duration
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class QuickLogViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeTrackrRepository
    private lateinit var imageStore: FakeImageStore
    private lateinit var vm: QuickLogViewModel

    companion object {
        val anchor: Instant = Instant.parse("2024-01-15T12:00:00Z")
        val fixedClock: Clock = Clock.fixed(anchor, ZoneOffset.UTC)
    }

    private fun makeCategory(
        id: String,
        valueType: ValueType = ValueType.None,
        allowEmptyText: Boolean = true,
        defaultValue: EventValue? = null,
    ) = Category.MetaCategory(
        id = id, name = id, emoji = "📌", color = 0xFFE53935L,
        valueType = valueType, defaultValue = defaultValue, allowEmptyText = allowEmptyText, sortOrder = 0,
    )

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTrackrRepository()
        imageStore = FakeImageStore()
        vm = QuickLogViewModel(repo, imageStore, clock = fixedClock)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // @spec EL-UI-032
    @Test fun `timestamp defaults to clock time at construction`() {
        assertEquals(anchor, vm.timestamp.value)
    }

    // @spec EL-UI-030
    @Test fun `selectCategory sets selectedCategory`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        vm.selectCategory(cat)
        assertEquals(cat, vm.selectedCategory.value)
    }

    // @spec EL-UI-034
    @Test fun `selected category deleted externally resets selectedCategory to null`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        vm.selectCategory(cat)
        repo.deleteCategory("c1")
        assertNull(vm.selectedCategory.value)
    }

    // @spec EL-NAV-002
    @Test fun `save generates UUID and stores event with correct fields`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        vm.selectCategory(cat)
        vm.notes.value = "test note"
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.Success)
        val events = repo.getEvents().first()
        assertEquals(1, events.size)
        val event = events[0]
        assertTrue(event.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        assertEquals("c1", event.categoryId)
        assertEquals(anchor, event.timestamp)
        assertEquals("test note", event.notes)
    }

    // @spec EL-UI-077
    @Test fun `save exposes the saved event's id via lastSavedEventId`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        vm.selectCategory(cat)
        vm.save()
        val persistedId = repo.getEvents().first().first().id
        assertEquals(persistedId, vm.lastSavedEventId.value)
    }

    // @spec EL-PROC-001
    @Test fun `reset deletes captured but unsaved image`() {
        val path = "/images/unsaved.jpg"
        vm.imagePath.value = path
        vm.reset()
        assertTrue(imageStore.wasDeleted(path))
    }

    // @spec EL-PROC-001
    // @spec EL-NAV-002b, EL-UI-032
    @Test fun `reset clears form state`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        vm.selectCategory(cat)
        vm.notes.value = "some notes"
        vm.imagePath.value = "/images/capture.jpg"
        vm.timestamp.value = Instant.parse("2020-01-01T00:00:00Z")
        vm.reset()
        assertNull(vm.selectedCategory.value)
        assertEquals("", vm.notes.value)
        assertNull(vm.imagePath.value)
        assertEquals(anchor, vm.timestamp.value)
        assertEquals(ValueUIState.None, vm.value.value)
    }

    // @spec EL-PROC-001
    @Test fun `reset with no captured image does not crash`() {
        vm.reset()
    }

    // @spec EL-UI-054
    @Test fun `save with empty text value when allowEmptyText false produces validation error`() = runTest {
        val cat = makeCategory("c1", valueType = ValueType.Text, allowEmptyText = false)
        repo.setCategories(cat)
        vm.selectCategory(cat)
        vm.value.value = ValueUIState.Text("")
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.ValidationError)
    }

    // @spec EL-UI-052b
    @Test fun `save with empty Number text produces validation error`() = runTest {
        val cat = makeCategory("c1", valueType = ValueType.Number)
        repo.setCategories(cat)
        vm.selectCategory(cat)
        // default state after selectCategory is Number("", defaultUnit) — empty text blocks save
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.ValidationError)
    }

    // @spec EL-UI-051b
    @Test fun `save with Boolean unset (null selection) produces validation error`() = runTest {
        val cat = makeCategory("c1", valueType = ValueType.Boolean)
        repo.setCategories(cat)
        vm.selectCategory(cat)
        // default is Bool(null) — no selection
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.ValidationError)
    }

    // @spec EL-UI-051b
    @Test fun `save with Boolean selected succeeds`() = runTest {
        val cat = makeCategory("c1", valueType = ValueType.Boolean)
        repo.setCategories(cat)
        vm.selectCategory(cat)
        vm.value.value = ValueUIState.Bool(true)
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.Success)
    }

    // @spec EL-UI-059b
    @Test fun `save with Exercise empty sets field produces validation error`() = runTest {
        val cat = makeCategory("c1", valueType = ValueType.Exercise)
        repo.setCategories(cat)
        vm.selectCategory(cat)
        vm.value.value = ValueUIState.Exercise("", "15")
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.ValidationError)
    }

    // @spec EL-UI-059b
    @Test fun `save with Exercise zero reps produces validation error`() = runTest {
        val cat = makeCategory("c1", valueType = ValueType.Exercise)
        repo.setCategories(cat)
        vm.selectCategory(cat)
        vm.value.value = ValueUIState.Exercise("3", "0")
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.ValidationError)
    }

    // @spec EL-UI-055c
    @Test fun `save with Duration all-empty fields saves as zero duration`() = runTest {
        val cat = makeCategory("c1", valueType = ValueType.Duration)
        repo.setCategories(cat)
        vm.selectCategory(cat)
        vm.value.value = ValueUIState.Duration("", "", "")
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.Success)
        val event = repo.getEvents().first().first()
        assertEquals(EventValue.DurationValue(kotlin.time.Duration.ZERO), event.value)
    }

    // @spec EL-UI-073
    @Test fun `expandMetaCategory sets expandedMetaCategoryId`() = runTest {
        vm.expandMetaCategory("m1")
        assertEquals("m1", vm.expandedMetaCategoryId.value)
    }

    // @spec EL-UI-073
    @Test fun `expandMetaCategory null clears expandedMetaCategoryId`() = runTest {
        vm.expandMetaCategory("m1")
        vm.expandMetaCategory(null)
        assertNull(vm.expandedMetaCategoryId.value)
    }

    // @spec EL-UI-073
    @Test fun `selectCategory preserves expandedMetaCategoryId for drill-down back navigation`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        vm.expandMetaCategory("c1")
        vm.selectCategory(cat)
        assertEquals("c1", vm.expandedMetaCategoryId.value)
    }

    // @spec EL-NAV-002b
    @Test fun `reset after successful save returns saveResult to Idle`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        vm.selectCategory(cat)
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
        vm.reset()
        assertEquals(SaveResult.Idle, vm.saveResult.value)
    }

    // @spec EL-UI-068
    @Test fun `selectCategory uses default when not dirty`() = runTest {
        val scaleCat = makeCategory("c1", valueType = ValueType.Scale)
        val scaleCat2 = makeCategory("c2", valueType = ValueType.Scale)
        repo.setCategories(scaleCat, scaleCat2)
        vm.selectCategory(scaleCat)
        // not dirty — even same-type switch should use the new category's default
        vm.selectCategory(scaleCat2)
        assertEquals(ValueUIState.Scale(5), vm.value.value)
    }

    // @spec EL-UI-068
    @Test fun `selectCategory uses default when no prior value`() = runTest {
        val scaleCat = makeCategory("c2", valueType = ValueType.Scale)
        repo.setCategories(scaleCat)
        vm.selectCategory(scaleCat)
        assertEquals(ValueUIState.Scale(5), vm.value.value)
    }

    // @spec EL-UI-068
    @Test fun `selectCategory uses default when switching from None type without interaction`() = runTest {
        val noneCat = makeCategory("c1", valueType = ValueType.None)
        val numCat = makeCategory("c2", valueType = ValueType.Number,
            defaultValue = EventValue.NumberValue(0.0, "kg"))
        repo.setCategories(noneCat, numCat)
        vm.selectCategory(noneCat)
        vm.selectCategory(numCat)
        assertEquals(ValueUIState.Number("0.0", "kg"), vm.value.value)
    }

    // @spec EL-UI-068b
    @Test fun `selectCategory preserves value verbatim when dirty and same type`() = runTest {
        val numCat1 = makeCategory("c1", valueType = ValueType.Number,
            defaultValue = EventValue.NumberValue(0.0, "kg"))
        val numCat2 = makeCategory("c2", valueType = ValueType.Number,
            defaultValue = EventValue.NumberValue(0.0, "cm"))
        repo.setCategories(numCat1, numCat2)
        vm.selectCategory(numCat1)
        vm.updateValue(ValueUIState.Number("75", "kg"))
        vm.selectCategory(numCat2)
        // text and unit preserved verbatim; new category's unit ("cm") is not substituted
        assertEquals(ValueUIState.Number("75", "kg"), vm.value.value)
    }

    // @spec EL-UI-068b
    @Test fun `selectCategory shows Mismatched when dirty and types differ`() = runTest {
        val boolCat = makeCategory("c1", valueType = ValueType.Boolean)
        val scaleCat = makeCategory("c2", valueType = ValueType.Scale)
        repo.setCategories(boolCat, scaleCat)
        vm.selectCategory(boolCat)
        vm.updateValue(ValueUIState.Bool(true))
        vm.selectCategory(scaleCat)
        assertTrue(vm.value.value is ValueUIState.Mismatched)
        assertEquals(
            EventValue.BooleanValue(true),
            (vm.value.value as ValueUIState.Mismatched).originalValue,
        )
    }

    // @spec EL-UI-068b
    @Test fun `selectCategory uses default when dirty but effective value is partial`() = runTest {
        val boolCat = makeCategory("c1", valueType = ValueType.Boolean)
        val scaleCat = makeCategory("c2", valueType = ValueType.Scale)
        repo.setCategories(boolCat, scaleCat)
        vm.selectCategory(boolCat)
        vm.updateValue(ValueUIState.Bool(null)) // partial — no selection made
        vm.selectCategory(scaleCat)
        assertEquals(ValueUIState.Scale(5), vm.value.value)
    }

    // @spec EL-UI-068b
    @Test fun `selectCategory unwraps Mismatched editableState for type check`() = runTest {
        val boolCat = makeCategory("c1", valueType = ValueType.Boolean)
        val scaleCat = makeCategory("c2", valueType = ValueType.Scale)
        val boolCat2 = makeCategory("c3", valueType = ValueType.Boolean)
        repo.setCategories(boolCat, scaleCat, boolCat2)
        vm.selectCategory(boolCat)
        vm.updateValue(ValueUIState.Bool(true))
        vm.selectCategory(scaleCat) // type mismatch → Mismatched(editableState=Bool(true))
        vm.selectCategory(boolCat2) // unwrap → Bool(true) matches Boolean → preserve
        assertEquals(ValueUIState.Bool(true), vm.value.value)
    }

    // @spec EL-UI-068b
    @Test fun `selectCategory recovers value from Mismatched originalValue after Discard pass-through`() = runTest {
        val numCat = makeCategory("c1", valueType = ValueType.Number,
            defaultValue = EventValue.NumberValue(0.0, "kg"))
        val noneCat = makeCategory("c2", valueType = ValueType.None)
        repo.setCategories(numCat, noneCat)
        vm.selectCategory(numCat)
        vm.updateValue(ValueUIState.Number("75", "kg"))
        vm.selectCategory(noneCat) // Discard outcome → Mismatched with null editableState
        vm.selectCategory(numCat)  // recover from originalValue → Number matches Number
        assertTrue(vm.value.value is ValueUIState.Number)
    }

    // @spec EL-UI-068c
    @Test fun `updateValue sets valueDirty`() = runTest {
        val cat = makeCategory("c1", valueType = ValueType.Scale)
        repo.setCategories(cat)
        vm.selectCategory(cat)
        vm.updateValue(ValueUIState.Scale(7))
        assertTrue(vm.valueDirty.value)
    }

    // @spec EL-UI-068c
    @Test fun `valueDirty persists across category switches`() = runTest {
        val scaleCat = makeCategory("c1", valueType = ValueType.Scale)
        val numCat = makeCategory("c2", valueType = ValueType.Number)
        repo.setCategories(scaleCat, numCat)
        vm.selectCategory(scaleCat)
        vm.updateValue(ValueUIState.Scale(7))
        vm.selectCategory(numCat)
        assertTrue(vm.valueDirty.value)
    }

    // @spec EL-UI-068c
    @Test fun `reset clears valueDirty`() = runTest {
        val cat = makeCategory("c1", valueType = ValueType.Scale)
        repo.setCategories(cat)
        vm.selectCategory(cat)
        vm.updateValue(ValueUIState.Scale(7))
        vm.reset()
        assertFalse(vm.valueDirty.value)
    }

    // @spec EL-UI-031a, EL-UI-031b
    @Test fun `createImageFile creates a file tracked by imageStore`() {
        val path = vm.createImageFile()
        assertTrue(imageStore.allStoredPaths().contains(path))
    }

    // @spec EL-UI-031b
    @Test fun `commitImage sets imagePath`() {
        val path = vm.createImageFile()
        vm.commitImage(path)
        assertEquals(path, vm.imagePath.value)
    }

    // @spec EL-UI-031b
    @Test fun `commitImage when replacing deletes old file and sets new`() {
        val old = vm.createImageFile()
        vm.commitImage(old)
        val new = vm.createImageFile()
        vm.commitImage(new)
        assertTrue(imageStore.wasDeleted(old))
        assertEquals(new, vm.imagePath.value)
    }

    // @spec EL-UI-031b
    @Test fun `cancelImage deletes the given file`() {
        val path = vm.createImageFile()
        vm.cancelImage(path)
        assertTrue(imageStore.wasDeleted(path))
    }

    // @spec EL-UI-031b
    @Test fun `cancelImage when replacing leaves existing imagePath unchanged`() {
        val old = vm.createImageFile()
        vm.commitImage(old)
        val pending = vm.createImageFile()
        vm.cancelImage(pending)
        assertEquals(old, vm.imagePath.value)
        assertTrue(imageStore.wasDeleted(pending))
    }

    // @spec EL-UI-031b
    @Test fun `removeImage deletes file and clears imagePath`() {
        val path = vm.createImageFile()
        vm.commitImage(path)
        vm.removeImage()
        assertTrue(imageStore.wasDeleted(path))
        assertNull(vm.imagePath.value)
    }

    // @spec EL-UI-078
    @Test fun `selectCategory seeds value from resolvedDefaultValue when type matches`() = runTest {
        val cat = makeCategory("c1", valueType = ValueType.Number,
            defaultValue = EventValue.NumberValue(42.0, "kg"))
        repo.setCategories(cat)
        vm.selectCategory(cat)
        assertEquals(ValueUIState.Number("42.0", "kg"), vm.value.value)
    }

    // @spec EL-UI-078
    @Test fun `selectCategory falls back to type default when resolvedDefaultValue type mismatches valueType`() = runTest {
        val cat = makeCategory("c1", valueType = ValueType.Scale,
            defaultValue = EventValue.NumberValue(0.0, "kg"))
        repo.setCategories(cat)
        vm.selectCategory(cat)
        assertEquals(ValueUIState.Scale(5), vm.value.value)
    }

    // @spec EL-UI-078
    @Test fun `selectCategory falls back to type default when resolvedDefaultValue is null`() = runTest {
        val cat = makeCategory("c1", valueType = ValueType.Exercise, defaultValue = null)
        repo.setCategories(cat)
        vm.selectCategory(cat)
        assertEquals(ValueUIState.Exercise("3", "15"), vm.value.value)
    }
}
