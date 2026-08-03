package net.clahey.trackr.ui.components

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ValueInputFieldFocusTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // @spec EL-UI-058
    @Test
    fun numberFieldFocusedWhenAutoFocusTrue() {
        composeTestRule.setContent {
            ValueInputField(uiState = ValueUIState.Number("", ""), onStateChange = {}, autoFocus = true)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("value_input_field").assertIsFocused()
    }

    // @spec EL-UI-058
    @Test
    fun textFieldFocusedWhenAutoFocusTrue() {
        composeTestRule.setContent {
            ValueInputField(uiState = ValueUIState.Text(""), onStateChange = {}, autoFocus = true)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("value_input_field").assertIsFocused()
    }

    // @spec EL-UI-058
    @Test
    fun durationHoursFieldFocusedWhenAutoFocusTrue() {
        composeTestRule.setContent {
            ValueInputField(uiState = ValueUIState.Duration("", "", "0"), onStateChange = {}, autoFocus = true)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("value_duration_h").assertIsFocused()
    }

    // @spec EL-UI-058
    @Test
    fun numberFieldNotFocusedByDefault() {
        composeTestRule.setContent {
            ValueInputField(uiState = ValueUIState.Number("", ""), onStateChange = {})
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("value_input_field").assertIsNotFocused()
    }
}
