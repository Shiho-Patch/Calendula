package de.jeanlucmakiola.calendula.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.jeanlucmakiola.calendula.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun rationale_renders_title_and_button() {
        composeTestRule.setContent {
            PermissionScreen(onGranted = {})
        }
        composeTestRule.onNodeWithText(res.getString(R.string.permission_rationale_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(res.getString(R.string.permission_request_button))
            .assertIsDisplayed()
    }
}
