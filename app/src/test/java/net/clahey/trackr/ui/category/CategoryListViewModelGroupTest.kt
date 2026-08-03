package net.clahey.trackr.ui.category

import net.clahey.trackr.FakeTrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Event
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.domain.ValueTypeWarningTier
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TestFunctionName")
class CategoryListViewModelGroupTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeTrackrRepository
    private lateinit var vm: CategoryListViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTrackrRepository()
        vm = CategoryListViewModel(repo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // @spec CAT-UI-003, CAT-UI-051
    @Test fun `startAddToGroup exposes all MetaCategories except self as eligible parents`() = runTest {
        repo.saveCategory(makeMetaCategory("target"))
        repo.saveCategory(makeMetaCategory("other1"))
        repo.saveCategory(makeMetaCategory("other2"))
        vm.startAddToGroup("target")
        val state = vm.pendingGroupPicker.value!!
        assertEquals(setOf("other1", "other2"), state.eligibleParents.map { it.id }.toSet())
        assertFalse(state.isMoveOperation)
    }

    // @spec CAT-UI-051
    @Test fun `startMoveToAnotherGroup excludes current parent from eligible parents`() = runTest {
        val parent = makeMetaCategory("parent")
        repo.saveCategory(parent)
        repo.saveCategory(makeMetaCategory("other"))
        repo.saveCategory(makeSubCategory("child", parent))
        vm.startMoveToAnotherGroup("child")
        val state = vm.pendingGroupPicker.value!!
        assertEquals(setOf("other"), state.eligibleParents.map { it.id }.toSet())
        assertTrue(state.isMoveOperation)
    }

    // @spec CAT-UI-003
    @Test fun `dismissGroupPicker clears pending state`() = runTest {
        repo.saveCategory(makeMetaCategory("target"))
        repo.saveCategory(makeMetaCategory("other"))
        vm.startAddToGroup("target")
        assertNotNull(vm.pendingGroupPicker.value)
        vm.dismissGroupPicker()
        assertNull(vm.pendingGroupPicker.value)
    }

    // @spec CAT-UI-052, DM-PROC-020
    @Test fun `reparentCategory converts MetaCategory to SubCategory preserving all explicit fields`() = runTest {
        val target = makeMetaCategory("target", emoji = "🏋️", color = 0xFF1E88E5L, valueType = ValueType.Number)
        val newParent = makeMetaCategory("parent")
        repo.saveCategory(target)
        repo.saveCategory(newParent)
        vm.reparentCategory("target", "parent")
        val saved = repo.getCategoryById("target").first() as Category.SubCategory
        assertEquals("🏋️", saved.emoji)
        assertEquals(0xFF1E88E5L, saved.color)
        assertEquals(ValueType.Number, saved.valueType)
        assertEquals("parent", saved.parent.id)
    }

    // @spec CAT-UI-052, DM-PROC-020
    @Test fun `reparentCategory preserves null inheritable fields when moving SubCategory to new parent`() = runTest {
        val oldParent = makeMetaCategory("oldParent")
        val newParent = makeMetaCategory("newParent")
        val child = makeSubCategory("child", oldParent) // all nulls = inheriting
        repo.saveCategory(oldParent)
        repo.saveCategory(newParent)
        repo.saveCategory(child)
        vm.reparentCategory("child", "newParent")
        val saved = repo.getCategoryById("child").first() as Category.SubCategory
        assertNull(saved.emoji)
        assertNull(saved.color)
        assertNull(saved.valueType)
        assertEquals("newParent", saved.parent.id)
    }

    // @spec CAT-UI-052, DM-PROC-020
    @Test fun `reparentCategory preserves explicit overrides on SubCategory when moving to new parent`() = runTest {
        val oldParent = makeMetaCategory("oldParent")
        val newParent = makeMetaCategory("newParent")
        val child = makeSubCategory("child", oldParent, emoji = "🎯", color = 0xFF00BCD4L, valueType = ValueType.Text)
        repo.saveCategory(oldParent)
        repo.saveCategory(newParent)
        repo.saveCategory(child)
        vm.reparentCategory("child", "newParent")
        val saved = repo.getCategoryById("child").first() as Category.SubCategory
        assertEquals("🎯", saved.emoji)
        assertEquals(0xFF00BCD4L, saved.color)
        assertEquals(ValueType.Text, saved.valueType)
        assertEquals("newParent", saved.parent.id)
    }

    // @spec CAT-UI-081
    @Test fun `reparentCategory shows a value-type confirmation dialog when the inherited type would change unsafely`() = runTest {
        val oldParent = makeMetaCategory("oldParent", valueType = ValueType.Number)
        val newParent = makeMetaCategory("newParent", valueType = ValueType.Duration)
        val child = makeSubCategory("child", oldParent) // inherits Number -> Duration: unsafe
        repo.saveCategory(oldParent)
        repo.saveCategory(newParent)
        repo.saveCategory(child)
        repo.setEvents(Event("e1", "child", Instant.parse("2024-01-15T12:00:00Z"), null, null, emptyList(), Instant.parse("2024-01-15T12:00:00Z")))
        vm.reparentCategory("child", "newParent")
        val pending = vm.pendingValueTypeConfirmation.value
        assertEquals(ValueTypeWarningTier.Unsafe, pending?.tier)
        assertEquals("oldParent", (repo.getCategoryById("child").first() as Category.SubCategory).parent.id)
    }

    // @spec CAT-UI-081
    @Test fun `reparentCategory persists directly when the inherited type does not change`() = runTest {
        val oldParent = makeMetaCategory("oldParent", valueType = ValueType.Text)
        val newParent = makeMetaCategory("newParent", valueType = ValueType.Text)
        val child = makeSubCategory("child", oldParent)
        repo.saveCategory(oldParent)
        repo.saveCategory(newParent)
        repo.saveCategory(child)
        vm.reparentCategory("child", "newParent")
        assertNull(vm.pendingValueTypeConfirmation.value)
        assertEquals("newParent", (repo.getCategoryById("child").first() as Category.SubCategory).parent.id)
    }

    // @spec CAT-UI-051
    @Test fun `reparentWithNewGroup creates MetaCategory with given name and reparents target`() = runTest {
        repo.saveCategory(makeMetaCategory("target"))
        vm.reparentWithNewGroup("target", "My New Group")
        val allCats = repo.getCategories().first()
        val newGroup = allCats.filterIsInstance<Category.MetaCategory>()
            .first { it.name == "My New Group" }
        val reparented = repo.getCategoryById("target").first() as Category.SubCategory
        assertEquals(newGroup.id, reparented.parent.id)
    }

    // @spec CAT-UI-051
    @Test fun `reparentWithNewGroup assigns new group a sortOrder below existing minimum`() = runTest {
        repo.saveCategory(makeMetaCategory("target", sortOrder = 5))
        vm.reparentWithNewGroup("target", "Group")
        val allCats = repo.getCategories().first()
        val newGroup = allCats.filterIsInstance<Category.MetaCategory>().first { it.name == "Group" }
        assertTrue(newGroup.sortOrder < 5)
    }

    // @spec CAT-UI-003
    @Test fun `removeFromGroup promotes SubCategory to MetaCategory resolving null inheritable fields`() = runTest {
        val parent = makeMetaCategory("parent", emoji = "🏋️", color = 0xFF1E88E5L, valueType = ValueType.Number)
        val child = makeSubCategory("child", parent) // all nulls
        repo.saveCategory(parent)
        repo.saveCategory(child)
        vm.removeFromGroup("child")
        val promoted = repo.getCategoryById("child").first() as Category.MetaCategory
        assertEquals("🏋️", promoted.emoji)
        assertEquals(0xFF1E88E5L, promoted.color)
        assertEquals(ValueType.Number, promoted.valueType)
    }

    // @spec CAT-UI-003
    @Test fun `removeFromGroup preserves explicit fields on SubCategory when promoting`() = runTest {
        val parent = makeMetaCategory("parent", emoji = "🏋️", color = 0xFF1E88E5L, valueType = ValueType.Number)
        val child = makeSubCategory("child", parent, emoji = "🎯", color = 0xFF00BCD4L, valueType = ValueType.Text)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        vm.removeFromGroup("child")
        val promoted = repo.getCategoryById("child").first() as Category.MetaCategory
        assertEquals("🎯", promoted.emoji)
        assertEquals(0xFF00BCD4L, promoted.color)
        assertEquals(ValueType.Text, promoted.valueType)
    }

    // @spec CAT-UI-051
    @Test fun `reparentCategory dismisses group picker after completion`() = runTest {
        val target = makeMetaCategory("target")
        val parent = makeMetaCategory("parent")
        repo.saveCategory(target)
        repo.saveCategory(parent)
        vm.startAddToGroup("target")
        assertNotNull(vm.pendingGroupPicker.value)
        vm.reparentCategory("target", "parent")
        assertNull(vm.pendingGroupPicker.value)
    }

    // ---------- Helpers ----------

    private fun makeMetaCategory(
        id: String,
        sortOrder: Int = 0,
        emoji: String = "📌",
        color: Long = 0xFFE53935L,
        valueType: ValueType = ValueType.None,
    ) = Category.MetaCategory(
        id = id, name = id, emoji = emoji, color = color,
        valueType = valueType, defaultValue = null, allowEmptyText = true, sortOrder = sortOrder,
    )

    private fun makeSubCategory(
        id: String,
        parent: Category.MetaCategory,
        emoji: String? = null,
        color: Long? = null,
        valueType: ValueType? = null,
    ) = Category.SubCategory(
        id = id, name = id, emoji = emoji, color = color, valueType = valueType,
        defaultValue = null, allowEmptyText = true, sortOrder = 0, parent = parent,
    )
}
