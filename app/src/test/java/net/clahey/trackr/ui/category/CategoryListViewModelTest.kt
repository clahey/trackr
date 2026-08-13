package net.clahey.trackr.ui.category

import net.clahey.trackr.FakeTrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.reminders.testReminderScheduler
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
        vm = CategoryListViewModel(repo, testReminderScheduler(repo))
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

    // @spec CAT-UI-007
    @Test fun `deleteCategory cancels the category's reminder alarm`() = runTest {
        val alarms = net.clahey.trackr.reminders.FakeAlarmScheduler()
        val scheduler = net.clahey.trackr.reminders.ReminderScheduler(
            repo, alarms, net.clahey.trackr.reminders.FakeReminderNotifier(), net.clahey.trackr.reminders.FakePreferencesDataStore(),
        )
        val localVm = CategoryListViewModel(repo, scheduler)
        repo.setCategories(makeCategory("c1"))
        localVm.deleteCategory("c1")
        assertEquals(listOf("c1"), alarms.cancelCalls)
    }

    // @spec CAT-UI-007
    @Test fun `confirmDelete cancels the category's reminder alarm`() = runTest {
        val alarms = net.clahey.trackr.reminders.FakeAlarmScheduler()
        val scheduler = net.clahey.trackr.reminders.ReminderScheduler(
            repo, alarms, net.clahey.trackr.reminders.FakeReminderNotifier(), net.clahey.trackr.reminders.FakePreferencesDataStore(),
        )
        val localVm = CategoryListViewModel(repo, scheduler)
        val anchor2 = Instant.parse("2024-01-15T12:00:00Z")
        repo.setCategories(makeCategory("c1"))
        repo.setEvents(net.clahey.trackr.domain.Event("e1", "c1", anchor2, null, null, emptyList(), anchor2))
        localVm.deleteCategory("c1") // has an event -> requires confirmation
        localVm.confirmDelete()
        assertEquals(listOf("c1"), alarms.cancelCalls)
    }

    // @spec REM-PERM-004
    @Test fun `hasEnabledReminder is false when no category has an enabled reminder`() = runTest {
        assertFalse(vm.hasEnabledReminder.first())
    }

    // @spec REM-PERM-004
    @Test fun `hasEnabledReminder is true when a category has an enabled reminder`() = runTest {
        val seededRepo = FakeTrackrRepository()
        seededRepo.setCategories(makeCategory("c1"))
        seededRepo.setReminders(
            net.clahey.trackr.domain.Reminder(
                categoryId = "c1", enabled = true, mode = net.clahey.trackr.domain.ReminderMode.FIXED,
                times = listOf(java.time.LocalTime.of(9, 0)), windowStart = java.time.LocalTime.MIDNIGHT, windowEnd = java.time.LocalTime.MIDNIGHT,
                occurrencesPerDay = 1, daysActive = java.time.DayOfWeek.entries.toSet(),
                showCategoryInNotification = false, nextFireAt = null,
            ),
        )
        val seededVm = CategoryListViewModel(seededRepo, testReminderScheduler(seededRepo))
        assertTrue(seededVm.hasEnabledReminder.first())
    }

    private fun makeCategory(id: String, sortOrder: Int = 0) = Category.MetaCategory(
        id = id, name = id, emoji = "📌", color = 0xFFE53935L,
        valueType = ValueType.None, defaultValue = null, allowEmptyText = true, sortOrder = sortOrder,
    )

    private fun makeEvent(id: String, categoryId: String) = net.clahey.trackr.domain.Event(
        id = id, categoryId = categoryId, timestamp = anchor,
        value = null, notes = null, imagePaths = emptyList(), createdAt = anchor,
    )
}
