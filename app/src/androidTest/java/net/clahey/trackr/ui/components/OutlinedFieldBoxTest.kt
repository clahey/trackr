package net.clahey.trackr.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val hasErrorSemantics = SemanticsMatcher("has error semantics") {
    it.config.contains(SemanticsProperties.Error)
}
private val hasNoErrorSemantics = SemanticsMatcher("has no error semantics") {
    !it.config.contains(SemanticsProperties.Error)
}

@RunWith(AndroidJUnit4::class)
class OutlinedFieldBoxTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // @spec CAT-UI-074, CAT-UI-075
    @Test
    fun labelAndContentAreDisplayed() {
        composeTestRule.setContent {
            OutlinedFieldBox(label = "Emoji") {
                Text("content", modifier = Modifier.testTag("inner_content"))
            }
        }
        composeTestRule.onNodeWithText("Emoji").assertIsDisplayed()
        composeTestRule.onNodeWithTag("inner_content").assertIsDisplayed()
    }

    // @spec CAT-UI-076
    @Test
    fun errorSemanticsSetWhenIsErrorTrue() {
        composeTestRule.setContent {
            OutlinedFieldBox(label = "Emoji", isError = true, modifier = Modifier.testTag("box")) {
                Text("content")
            }
        }
        composeTestRule.onNodeWithTag("box").assert(hasErrorSemantics)
    }

    // @spec CAT-UI-076
    @Test
    fun noErrorSemanticsWhenIsErrorFalse() {
        composeTestRule.setContent {
            OutlinedFieldBox(label = "Emoji", isError = false, modifier = Modifier.testTag("box")) {
                Text("content")
            }
        }
        composeTestRule.onNodeWithTag("box").assert(hasNoErrorSemantics)
    }
}
