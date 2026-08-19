package net.clahey.trackr.ui.components

import net.clahey.trackr.ui.components.ReminderPermissionProblem.ExactAlarmsUnavailable
import net.clahey.trackr.ui.components.ReminderPermissionProblem.NotificationsDisabled
import net.clahey.trackr.ui.components.ReminderPermissionProblem.ReminderChannelDisabled
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPermissionProblemTest {

    private data class Case(
        val notificationsEnabled: Boolean,
        val reminderChannelEnabled: Boolean,
        val exactAlarmAvailable: Boolean,
        val expected: ReminderPermissionProblem?,
    )

    // @spec REM-PERM-006
    @Test fun `the most severe problem present is the one reported`() {
        val cases = listOf(
            Case(true, true, true, null),
            Case(true, true, false, ExactAlarmsUnavailable),
            Case(true, false, true, ReminderChannelDisabled),
            Case(true, false, false, ReminderChannelDisabled),
            Case(false, true, true, NotificationsDisabled),
            Case(false, true, false, NotificationsDisabled),
            Case(false, false, true, NotificationsDisabled),
            Case(false, false, false, NotificationsDisabled),
        )
        for (case in cases) {
            assertEquals(
                case.toString(),
                case.expected,
                reminderPermissionProblem(
                    notificationsEnabled = case.notificationsEnabled,
                    reminderChannelEnabled = case.reminderChannelEnabled,
                    exactAlarmAvailable = case.exactAlarmAvailable,
                ),
            )
        }
    }
}
