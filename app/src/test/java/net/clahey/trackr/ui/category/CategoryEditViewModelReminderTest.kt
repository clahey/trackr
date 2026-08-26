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
import net.clahey.trackr.ui.components.ReminderPermissionProblem
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

// @spec REM-UI-006, REM-UI-006a, REM-UI-009, REM-UI-010, REM-UI-011, REM-PERM-003, REM-SCHED-013, REM-SCHED-014
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryEditViewModelReminderTest {

    private data class PermissionCase(
        val notifications: Boolean,
        val channel: Boolean,
        val exactAlarms: Boolean,
        val expected: ReminderPermissionProblem,
    )

    private data class OccurrencesCase(val start: String, val typed: String, val expected: String)

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
        // An empty daysActive would fail validation if the reminder were on.
        vm.setReminderUIState(vm.reminderUIState.value.copy(enabled = false, daysActive = emptySet()))
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
    }

    // @spec REM-UI-009
    @Test fun `enabling FIXED mode with no times blocks save`() = runTest {
        fillRequiredFields()
        vm.setReminderUIState(
            vm.reminderUIState.value.copy(enabled = true, mode = ReminderMode.FIXED, times = emptyList()),
        )
        vm.save()
        assertEquals(SaveResult.ValidationError("reminder_times"), vm.saveResult.value)
    }

    // @spec REM-UI-009
    @Test fun `enabling a reminder with no active days blocks save`() = runTest {
        fillRequiredFields()
        vm.setReminderUIState(vm.reminderUIState.value.copy(enabled = true, daysActive = emptySet()))
        vm.save()
        assertEquals(SaveResult.ValidationError("reminder_days"), vm.saveResult.value)
    }

    // @spec REM-UI-009, REM-UI-010
    @Test fun `RANDOM mode with windowEnd before windowStart blocks save unless it is the midnight sentinel`() = runTest {
        fillRequiredFields()
        vm.setReminderUIState(
            vm.reminderUIState.value.copy(
                enabled = true,
                mode = ReminderMode.RANDOM,
                windowStart = LocalTime.of(20, 0),
                windowEnd = LocalTime.of(8, 0), // literal 08:00, before 20:00 — invalid
            ),
        )
        vm.save()
        assertEquals(SaveResult.ValidationError("reminder_window"), vm.saveResult.value)
    }

    // @spec REM-UI-010
    @Test fun `RANDOM mode with an untouched midnight window is valid`() = runTest {
        fillRequiredFields()
        // windowStart/windowEnd default to LocalTime.MIDNIGHT; occurrencesPerDay defaults to 1.
        vm.setReminderUIState(vm.reminderUIState.value.copy(enabled = true, mode = ReminderMode.RANDOM))
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
    }

    // A rejected keystroke leaves the field on its previous text, so each case names where the
    // field started as well as what was typed into it.
    // @spec REM-UI-006
    @Test fun `the times-per-day field accepts only empty or one or two digits`() = runTest {
        val cases = listOf(
            OccurrencesCase(start = "1", typed = "", expected = ""),
            OccurrencesCase(start = "", typed = "4", expected = "4"),
            OccurrencesCase(start = "1", typed = "12", expected = "12"),
            OccurrencesCase(start = "1", typed = "99", expected = "99"),
            // A digit, so the field takes it; REM-UI-006a is what refuses it, at save.
            OccurrencesCase(start = "1", typed = "0", expected = "0"),
            OccurrencesCase(start = "12", typed = "123", expected = "12"),
            OccurrencesCase(start = "1", typed = "1a", expected = "1"),
            OccurrencesCase(start = "1", typed = "a", expected = "1"),
            OccurrencesCase(start = "1", typed = "-1", expected = "1"),
            OccurrencesCase(start = "1", typed = "1.5", expected = "1"),
            OccurrencesCase(start = "1", typed = " ", expected = "1"),
            // Arabic-Indic four. A numeric keypad on an Arabic locale emits these, and they parse,
            // so the check is "is a digit" rather than "is between ASCII 0 and 9".
            OccurrencesCase(start = "1", typed = "٤", expected = "٤"),
        )
        for (case in cases) {
            vm.setReminderUIState(vm.reminderUIState.value.copy(occurrencesPerDay = case.start))
            assertEquals(case.toString(), case.start, vm.reminderUIState.value.occurrencesPerDay)
            vm.setReminderUIState(vm.reminderUIState.value.copy(occurrencesPerDay = case.typed))
            assertEquals(case.toString(), case.expected, vm.reminderUIState.value.occurrencesPerDay)
        }
    }

    // A blocked save leaves the ViewModel untouched, so both cases run against the one instance.
    // @spec REM-UI-006a
    @Test fun `an empty or zero times-per-day blocks a RANDOM save`() = runTest {
        fillRequiredFields()
        vm.setReminderUIState(vm.reminderUIState.value.copy(enabled = true, mode = ReminderMode.RANDOM))
        for (value in listOf("", "0")) {
            vm.setReminderUIState(vm.reminderUIState.value.copy(occurrencesPerDay = value))
            vm.save()
            assertEquals(value, SaveResult.ValidationError("reminder_occurrences"), vm.saveResult.value)
        }
    }

    // @spec REM-UI-006a
    @Test fun `an empty times-per-day does not block a FIXED save`() = runTest {
        fillRequiredFields()
        vm.setReminderUIState(
            vm.reminderUIState.value.copy(
                enabled = true,
                mode = ReminderMode.FIXED,
                times = listOf(LocalTime.of(9, 0)),
                occurrencesPerDay = "",
            ),
        )
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
        val saved = repo.getCategories().first().single { it.name == "Category" }
        // The field is not shown in FIXED mode and the domain type has no room for "unset", so an
        // emptied field stores 1 rather than propagating a value RANDOM would divide by.
        assertEquals(1, repo.getReminderForCategory(saved.id).first()!!.occurrencesPerDay)
    }

    // @spec REM-UI-006
    @Test fun `the times-per-day text is stored as the number it spells`() = runTest {
        fillRequiredFields()
        vm.setReminderUIState(
            vm.reminderUIState.value.copy(enabled = true, mode = ReminderMode.RANDOM, occurrencesPerDay = "99"),
        )
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
        val saved = repo.getCategories().first().single { it.name == "Category" }
        assertEquals(99, repo.getReminderForCategory(saved.id).first()!!.occurrencesPerDay)
    }

    // The section seeds with Reminder.default()'s values, so an untouched save and a save of a
    // deliberately-default reminder look identical in the state — only the edit flag separates them.
    // @spec REM-UI-012
    @Test fun `saving without touching the Reminder section writes no reminder row`() = runTest {
        fillRequiredFields()
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
        val saved = vm.savedCategoryId.value!!
        assertNull(repo.getReminderForCategory(saved).first())
    }

    // @spec REM-UI-012
    @Test fun `saving without touching the Reminder section leaves a stored reminder alone`() = runTest {
        val category = Category.MetaCategory(
            id = "cat1", name = "Category", emoji = "📌", color = 0xFFE53935L,
            valueType = ValueType.None, defaultValue = null, allowEmptyText = true, sortOrder = 0,
        )
        repo.setCategories(category)
        val stored = Reminder(
            categoryId = "cat1", enabled = true, mode = ReminderMode.RANDOM,
            times = emptyList(), windowStart = LocalTime.of(9, 0), windowEnd = LocalTime.of(21, 0),
            occurrencesPerDay = 4, daysActive = setOf(DayOfWeek.MONDAY),
            showCategoryInNotification = true, nextFireAt = null,
        )
        repo.setReminders(stored)
        vm = CategoryEditViewModel(
            repo,
            ReminderScheduler(repo, alarms, FakeReminderNotifier(), FakePreferencesDataStore()),
            SavedStateHandle(mapOf("categoryId" to "cat1")),
        )
        vm.setName("Renamed")
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
        assertEquals(stored, repo.getReminderForCategory("cat1").first())
    }

    // @spec REM-UI-012
    @Test fun `saving after editing the Reminder section writes the row`() = runTest {
        fillRequiredFields()
        vm.setReminderUIState(vm.reminderUIState.value.copy(enabled = true))
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
        val saved = repo.getReminderForCategory(vm.savedCategoryId.value!!).first()
        assertEquals(true, saved?.enabled)
    }

    // @spec REM-UI-006
    @Test fun `a stored times-per-day seeds the field as text`() = runTest {
        val category = Category.MetaCategory(
            id = "cat1", name = "Category", emoji = "📌", color = 0xFFE53935L,
            valueType = ValueType.None, defaultValue = null, allowEmptyText = true, sortOrder = 0,
        )
        repo.setCategories(category)
        repo.saveReminder(
            Reminder(
                categoryId = "cat1", enabled = true, mode = ReminderMode.RANDOM,
                times = emptyList(), windowStart = LocalTime.MIDNIGHT, windowEnd = LocalTime.MIDNIGHT,
                occurrencesPerDay = 7, daysActive = DayOfWeek.entries.toSet(),
                showCategoryInNotification = false, nextFireAt = null,
            ),
        )
        vm = CategoryEditViewModel(
            repo,
            ReminderScheduler(repo, alarms, FakeReminderNotifier(), FakePreferencesDataStore()),
            SavedStateHandle(mapOf("categoryId" to "cat1")),
        )
        assertEquals("7", vm.reminderUIState.value.occurrencesPerDay)
    }

    // A blocked save leaves the ViewModel untouched, so every case runs against the one instance.
    // Exact-alarm availability is set on the fake, not passed to save — REM-SCHED-021.
    // @spec REM-PERM-003, REM-PERM-006, REM-SCHED-021
    @Test fun `each permission problem blocks the save and is named in the confirmation`() = runTest {
        val cases = listOf(
            PermissionCase(notifications = false, channel = true, exactAlarms = true,
                expected = ReminderPermissionProblem.NotificationsDisabled),
            PermissionCase(notifications = true, channel = false, exactAlarms = true,
                expected = ReminderPermissionProblem.ReminderChannelDisabled),
            PermissionCase(notifications = true, channel = true, exactAlarms = false,
                expected = ReminderPermissionProblem.ExactAlarmsUnavailable),
        )
        fillRequiredFields()
        vm.setReminderUIState(vm.reminderUIState.value.copy(enabled = true))
        for (case in cases) {
            alarms.setExactAvailable(case.exactAlarms)
            vm.save(
                notificationPermissionGranted = case.notifications,
                reminderChannelEnabled = case.channel,
            )
            assertEquals(case.toString(), case.expected, vm.pendingPermissionConfirmation.value)
            assertEquals(case.toString(), SaveResult.Idle, vm.saveResult.value)
        }
        assertNull(repo.getCategories().first().find { it.name == "Category" })
    }

    // @spec REM-PERM-003
    @Test fun `forceSaveDespitePermission proceeds despite missing permission`() = runTest {
        fillRequiredFields()
        vm.setReminderUIState(vm.reminderUIState.value.copy(enabled = true))
        vm.save(notificationPermissionGranted = false, forceSaveDespitePermission = true)
        assertNull(vm.pendingPermissionConfirmation.value)
        assertEquals(SaveResult.Success, vm.saveResult.value)
    }

    // @spec REM-PERM-003
    @Test fun `missing permissions do not block a save with the reminder off`() = runTest {
        fillRequiredFields()
        vm.setReminderUIState(vm.reminderUIState.value.copy(enabled = false))
        alarms.setExactAvailable(false)
        vm.save(
            notificationPermissionGranted = false,
            reminderChannelEnabled = false,
        )
        assertNull(vm.pendingPermissionConfirmation.value)
        assertEquals(SaveResult.Success, vm.saveResult.value)
    }

    // @spec REM-SCHED-013
    @Test fun `saving with an enabled reminder arms the alarm`() = runTest {
        fillRequiredFields()
        vm.setReminderUIState(
            vm.reminderUIState.value.copy(
                enabled = true,
                mode = ReminderMode.FIXED,
                times = listOf(LocalTime.of(9, 0)),
            ),
        )
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
        vm.setReminderUIState(vm.reminderUIState.value.copy(enabled = false))
        vm.save()
        assertEquals(SaveResult.Success, vm.saveResult.value)
        assertEquals(listOf("cat1"), alarms.cancelCalls)
    }

    // @spec REM-DATA-003, REM-DATA-004
    @Test fun `a category with no reminder row seeds the UI state defaults`() = runTest {
        repo.setCategories(
            Category.MetaCategory(
                id = "cat1", name = "Category", emoji = "📌", color = 0xFFE53935L,
                valueType = ValueType.None, defaultValue = null, allowEmptyText = true, sortOrder = 0,
            ),
        )
        vm = CategoryEditViewModel(
            repo,
            ReminderScheduler(repo, alarms, FakeReminderNotifier(), FakePreferencesDataStore()),
            SavedStateHandle(mapOf("categoryId" to "cat1")),
        )

        val state = vm.reminderUIState.value
        assertFalse(state.showCategoryInNotification)
        assertEquals(DayOfWeek.entries.toSet(), state.daysActive)
    }
}
