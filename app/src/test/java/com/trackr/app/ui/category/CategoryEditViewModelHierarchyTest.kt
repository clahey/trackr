package com.trackr.app.ui.category

import androidx.lifecycle.SavedStateHandle
import com.trackr.app.FakeTrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
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
        vm.valueTypeState.value = ValueType.Number // non-reversible; count > 0 → warning
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
        vm.valueTypeState.value = ValueType.Number
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
        vm.valueTypeState.value = ValueType.Number
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
        vm.valueTypeState.value = ValueType.None // Scale→None is Unsafe; event exists → warning
        assertNotNull(vm.valueTypeWarning.value)
        vm.valueTypeState.value = null // revert to inherit → effectiveValueType = Scale = original
        assertNull(vm.valueTypeWarning.value) // back to original (CAT-UI-031)
    }

    // @spec CAT-UI-043
    @Test fun `SubCategory create mode does not advance color counter`() = runTest {
        val parent = makeMetaCategory("parent")
        repo.saveCategory(parent)
        val counterBefore = repo.peekColorCounter()
        CategoryEditViewModel(repo, SavedStateHandle(mapOf("parentId" to "parent")))
        assertEquals(counterBefore, repo.peekColorCounter())
    }

    // @spec CAT-UI-054
    @Test fun `SubCategory create mode opens with null inheritable fields`() = runTest {
        val parent = makeMetaCategory("parent")
        repo.saveCategory(parent)
        val vm = CategoryEditViewModel(repo, SavedStateHandle(mapOf("parentId" to "parent")))
        assertNull(vm.emojiState.value)
        assertNull(vm.colorState.value)
        assertNull(vm.valueTypeState.value)
    }

    // @spec CAT-UI-041
    @Test fun `new SubCategory gets global minimum sortOrder minus 1`() = runTest {
        val parent = makeMetaCategory("parent", sortOrder = 5)
        repo.saveCategory(parent)
        val vm = CategoryEditViewModel(repo, SavedStateHandle(mapOf("parentId" to "parent")))
        vm.name.value = "child"
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
        val saved = repo.getCategories().first().first { it.id != "parent" } as Category.SubCategory
        assertEquals(4, saved.sortOrder)
    }

    // @spec DM-PROC-019
    @Test fun `removeFromGroup resolves null emoji color and valueType to parent values`() = runTest {
        val parent = makeMetaCategory(
            "parent", emoji = "🏋️", color = 0xFF1E88E5L, valueType = ValueType.Number,
        )
        val child = makeSubCategory("child", parent = parent) // all null (inheriting)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        val vm = editVm("child")
        vm.removeFromGroup()
        val saved = repo.getCategoryById("child").first() as Category.MetaCategory
        assertEquals("🏋️", saved.emoji)
        assertEquals(0xFF1E88E5L, saved.color)
        assertEquals(ValueType.Number, saved.valueType)
    }

    // @spec DM-PROC-021
    @Test fun `MetaCategory migration includes events of inheriting SubCategories`() = runTest {
        val parent = makeMetaCategory("parent", valueType = ValueType.None)
        val child = makeSubCategory("child", parent = parent) // null valueType = inheriting
        repo.saveCategory(parent)
        repo.saveCategory(child)
        repo.saveEvent(makeEvent("e1", "child", null)) // None-type event on inheriting child
        val vm = editVm("parent")
        vm.valueTypeState.value = ValueType.Number
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
        vm.valueTypeState.value = ValueType.Number
        vm.save()
        val childEvent = repo.getEventsByCategory("child").first().first()
        assertEquals(EventValue.TextValue("hello"), childEvent.value) // not migrated
    }

    // ---------- Helpers ----------

    private fun editVm(categoryId: String) =
        CategoryEditViewModel(repo, SavedStateHandle(mapOf("categoryId" to categoryId)))

    private fun makeMetaCategory(
        id: String,
        sortOrder: Int = 0,
        emoji: String = "📌",
        color: Long = 0xFFE53935L,
        valueType: ValueType = ValueType.None,
    ) = Category.MetaCategory(
        id = id, name = id, emoji = emoji, color = color,
        valueType = valueType, unit = null, allowEmptyText = true, sortOrder = sortOrder,
    )

    private fun makeSubCategory(
        id: String,
        parent: Category.MetaCategory,
        sortOrder: Int = 0,
        valueType: ValueType? = null,
    ) = Category.SubCategory(
        id = id, name = id, emoji = null, color = null, valueType = valueType,
        unit = null, allowEmptyText = true, sortOrder = sortOrder, parent = parent,
    )

    private fun makeEvent(id: String, categoryId: String, value: EventValue? = null) = Event(
        id = id, categoryId = categoryId, timestamp = anchor,
        value = value, notes = null, imagePaths = emptyList(), createdAt = anchor,
    )
}
