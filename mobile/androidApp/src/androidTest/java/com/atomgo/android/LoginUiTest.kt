package com.atomgo.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class LoginUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginScreen_elementsExistAndCanType() {
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("login_submit_button", useUnmergedTree = true).assertExists()

        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performTextClearance()
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performTextInput("admin")

        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performTextClearance()
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performTextInput("admin123")

        composeRule.onNodeWithTag("login_submit_button", useUnmergedTree = true)
            .performClick()
    }
}
