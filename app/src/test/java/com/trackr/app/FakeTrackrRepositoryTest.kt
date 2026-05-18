package com.trackr.app

import com.trackr.app.domain.Category
import com.trackr.app.domain.ValueType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeTrackrRepositoryTest {

    private fun makeCategory(id: String, sortOrder: Int) = Category(
        id = id, name = id, emoji = "📌", color = 0xFFE53935L,
        valueType = ValueType.None, unit = null, allowEmptyText = true, sortOrder = sortOrder,
    )

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
}
