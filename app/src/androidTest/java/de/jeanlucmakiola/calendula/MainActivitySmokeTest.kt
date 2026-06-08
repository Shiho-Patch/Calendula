package de.jeanlucmakiola.calendula

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke: launches MainActivity and asserts the permission rationale renders
 * when calendar access has not yet been granted. Without GrantPermissionRule
 * the system reports NOT_GRANTED on first launch so we land in PermissionScreen.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun permissionRationale_isDisplayed_onLaunch_withoutPermission() {
        composeTestRule.onNodeWithText(res.getString(R.string.permission_rationale_title))
            .assertIsDisplayed()
    }
}
