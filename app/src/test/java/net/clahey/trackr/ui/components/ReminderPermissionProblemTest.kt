package net.clahey.trackr.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderPermissionProblemTest {

    // @spec REM-PERM-006
    @Test fun `nothing to report when both permissions are in place`() {
        assertNull(reminderPermissionProblem(notificationsEnabled = true, exactAlarmAvailable = true))
    }

    // @spec REM-PERM-006
    @Test fun `disabled notifications are reported`() {
        assertEquals(
            ReminderPermissionProblem.NotificationsDisabled,
            reminderPermissionProblem(notificationsEnabled = false, exactAlarmAvailable = true),
        )
    }

    // @spec REM-PERM-006
    @Test fun `unavailable exact alarms are reported`() {
        assertEquals(
            ReminderPermissionProblem.ExactAlarmsUnavailable,
            reminderPermissionProblem(notificationsEnabled = true, exactAlarmAvailable = false),
        )
    }

    // @spec REM-PERM-006
    @Test fun `notifications outrank exact alarms when both are missing`() {
        assertEquals(
            ReminderPermissionProblem.NotificationsDisabled,
            reminderPermissionProblem(notificationsEnabled = false, exactAlarmAvailable = false),
        )
    }
}
