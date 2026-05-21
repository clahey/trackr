package com.trackr.app.ui.category

import com.trackr.app.FakeTrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.ValueType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
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
class CategoryListViewModelTest {

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

    // @spec CAT-UI-001
    @Test fun `categories are exposed sorted by sortOrder ascending`() = runTest {
        repo.saveCategory(makeCategory("c3", sortOrder = 3))
        repo.saveCategory(makeCategory("c1", sortOrder = 1))
        repo.saveCategory(makeCategory("c2", sortOrder = 2))
        val result = vm.categories.first()
        assertEquals(listOf("c1", "c2", "c3"), result.map { it.id })
    }

    // @spec CAT-UI-004
    @Test fun `deleting category with no events deletes immediately without confirmation`() = runTest {
        repo.saveCategory(makeCategory("c1"))
        vm.deleteCategory("c1")
        assertNull(vm.pendingDeleteConfirmation.value)
        assertTrue(vm.categories.first().isEmpty())
    }

    // @spec CAT-UI-005
    @Test fun `deleting category with events shows confirmation with event count`() = runTest {
        repo.saveCategory(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        repo.saveEvent(makeEvent("e2", "c1"))
        vm.deleteCategory("c1")
        val confirmation = vm.pendingDeleteConfirmation.value
        assertNotNull(confirmation)
        assertEquals("c1", confirmation!!.categoryId)
        assertEquals(2, confirmation.ownEventCount)
    }

    // @spec CAT-UI-006
    @Test fun `confirmDelete deletes category and all its events`() = runTest {
        repo.saveCategory(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm.deleteCategory("c1")
        vm.confirmDelete()
        assertNull(vm.pendingDeleteConfirmation.value)
        assertTrue(vm.categories.first().isEmpty())
    }

    // @spec CAT-UI-005
    @Test fun `cancelDelete clears pending confirmation without deleting`() = runTest {
        repo.saveCategory(makeCategory("c1"))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm.deleteCategory("c1")
        vm.cancelDelete()
        assertNull(vm.pendingDeleteConfirmation.value)
        assertFalse(vm.categories.first().isEmpty())
    }

    private fun makeCategory(id: String, sortOrder: Int = 0) = Category.MetaCategory(
        id = id, name = id, emoji = "📌", color = 0xFFE53935L,
        valueType = ValueType.None, unit = null, allowEmptyText = true, sortOrder = sortOrder,
    )

    private fun makeEvent(id: String, categoryId: String) = com.trackr.app.domain.Event(
        id = id, categoryId = categoryId, timestamp = anchor,
        value = null, notes = null, imagePaths = emptyList(), createdAt = anchor,
    )
}
