package com.atomgo.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class HomeNavigationUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginAsAdmin_opensAdminHome() {
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performTextClearance()
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performTextInput("admin_ip")

        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performTextClearance()
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performTextInput("adminip123")

        composeRule.onNodeWithTag("login_submit_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_home_title", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
    }

    @Test
    fun loginAsAdminClassic_opensAdminHome() {
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performTextReplacement("admin")

        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performTextReplacement("admin123")

        composeRule.onNodeWithTag("login_submit_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_home_title", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag("admin_home_title", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_search_field", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_filter_all", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_filter_soon_return", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_filter_debtors", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_filter_mine", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_tab_rents", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_tab_clients", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_tab_bikes", useUnmergedTree = true).assertIsDisplayed()
        // Keep screen for emulator screenshot capture in validation runs.
        Thread.sleep(3000)
    }

    @Ignore("Requires stable seeded client credentials on backend environment")
    @Test
    fun loginAsClient_opensClientHome() {
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performTextClearance()
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performTextInput("ip.ui.54fz")

        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performTextClearance()
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performTextInput("client123")

        composeRule.onNodeWithTag("login_submit_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag("client_home_title", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
    }
}
