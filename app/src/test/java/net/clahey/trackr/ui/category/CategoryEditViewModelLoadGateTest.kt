package net.clahey.trackr.ui.category

import androidx.lifecycle.SavedStateHandle
import net.clahey.trackr.FakeTrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Reminder
import net.clahey.trackr.domain.ReminderMode
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.reminders.testReminderScheduler
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

// @spec CAT-UI-018
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryEditViewModelLoadGateTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeTrackrRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTrackrRepository()
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun category(id: String) = Category.MetaCategory(
        id = id, name = "Water", emoji = "💧", color = 0xFF0000FFL,
        valueType = ValueType.None, defaultValue = null, allowEmptyText = true, sortOrder = 0,
    )

    private fun reminder(categoryId: String, nextFireAt: Instant? = null) = Reminder(
        categoryId = categoryId,
        enabled = true,
        mode = ReminderMode.FIXED,
        times = listOf(LocalTime.of(7, 30)),
        windowStart = LocalTime.MIDNIGHT,
        windowEnd = LocalTime.MIDNIGHT,
        occurrencesPerDay = 1,
        daysActive = setOf(DayOfWeek.MONDAY),
        showCategoryInNotification = true,
        nextFireAt = nextFireAt,
    )

    private fun editVm(categoryId: String) = CategoryEditViewModel(
        repo, testReminderScheduler(repo), SavedStateHandle(mapOf("categoryId" to categoryId)),
    )

    // A category with no reminder row is the common case; the reminder read completing with null
    // has to open the gate, or the screen stays disabled forever.
    // @spec CAT-UI-018
    @Test fun `gate opens when the category has no reminder row`() = runTest {
        repo.saveCategory(category("c1"))
        val vm = editVm("c1")
        assertTrue(vm.isLoaded.value)
    }

    // @spec CAT-UI-018
    @Test fun `gate stays closed until every read has completed`() = runTest {
        repo.saveCategory(category("c1"))
        repo.saveReminder(reminder("c1"))
        val gate = CompletableDeferred<Unit>()
        repo.reminderReadGate = gate

        val vm = editVm("c1")
        assertFalse(vm.isLoaded.value)

        gate.complete(Unit)
        assertTrue(vm.isLoaded.value)
    }

    // CAT-UI-017's navigation must not wait on the reminder read.
    // @spec CAT-UI-018, CAT-UI-017
    @Test fun `missing category navigates back without waiting for the reminder read`() = runTest {
        repo.reminderReadGate = CompletableDeferred()

        val vm = editVm("nonexistent")

        assertTrue(vm.navigateBack.value)
        assertFalse(vm.isLoaded.value)
    }

    // @spec CAT-UI-018
    @Test fun `field edits are ignored while loading`() = runTest {
        repo.saveCategory(category("c1"))
        val gate = CompletableDeferred<Unit>()
        repo.categoryReadGate = gate

        val vm = editVm("c1")
        vm.setName("typed while loading")
        vm.setReminderEnabled(true)

        gate.complete(Unit)

        assertEquals("Water", vm.name.value)
        assertFalse(vm.reminderUIState.value.enabled)
        assertFalse(vm.hasUserEdits.value)
    }

    // @spec CAT-UI-018
    @Test fun `save while loading persists nothing`() = runTest {
        repo.saveCategory(category("c1"))
        repo.saveReminder(reminder("c1"))
        val gate = CompletableDeferred<Unit>()
        repo.categoryReadGate = gate

        val vm = editVm("c1")
        vm.save()
        assertFalse(vm.isLoaded.value)

        // Reopen before asserting — the reads below would otherwise wait on this same gate.
        gate.complete(Unit)

        assertEquals("Water", repo.getCategoryById("c1").first()!!.name)
        assertTrue(repo.getReminderForCategory("c1").first()!!.enabled)
    }

    // The reminders segment writes nextFireAt independently of this screen; a write landing between
    // the two reads must not be visible in the seeded UI state (it is not a UI-state field).
    // @spec CAT-UI-018
    @Test fun `nextFireAt written between the two reads does not affect reminder UI state`() = runTest {
        repo.saveCategory(category("c1"))
        repo.saveReminder(reminder("c1"))
        val gate = CompletableDeferred<Unit>()
        repo.reminderReadGate = gate

        val vm = editVm("c1")
        repo.saveReminder(reminder("c1", nextFireAt = Instant.parse("2026-08-14T07:30:00Z")))
        gate.complete(Unit)

        assertTrue(vm.isLoaded.value)
        val state = vm.reminderUIState.value
        assertEquals(ReminderMode.FIXED, state.mode)
        assertEquals(listOf(LocalTime.of(7, 30)), state.times)
        assertEquals(setOf(DayOfWeek.MONDAY), state.daysActive)
        assertTrue(state.enabled)
        assertTrue(state.showCategoryInNotification)
    }

    // Guards against create mode being left permanently gated; it does not exercise the waiting
    // itself, since the color-index read has no gate hook.
    // @spec CAT-UI-018
    @Test fun `create mode gate opens`() = runTest {
        val vm = CategoryEditViewModel(repo, testReminderScheduler(repo), SavedStateHandle())
        assertTrue(vm.isLoaded.value)
        assertNull(vm.parentCategory.value)
    }
}
