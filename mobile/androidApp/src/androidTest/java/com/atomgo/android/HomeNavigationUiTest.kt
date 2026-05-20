package com.atomgo.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

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
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_home_title", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        assertAdminHomeVisible()
        captureScreen("admin-home-admin-ip.png")
    }

    @Test
    fun loginAsAdminClassic_opensAdminHome() {
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performTextReplacement("admin")

        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performTextReplacement("admin123")

        composeRule.onNodeWithTag("login_submit_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_home_title", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        assertAdminHomeVisible()
        captureScreen("admin-home-admin-classic.png")
    }

    @Test
    fun loginWithRememberedCredentials_opensAdminHome() {
        // User scenario: login/password already pre-filled by remember-me.
        composeRule.onNodeWithTag("login_submit_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_home_title", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        assertAdminHomeVisible()
        captureScreen("admin-home-remembered.png")
    }

    @Test
    fun adminRentsList_scrollsLikeIos() {
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performTextReplacement("admin")

        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performTextReplacement("admin123")

        composeRule.onNodeWithTag("login_submit_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_home_title", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }

        composeRule.onNodeWithTag("admin_rents_list", useUnmergedTree = true).assertIsDisplayed()
        captureScreen("admin-rents-scroll-top.png")

        composeRule.onNodeWithTag("admin_rents_list", useUnmergedTree = true).performTouchInput {
            swipeUp()
            swipeUp()
        }
        composeRule.waitForIdle()
        captureScreen("admin-rents-scroll-down.png")
    }

    @Test
    fun adminTabs_clientsAndBikes_openWithLists() {
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performTextReplacement("admin")

        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performTextReplacement("admin123")

        composeRule.onNodeWithTag("login_submit_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_home_title", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }

        composeRule.onNodeWithTag("admin_tab_clients", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_clients_list", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag("admin_clients_list", useUnmergedTree = true).assertIsDisplayed()
        captureScreen("admin-clients-tab.png")

        composeRule.onNodeWithTag("admin_tab_bikes", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_bikes_list", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag("admin_bikes_list", useUnmergedTree = true).assertIsDisplayed()
        captureScreen("admin-bikes-tab.png")
    }

    @Test
    fun adminCreateScreens_openOnAllTabs() {
        loginAsAdminClassicWithFallback()

        composeRule.onNodeWithTag("admin_create_button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("create_rental_sheet", useUnmergedTree = true).assertIsDisplayed()
        captureScreen("create-rental-screen.png")

        composeRule.onNodeWithTag("create_rental_client_selector", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("create_rental_client_picker_list", useUnmergedTree = true).assertIsDisplayed()
        captureScreen("create-rental-client-picker-screen.png")
        composeRule.onNodeWithTag("selection_picker_close_button", useUnmergedTree = true).performClick()

        composeRule.onNodeWithTag("create_rental_bike_selector", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("create_rental_bike_picker_list", useUnmergedTree = true).assertIsDisplayed()
        captureScreen("create-rental-bike-picker-screen.png")
        composeRule.onNodeWithTag("selection_picker_close_button", useUnmergedTree = true).performClick()

        composeRule.onNodeWithTag("create_rental_cancel_button", useUnmergedTree = true).performClick()

        composeRule.onNodeWithTag("admin_tab_clients", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_clients_list", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag("admin_clients_create_button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("create_client_sheet", useUnmergedTree = true).assertIsDisplayed()
        captureScreen("create-client-screen.png")
        composeRule.onNodeWithTag("create_client_cancel_button", useUnmergedTree = true).performClick()

        composeRule.onNodeWithTag("admin_tab_bikes", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_bikes_list", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag("admin_bikes_create_button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("create_bike_sheet", useUnmergedTree = true).assertIsDisplayed()
        captureScreen("create-bike-screen.png")
        composeRule.onNodeWithTag("create_bike_cancel_button", useUnmergedTree = true).performClick()
    }

    @Test
    fun adminDetailsScreens_openAndCapture() {
        loginAsAdminClassicWithFallback()
        var rentalCapturedFromRents = false

        if (waitForTag("admin_rent_card_first", timeoutMillis = 8_000)) {
            composeRule.onNodeWithTag("admin_rent_card_first", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 60_000) {
                runCatching {
                    composeRule.onNodeWithTag("admin_rental_details_content", useUnmergedTree = true).fetchSemanticsNode()
                }.isSuccess
            }
            composeRule.onNodeWithTag("admin_rental_details_content", useUnmergedTree = true).assertIsDisplayed()
            captureScreen("admin-rental-details-screen.png")
            rentalCapturedFromRents = true
            composeRule.onNodeWithTag("admin_rental_details_back", useUnmergedTree = true).performClick()
        }

        composeRule.onNodeWithTag("admin_tab_clients", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_clients_list", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag("admin_client_row_first", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            val hasCard = runCatching {
                composeRule.onNodeWithTag("admin_client_details_card", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
            val hasProfileSection = runCatching {
                composeRule.onNodeWithTag("admin_client_profile_section", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
            val hasLoading = runCatching {
                composeRule.onNodeWithTag("admin_client_details_loading", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
            hasCard && hasProfileSection && !hasLoading
        }
        composeRule.onNodeWithTag("admin_client_details_card", useUnmergedTree = true).assertIsDisplayed()
        composeRule.waitForIdle()
        Thread.sleep(250)
        captureScreen("admin-client-details-screen.png")

        if (waitForTag("admin_client_history_row_first", timeoutMillis = 8_000)) {
            composeRule.onNodeWithTag("admin_client_history_row_first", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 60_000) {
                runCatching {
                    composeRule.onNodeWithTag("admin_rental_details_content", useUnmergedTree = true).fetchSemanticsNode()
                }.isSuccess
            }
            if (!rentalCapturedFromRents) {
                captureScreen("admin-rental-details-screen.png")
            }
            captureScreen("admin-client-rental-details-screen.png")
            composeRule.onNodeWithTag("admin_rental_details_renter_row", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 60_000) {
                val hasCard = runCatching {
                    composeRule.onNodeWithTag("admin_client_details_card", useUnmergedTree = true).fetchSemanticsNode()
                }.isSuccess
                val hasProfileSection = runCatching {
                    composeRule.onNodeWithTag("admin_client_profile_section", useUnmergedTree = true).fetchSemanticsNode()
                }.isSuccess
                val hasLoading = runCatching {
                    composeRule.onNodeWithTag("admin_client_details_loading", useUnmergedTree = true).fetchSemanticsNode()
                }.isSuccess
                hasCard && hasProfileSection && !hasLoading
            }
            composeRule.waitForIdle()
            Thread.sleep(250)
            captureScreen("admin-rental-open-client-screen.png")
        }
    }

    @Test
    fun adminRentPipelineModes_switchAndFilterSoonReturn() {
        loginAsAdminClassicWithFallback()

        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_rent_card_avatar_first", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }

        composeRule.onNodeWithTag("admin_rent_card_avatar_first", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("admin_pipeline_mode_long_term", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_pipeline_mode_soon_return", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_pipeline_mode_mine", useUnmergedTree = true).assertIsDisplayed()
        captureScreen("admin-rent-pipeline-menu.png")

        val switched = clickFirstAvailableMode(
            "admin_pipeline_mode_soon_return",
            "admin_pipeline_mode_long_term"
        )
        if (!switched) {
            composeRule.onNodeWithTag("admin_pipeline_mode_mine", useUnmergedTree = true).performClick()
            composeRule.waitForIdle()
        }

        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_filter_soon_return", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag("admin_filter_soon_return", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_rents_container", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        captureScreen("admin-rent-soon-return-filter.png")
    }

    private fun loginAsAdminClassicWithFallback() {
        composeRule.onNodeWithTag("login_submit_button", useUnmergedTree = true).performClick()
        if (waitForTag("admin_home_title", timeoutMillis = 8_000)) return

        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performTextClearance()
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performTextInput("admin")

        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performTextClearance()
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performTextInput("admin123")

        composeRule.onNodeWithTag("login_submit_button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("admin_home_title", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long): Boolean {
        return runCatching {
            composeRule.waitUntil(timeoutMillis = timeoutMillis) {
                runCatching {
                    composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                }.isSuccess
            }
            true
        }.getOrDefault(false)
    }

    private fun clickFirstAvailableMode(vararg tags: String): Boolean {
        tags.forEach { tag ->
            val clicked = runCatching {
                composeRule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
                true
            }.getOrElse { false }
            if (clicked) return true
        }
        return false
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

    private fun assertAdminHomeVisible() {
        composeRule.onNodeWithTag("admin_home_title", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_search_field", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_filter_all", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_filter_soon_return", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_filter_debtors", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_filter_mine", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_tab_rents", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_tab_clients", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("admin_tab_bikes", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun captureScreen(fileName: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val file = File("/sdcard/Download/$fileName")
        device.takeScreenshot(file)
    }
}
