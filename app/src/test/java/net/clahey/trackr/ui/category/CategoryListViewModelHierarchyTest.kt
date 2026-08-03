package net.clahey.trackr.ui.category

// NOTE: This file will not compile until Phase 6 converts Category to a sealed class
// with MetaCategory and SubCategory variants (DM-DATA-025, DM-DATA-026, DM-DATA-027).
// Expected failure mode: compile error on Category.MetaCategory / Category.SubCategory references.

import net.clahey.trackr.FakeTrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.ValueType
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
class CategoryListViewModelHierarchyTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeTrackrRepository
    private lateinit var vm: CategoryListViewModel

    private val anchor: Instant = Instant.parse("2024-01-15T12:00:00Z")

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTrackrRepository()
        vm = CategoryListViewModel(repo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // ---------- List display ----------

    // @spec CAT-UI-001
    @Test fun `categories list includes MetaCategories and their SubCategories in sortOrder`() = runTest {
        val parent = makeMetaCategory("parent", sortOrder = 1)
        val child1 = makeSubCategory("child1", parent = parent, sortOrder = 2)
        val child2 = makeSubCategory("child2", parent = parent, sortOrder = 3)
        val top = makeMetaCategory("top", sortOrder = 0)
        repo.saveCategory(top)
        repo.saveCategory(parent)
        repo.saveCategory(child1)
        repo.saveCategory(child2)
        val result = vm.categories.first()
        assertEquals(listOf("top", "parent", "child1", "child2"), result.map { it.id })
    }

    // @spec CAT-UI-001
    @Test fun `categories list places SubCategories after parent even when SubCategory sortOrder is lower`() = runTest {
        val parent = makeMetaCategory("parent", sortOrder = 10)
        val child = makeSubCategory("child", parent = parent, sortOrder = 1)
        val other = makeMetaCategory("other", sortOrder = 5)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        repo.saveCategory(other)
        val result = vm.categories.first()
        assertEquals(listOf("other", "parent", "child"), result.map { it.id })
    }

    // ---------- Delete gate ----------

    // @spec CAT-UI-004
    @Test fun `deleting MetaCategory with SubCategories shows confirmation even with zero own events`() = runTest {
        val parent = makeMetaCategory("parent")
        val child = makeSubCategory("child", parent = parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        vm.deleteCategory("parent")
        assertNotNull(vm.pendingDeleteConfirmation.value)
    }

    // @spec CAT-UI-004
    @Test fun `deleting MetaCategory with zero own events and zero SubCategories deletes immediately`() = runTest {
        repo.saveCategory(makeMetaCategory("parent"))
        vm.deleteCategory("parent")
        assertNull(vm.pendingDeleteConfirmation.value)
        assertTrue(vm.categories.first().isEmpty())
    }

    // @spec CAT-UI-004
    @Test fun `deleting SubCategory with zero events deletes immediately`() = runTest {
        val parent = makeMetaCategory("parent")
        val child = makeSubCategory("child", parent = parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        vm.deleteCategory("child")
        assertNull(vm.pendingDeleteConfirmation.value)
        assertFalse(vm.categories.first().any { it.id == "child" })
    }

    // ---------- Confirmation dialog content ----------

    // @spec CAT-UI-005
    @Test fun `MetaCategory confirmation has ownEventCount and subCategoryCount`() = runTest {
        val parent = makeMetaCategory("parent")
        val child = makeSubCategory("child", parent = parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        repo.saveEvent(makeEvent("e1", "parent"))
        vm.deleteCategory("parent")
        val confirmation = vm.pendingDeleteConfirmation.value!!
        assertEquals(1, confirmation.ownEventCount)
        assertEquals(1, confirmation.subCategoryCount)
    }

    // @spec CAT-UI-005
    @Test fun `MetaCategory confirmation with only own events has zero subCategoryCount`() = runTest {
        repo.saveCategory(makeMetaCategory("parent"))
        repo.saveEvent(makeEvent("e1", "parent"))
        vm.deleteCategory("parent")
        val confirmation = vm.pendingDeleteConfirmation.value!!
        assertEquals(1, confirmation.ownEventCount)
        assertEquals(0, confirmation.subCategoryCount)
    }

    // @spec CAT-UI-005
    @Test fun `MetaCategory confirmation with only SubCategories has zero ownEventCount`() = runTest {
        val parent = makeMetaCategory("parent")
        val child = makeSubCategory("child", parent = parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        vm.deleteCategory("parent")
        val confirmation = vm.pendingDeleteConfirmation.value!!
        assertEquals(0, confirmation.ownEventCount)
        assertEquals(1, confirmation.subCategoryCount)
    }

    // ---------- MetaCategory deletion behavior ----------

    // @spec CAT-UI-006
    @Test fun `confirming MetaCategory deletion promotes SubCategories and deletes only parent's own events`() = runTest {
        val parent = makeMetaCategory("parent")
        val child = makeSubCategory("child", parent = parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        repo.saveEvent(makeEvent("e_parent", "parent"))
        repo.saveEvent(makeEvent("e_child", "child"))
        vm.deleteCategory("parent")
        vm.confirmDelete()
        val remaining = vm.categories.first()
        assertFalse("parent should be deleted", remaining.any { it.id == "parent" })
        assertTrue("child should still exist", remaining.any { it.id == "child" })
        assertNotNull(repo.getEventById("e_child").first())
        assertNull(repo.getEventById("e_parent").first())
    }

    // ---------- Helpers ----------

    private fun makeMetaCategory(id: String, sortOrder: Int = 0) = Category.MetaCategory(
        id = id, name = id, emoji = "📌", color = 0xFFE53935L,
        valueType = ValueType.None, defaultValue = null, allowEmptyText = true, sortOrder = sortOrder,
    )

    private fun makeSubCategory(
        id: String,
        parent: Category.MetaCategory,
        sortOrder: Int = 0,
    ) = Category.SubCategory(
        id = id, name = id, emoji = null, color = null, valueType = null,
        defaultValue = null, allowEmptyText = true, sortOrder = sortOrder, parent = parent,
    )

    private fun makeEvent(id: String, categoryId: String) = net.clahey.trackr.domain.Event(
        id = id, categoryId = categoryId, timestamp = anchor,
        value = null, notes = null, imagePaths = emptyList(), createdAt = anchor,
    )
}
