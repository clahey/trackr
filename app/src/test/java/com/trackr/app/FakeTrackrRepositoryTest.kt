package com.trackr.app

import com.trackr.app.domain.Category
import com.trackr.app.domain.ValueType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

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
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
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
    @Test fun `saveCategoryAndMigrateEvents throws when nesting a category that has children`() = runTest {
        val repo = FakeTrackrRepository()
        val parent = makeCategory("parent")
        val newParent = makeCategory("newParent")
        val child = makeSubCategory("child", parent = parent)
        repo.setCategories(parent, newParent, child)
        try {
            repo.saveCategoryAndMigrateEvents(
                makeSubCategory("parent", parent = newParent),
                fromType = ValueType.None,
            )
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    private fun makeCategory(id: String, sortOrder: Int = 0) = Category.MetaCategory(
        id = id, name = id, emoji = "📌", color = 0xFFE53935L,
        valueType = ValueType.None, unit = null, allowEmptyText = true, sortOrder = sortOrder,
    )

    private fun makeSubCategory(id: String, parent: Category.MetaCategory, sortOrder: Int = 0) =
        Category.SubCategory(
            id = id, name = id, emoji = null, color = null, valueType = null,
            unit = null, allowEmptyText = true, sortOrder = sortOrder, parent = parent,
        )
}
