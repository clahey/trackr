package net.clahey.trackr.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class TimePickerDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var confirmed: LocalTime? = null
    private var dismissed = false

    private fun showDialog(initial: LocalTime) {
        composeTestRule.setContent {
            TimePickerDialog(
                initial = initial,
                onConfirm = { confirmed = it },
                onDismiss = { dismissed = true },
            )
        }
    }

    // @spec REM-UI-004, REM-UI-005, EL-UI-032
    @Test
    fun confirmReportsTheSeededTime() {
        showDialog(LocalTime.of(14, 30))

        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(LocalTime.of(14, 30), confirmed)
        assertFalse(dismissed)
    }

    // Minutes rather than hours: the hour dial's labels depend on whether the device is in 12- or
    // 24-hour mode, and minutes read the same either way.
    // @spec REM-UI-004, REM-UI-005, EL-UI-032
    @Test
    fun confirmReportsTheTimeThePickerWasMovedTo() {
        showDialog(LocalTime.of(14, 30))

        composeTestRule.onNodeWithContentDescription("Select minutes", substring = true).performClick()
        composeTestRule.onNodeWithContentDescription("35 minutes").performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(LocalTime.of(14, 35), confirmed)
    }

    // @spec REM-UI-004, REM-UI-005, EL-UI-032
    @Test
    fun cancelReportsADismissalAndNoTime() {
        showDialog(LocalTime.of(14, 30))

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertTrue(dismissed)
        assertNull(confirmed)
    }
}
