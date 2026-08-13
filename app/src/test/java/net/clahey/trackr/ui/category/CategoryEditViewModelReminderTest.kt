package net.clahey.trackr.ui.category

import androidx.lifecycle.SavedStateHandle
import net.clahey.trackr.FakeTrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Reminder
import net.clahey.trackr.domain.ReminderMode
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.reminders.FakeAlarmScheduler
import net.clahey.trackr.reminders.FakePreferencesDataStore
import net.clahey.trackr.reminders.FakeReminderNotifier
import net.clahey.trackr.reminders.ReminderScheduler
import net.clahey.trackr.ui.SaveResult
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
import java.time.LocalTime

// @spec REM-UI-009, REM-UI-010, REM-UI-011, REM-PERM-003, REM-SCHED-013, REM-SCHED-014
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryEditViewModelReminderTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeTrackrRepository
    private lateinit var alarms: FakeAlarmScheduler
    private lateinit var vm: CategoryEditViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTrackrRepository()
        alarms = FakeAlarmScheduler()
        val scheduler = ReminderScheduler(repo, alarms, FakeReminderNotifier(), FakePreferencesDataStore())
        vm = CategoryEditViewModel(repo, scheduler, SavedStateHandle())
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun fillRequiredFields() {
        vm.setName("Category")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "📌"))
    }

    // @spec REM-UI-011
    @Test fun `saving with reminder off never validates reminder fields`() = runTest {
        fillRequiredFields()
        vm.setReminderEnabled(false)
        vm.setReminderDaysActive(emptySet()) // would fail validation if the reminder were on
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
    }

    // @spec REM-UI-009
    @Test fun `enabling FIXED mode with no times blocks save`() = runTest {
        fillRequiredFields()
        vm.setReminderEnabled(true)
        vm.setReminderMode(ReminderMode.FIXED)
        vm.setReminderTimes(emptyList())
        vm.save()
        assertEquals(SaveResult.ValidationError("reminder_times"), vm.saveResult.value)
    }

    // @spec REM-UI-009
    @Test fun `enabling a reminder with no active days blocks save`() = runTest {
        fillRequiredFields()
        vm.setReminderEnabled(true)
        vm.setReminderDaysActive(emptySet())
        vm.save()
        assertEquals(SaveResult.ValidationError("reminder_days"), vm.saveResult.value)
    }

    // @spec REM-UI-009, REM-UI-010
    @Test fun `RANDOM mode with windowEnd before windowStart blocks save unless it is the midnight sentinel`() = runTest {
        fillRequiredFields()
        vm.setReminderEnabled(true)
        vm.setReminderMode(ReminderMode.RANDOM)
        vm.setReminderWindowStart(LocalTime.of(20, 0))
        vm.setReminderWindowEnd(LocalTime.of(8, 0)) // literal 08:00, before 20:00 — invalid
        vm.save()
        assertEquals(SaveResult.ValidationError("reminder_window"), vm.saveResult.value)
    }

    // @spec REM-UI-010
    @Test fun `RANDOM mode with an untouched midnight window is valid`() = runTest {
        fillRequiredFields()
        vm.setReminderEnabled(true)
        vm.setReminderMode(ReminderMode.RANDOM)
        // windowStart/windowEnd default to LocalTime.MIDNIGHT; occurrencesPerDay defaults to 1.
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
    }

    // @spec REM-PERM-003
    @Test fun `enabling a reminder without notification permission blocks save and requests confirmation`() = runTest {
        fillRequiredFields()
        vm.setReminderEnabled(true)
        vm.save(notificationPermissionGranted = false, exactAlarmAvailable = true)
        assertTrue(vm.pendingPermissionConfirmation.value)
        assertEquals(SaveResult.Idle, vm.saveResult.value)
        assertNull(repo.getCategories().first().find { it.name == "Category" })
    }

    // @spec REM-PERM-003
    @Test fun `forceSaveDespitePermission proceeds despite missing permission`() = runTest {
        fillRequiredFields()
        vm.setReminderEnabled(true)
        vm.save(notificationPermissionGranted = false, exactAlarmAvailable = true, forceSaveDespitePermission = true)
        assertFalse(vm.pendingPermissionConfirmation.value)
        assertEquals(SaveResult.Success, vm.saveResult.value)
    }

    // @spec REM-SCHED-013
    @Test fun `saving with an enabled reminder arms the alarm`() = runTest {
        fillRequiredFields()
        vm.setReminderEnabled(true)
        vm.setReminderMode(ReminderMode.FIXED)
        vm.setReminderTimes(listOf(LocalTime.of(9, 0)))
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
        assertTrue(alarms.armCalls.isNotEmpty())
    }

    // @spec REM-SCHED-014
    @Test fun `saving with the reminder toggled off cancels the alarm`() = runTest {
        val category = Category.MetaCategory(
            id = "cat1", name = "Category", emoji = "📌", color = 0xFFE53935L,
            valueType = ValueType.None, defaultValue = null, allowEmptyText = true, sortOrder = 0,
        )
        repo.setCategories(category)
        repo.saveReminder(
            Reminder(
                categoryId = "cat1", enabled = true, mode = ReminderMode.FIXED,
                times = listOf(LocalTime.of(9, 0)), windowStart = LocalTime.MIDNIGHT, windowEnd = LocalTime.MIDNIGHT,
                occurrencesPerDay = 1, daysActive = DayOfWeek.entries.toSet(),
                showCategoryInNotification = false, nextFireAt = null,
            ),
        )
        vm = CategoryEditViewModel(
            repo,
            ReminderScheduler(repo, alarms, FakeReminderNotifier(), FakePreferencesDataStore()),
            SavedStateHandle(mapOf("categoryId" to "cat1")),
        )
        vm.setReminderEnabled(false)
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
        assertEquals(listOf("cat1"), alarms.cancelCalls)
    }
}
