package com.trackr.app.ui.category

import com.trackr.app.FakeTrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.ValueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.trackr.app.ui.SaveResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryEditViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeTrackrRepository
    private lateinit var vm: CategoryEditViewModel

    companion object {
        val anchor: Instant = Instant.parse("2024-01-15T12:00:00Z")
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTrackrRepository()
        vm = CategoryEditViewModel(repo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // @spec CAT-UI-020
    @Test fun `save with empty name produces validation error`() = runTest {
        vm.name.value = ""
        vm.emoji.value = "🏃"
        vm.save()
        val result = vm.saveResult.value
        assertTrue(result is SaveResult.ValidationError)
        assertEquals("name", (result as SaveResult.ValidationError).field)
    }

    // @spec CAT-UI-020
    @Test fun `save with whitespace-only name produces validation error`() = runTest {
        vm.name.value = "   "
        vm.emoji.value = "🏃"
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.ValidationError)
    }

    // @spec CAT-UI-021
    @Test fun `save with empty emoji produces validation error`() = runTest {
        vm.name.value = "Running"
        vm.emoji.value = ""
        vm.save()
        val result = vm.saveResult.value
        assertTrue(result is SaveResult.ValidationError)
        assertEquals("emoji", (result as SaveResult.ValidationError).field)
    }

    // @spec CAT-UI-022
    @Test fun `save with multi-grapheme emoji produces validation error`() = runTest {
        vm.name.value = "Running"
        vm.emoji.value = "🏃🏃"
        vm.save()
        val result = vm.saveResult.value
        assertTrue(result is SaveResult.ValidationError)
        assertEquals("emoji", (result as SaveResult.ValidationError).field)
    }

    // @spec CAT-UI-040
    @Test fun `new category gets a UUID on save`() = runTest {
        vm.name.value = "Running"
        vm.emoji.value = "🏃"
        vm.save()
        val saved = repo.getCategories()
        assertTrue(vm.saveResult.value is SaveResult.Success)
        // UUID format check: 8-4-4-4-12 hex chars
        val savedCategory = getSavedCategory()
        assertTrue(savedCategory.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    // @spec CAT-UI-041
    @Test fun `new category gets sortOrder currentMin minus 1`() = runTest {
        repo.saveCategory(makeCategory("existing", sortOrder = 5))
        vm.name.value = "Running"
        vm.emoji.value = "🏃"
        vm.save()
        assertEquals(4, getSavedCategoryByName("Running").sortOrder)
    }

    // @spec CAT-UI-042
    @Test fun `new category gets allowEmptyText true`() = runTest {
        vm.name.value = "Running"
        vm.emoji.value = "🏃"
        vm.save()
        assertTrue(getSavedCategory().allowEmptyText)
    }

    // @spec CAT-UI-043
    @Test fun `new category default color uses monotonic counter`() = runTest {
        vm.name.value = "Running"
        vm.emoji.value = "🏃"
        vm.save()
        val color = getSavedCategory().color
        // First call to getAndIncrement returns 0 → Red (0xFFE53935)
        assertEquals(0xFFE53935L, color)
    }

    // @spec CAT-UI-043
    @Test fun `second new category gets next palette color`() = runTest {
        vm.name.value = "Running"; vm.emoji.value = "🏃"; vm.save()
        vm = CategoryEditViewModel(repo)
        vm.name.value = "Sleep"; vm.emoji.value = "💤"; vm.save()
        assertEquals(0xFFE53935L, getSavedCategoryByName("Running").color) // Red
        assertEquals(0xFFFB8C00L, getSavedCategoryByName("Sleep").color)   // Orange
    }

    // @spec CAT-UI-030
    @Test fun `changing valueType on category with events shows warning`() = runTest {
        val existingCategory = makeCategory("c1", valueType = ValueType.Scale)
        repo.saveCategory(existingCategory)
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = CategoryEditViewModel(repo, categoryId = "c1")
        vm.valueType.value = ValueType.Text
        assertTrue(vm.showValueTypeWarning.value)
    }

    // @spec CAT-UI-031
    @Test fun `changing valueType on category with no events shows no warning`() = runTest {
        val existingCategory = makeCategory("c1", valueType = ValueType.Scale)
        repo.saveCategory(existingCategory)
        vm = CategoryEditViewModel(repo, categoryId = "c1")
        vm.valueType.value = ValueType.Text
        assertFalse(vm.showValueTypeWarning.value)
    }

    private suspend fun getSavedCategory(): Category =
        repo.getCategories().first().first()

    private suspend fun getSavedCategoryByName(name: String): Category =
        repo.getCategories().first().first { it.name == name }

    private suspend fun getAllSavedColors(): List<Long> =
        repo.getCategories().first().map { it.color }

    private fun makeCategory(
        id: String,
        sortOrder: Int = 0,
        valueType: ValueType = ValueType.None,
    ) = Category(id = id, name = id, emoji = "📌", color = 0xFFE53935L,
        valueType = valueType, unit = null, allowEmptyText = true, sortOrder = sortOrder)

    private fun makeEvent(id: String, categoryId: String) = com.trackr.app.domain.Event(
        id = id, categoryId = categoryId, timestamp = anchor,
        value = null, notes = null, imagePaths = emptyList(), createdAt = anchor)
}
