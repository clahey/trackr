package com.trackr.app.ui.home

import com.trackr.app.FakeImageStore
import com.trackr.app.FakeTrackrRepository
import com.trackr.app.domain.Category
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
    ) = Category.MetaCategory(
        id = id, name = id, emoji = "📌", color = 0xFFE53935L,
        valueType = valueType, unit = null, allowEmptyText = allowEmptyText, sortOrder = 0,
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

    // @spec EL-PROC-001
    @Test fun `reset deletes captured but unsaved image`() {
        val path = "/images/unsaved.jpg"
        vm.imagePath.value = path
        vm.reset()
        assertTrue(imageStore.wasDeleted(path))
    }

    // @spec EL-PROC-001
    @Test fun `reset clears form state`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        vm.selectCategory(cat)
        vm.notes.value = "some notes"
        vm.imagePath.value = "/images/capture.jpg"
        vm.reset()
        assertNull(vm.selectedCategory.value)
        assertEquals("", vm.notes.value)
        assertNull(vm.imagePath.value)
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
        vm.value.value = EventValue.TextValue("")
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.ValidationError)
    }

    // @spec EL-UI-052b
    @Test fun `save with no value for Number type produces validation error`() = runTest {
        val cat = makeCategory("c1", valueType = ValueType.Number)
        repo.setCategories(cat)
        vm.selectCategory(cat)
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.ValidationError)
    }

    // @spec EL-UI-055b
    @Test fun `save with no value for Duration type produces validation error`() = runTest {
        val cat = makeCategory("c1", valueType = ValueType.Duration)
        repo.setCategories(cat)
        vm.selectCategory(cat)
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.ValidationError)
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
    @Test fun `selectCategory clears expandedMetaCategoryId`() = runTest {
        val cat = makeCategory("c1")
        repo.setCategories(cat)
        vm.expandMetaCategory("c1")
        vm.selectCategory(cat)
        assertNull(vm.expandedMetaCategoryId.value)
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
}
