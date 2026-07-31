package net.clahey.trackr.ui.category

import androidx.lifecycle.SavedStateHandle
import net.clahey.trackr.FakeTrackrRepository
import net.clahey.trackr.reminders.testReminderScheduler
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Event
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TestFunctionName")
class CategoryEditViewModelHierarchyTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeTrackrRepository

    private val anchor: Instant = Instant.parse("2024-01-15T12:00:00Z")

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTrackrRepository()
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // @spec CAT-UI-030
    @Test fun `warning for MetaCategory counts events in inheriting SubCategories`() = runTest {
        val parent = makeMetaCategory("parent", valueType = ValueType.None)
        val child = makeSubCategory("child", parent = parent) // null valueType = inheriting
        repo.saveCategory(parent)
        repo.saveCategory(child)
        repo.saveEvent(makeEvent("e1", "child")) // inheriting SubCategory event
        val vm = editVm("parent")
        vm.setValueTypeState(ValueType.Number) // non-reversible; count > 0 → warning
        assertNotNull(vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030
    @Test fun `warning for MetaCategory excludes events in SubCategories with explicit valueType`() = runTest {
        val parent = makeMetaCategory("parent", valueType = ValueType.None)
        val child = makeSubCategory("child", parent = parent, valueType = ValueType.Text) // explicit
        repo.saveCategory(parent)
        repo.saveCategory(child)
        repo.saveEvent(makeEvent("e1", "child")) // non-inheriting SubCategory event
        val vm = editVm("parent")
        vm.setValueTypeState(ValueType.Number)
        assertNull(vm.valueTypeWarning.value) // child's event not counted
    }

    // @spec CAT-UI-030
    @Test fun `warning for SubCategory uses only its own events`() = runTest {
        val parent = makeMetaCategory("parent", valueType = ValueType.None)
        val child = makeSubCategory("child", parent = parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        repo.saveEvent(makeEvent("e1", "parent")) // parent's event — must not count for child
        val vm = editVm("child")
        vm.setValueTypeState(ValueType.Number)
        assertNull(vm.valueTypeWarning.value) // child has zero own events
    }

    // @spec CAT-UI-030, CAT-UI-031
    @Test fun `originalValueType for inheriting SubCategory is parent resolved type`() = runTest {
        val parent = makeMetaCategory("parent", valueType = ValueType.Scale)
        val child = makeSubCategory("child", parent = parent) // inheriting → effective = Scale
        repo.saveCategory(parent)
        repo.saveCategory(child)
        repo.saveEvent(makeEvent("e1", "child"))
        val vm = editVm("child")
        vm.setValueTypeState(ValueType.None) // Scale→None is Unsafe; event exists → warning
        assertNotNull(vm.valueTypeWarning.value)
        vm.setValueTypeState(null) // revert to inherit → effectiveValueType = Scale = original
        assertNull(vm.valueTypeWarning.value) // back to original (CAT-UI-031)
    }

    // @spec CAT-UI-043
    @Test fun `SubCategory create mode does not advance color counter`() = runTest {
        val parent = makeMetaCategory("parent")
        repo.saveCategory(parent)
        val counterBefore = repo.peekColorCounter()
        CategoryEditViewModel(repo, testReminderScheduler(repo), SavedStateHandle(mapOf("parentId" to "parent")))
        assertEquals(counterBefore, repo.peekColorCounter())
    }

    // @spec CAT-UI-054
    @Test fun `SubCategory create mode opens with inherit mode for all inheritable fields`() = runTest {
        val parent = makeMetaCategory("parent")
        repo.saveCategory(parent)
        val vm = CategoryEditViewModel(repo, testReminderScheduler(repo), SavedStateHandle(mapOf("parentId" to "parent")))
        assertEquals(EmojiMode.INHERIT, vm.emojiUIState.value.mode)
        assertNull(vm.colorState.value)
        assertNull(vm.valueTypeState.value)
    }

    // @spec CAT-UI-041
    @Test fun `new SubCategory gets global minimum sortOrder minus 1`() = runTest {
        val parent = makeMetaCategory("parent", sortOrder = 5)
        repo.saveCategory(parent)
        val vm = CategoryEditViewModel(repo, testReminderScheduler(repo), SavedStateHandle(mapOf("parentId" to "parent")))
        vm.setName("child")
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
        val saved = repo.getCategories().first().first { it.id != "parent" } as Category.SubCategory
        assertEquals(4, saved.sortOrder)
    }

    // @spec DM-PROC-021
    @Test fun `MetaCategory migration includes events of inheriting SubCategories`() = runTest {
        val parent = makeMetaCategory("parent", valueType = ValueType.None)
        val child = makeSubCategory("child", parent = parent) // null valueType = inheriting
        repo.saveCategory(parent)
        repo.saveCategory(child)
        repo.saveEvent(makeEvent("e1", "child", null)) // None-type event on inheriting child
        val vm = editVm("parent")
        vm.setValueTypeState(ValueType.Number)
        vm.save()
        val childEvent = repo.getEventsByCategory("child").first().first()
        assertEquals(EventValue.NumberValue(0.0, null), childEvent.value)
    }

    // @spec DM-PROC-021
    @Test fun `MetaCategory migration excludes events of SubCategories with explicit valueType`() = runTest {
        val parent = makeMetaCategory("parent", valueType = ValueType.None)
        val child = makeSubCategory("child", parent = parent, valueType = ValueType.Text)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        repo.saveEvent(makeEvent("e1", "child", EventValue.TextValue("hello")))
        val vm = editVm("parent")
        vm.setValueTypeState(ValueType.Number)
        vm.save()
        val childEvent = repo.getEventsByCategory("child").first().first()
        assertEquals(EventValue.TextValue("hello"), childEvent.value) // not migrated
    }

    // ---------- SubCategory default value ----------

    // @spec CAT-UI-066
    @Test fun `SubCategory create mode pre-populates numberDefaultUnit from parent defaultValue`() = runTest {
        val parent = makeMetaCategory("parent", valueType = ValueType.Number,
            defaultValue = EventValue.NumberValue(0.0, "kg"))
        repo.saveCategory(parent)
        val vm = CategoryEditViewModel(repo, testReminderScheduler(repo), SavedStateHandle(mapOf("parentId" to "parent")))
        assertEquals("kg", vm.numberDefaultUnit.value)
    }

    // @spec CAT-UI-066
    @Test fun `SubCategory create mode uses blank unit when parent defaultValue is null`() = runTest {
        val parent = makeMetaCategory("parent", valueType = ValueType.Number, defaultValue = null)
        repo.saveCategory(parent)
        val vm = CategoryEditViewModel(repo, testReminderScheduler(repo), SavedStateHandle(mapOf("parentId" to "parent")))
        assertEquals("", vm.numberDefaultUnit.value)
    }

    // @spec CAT-UI-066
    @Test fun `SubCategory create mode saves null defaultValue when unit not edited`() = runTest {
        val parent = makeMetaCategory("parent", valueType = ValueType.Number,
            defaultValue = EventValue.NumberValue(0.0, "kg"))
        repo.saveCategory(parent)
        val vm = CategoryEditViewModel(repo, testReminderScheduler(repo), SavedStateHandle(mapOf("parentId" to "parent")))
        vm.setName("child")
        vm.save()
        val saved = repo.getCategories().first().first { it.id != "parent" } as Category.SubCategory
        assertNull(saved.defaultValue)
    }

    // @spec CAT-UI-066
    @Test fun `SubCategory create mode saves NumberValue when unit field edited`() = runTest {
        val parent = makeMetaCategory("parent", valueType = ValueType.Number, defaultValue = null)
        repo.saveCategory(parent)
        val vm = CategoryEditViewModel(repo, testReminderScheduler(repo), SavedStateHandle(mapOf("parentId" to "parent")))
        vm.updateNumberDefaultUnit("lbs")
        vm.setName("child")
        vm.save()
        val saved = repo.getCategories().first().first { it.id != "parent" } as Category.SubCategory
        assertEquals(EventValue.NumberValue(0.0, "lbs"), saved.defaultValue)
    }

    // @spec CAT-UI-066
    @Test fun `SubCategory create mode uses type default when parent defaultValue type mismatches effective valueType`() = runTest {
        val parent = makeMetaCategory("parent", valueType = ValueType.Number,
            defaultValue = EventValue.NumberValue(0.0, "kg"))
        repo.saveCategory(parent)
        val vm = CategoryEditViewModel(repo, testReminderScheduler(repo), SavedStateHandle(mapOf("parentId" to "parent")))
        vm.setValueTypeState(ValueType.Exercise)
        assertEquals("3", vm.exerciseDefaultSets.value)
        assertEquals("15", vm.exerciseDefaultReps.value)
    }

    // @spec CAT-UI-066
    @Test fun `SubCategory create mode pre-populates exercise defaults from parent ExerciseValue`() = runTest {
        val parent = makeMetaCategory("parent", valueType = ValueType.Exercise,
            defaultValue = EventValue.ExerciseValue(5, 10))
        repo.saveCategory(parent)
        val vm = CategoryEditViewModel(repo, testReminderScheduler(repo), SavedStateHandle(mapOf("parentId" to "parent")))
        assertEquals("5", vm.exerciseDefaultSets.value)
        assertEquals("10", vm.exerciseDefaultReps.value)
    }

    // ---------- Helpers ----------

    private fun editVm(categoryId: String) =
        CategoryEditViewModel(repo, testReminderScheduler(repo), SavedStateHandle(mapOf("categoryId" to categoryId)))

    private fun makeMetaCategory(
        id: String,
        sortOrder: Int = 0,
        emoji: String = "📌",
        color: Long = 0xFFE53935L,
        valueType: ValueType = ValueType.None,
        defaultValue: EventValue? = null,
    ) = Category.MetaCategory(
        id = id, name = id, emoji = emoji, color = color,
        valueType = valueType, defaultValue = defaultValue, allowEmptyText = true, sortOrder = sortOrder,
    )

    private fun makeSubCategory(
        id: String,
        parent: Category.MetaCategory,
        sortOrder: Int = 0,
        valueType: ValueType? = null,
    ) = Category.SubCategory(
        id = id, name = id, emoji = null, color = null, valueType = valueType,
        defaultValue = null, allowEmptyText = true, sortOrder = sortOrder, parent = parent,
    )

    private fun makeEvent(id: String, categoryId: String, value: EventValue? = null) = Event(
        id = id, categoryId = categoryId, timestamp = anchor,
        value = value, notes = null, imagePaths = emptyList(), createdAt = anchor,
    )
}
