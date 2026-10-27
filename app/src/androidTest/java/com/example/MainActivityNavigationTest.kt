package com.example

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyBottomNavigationScreenRenders() {
        val destinations = listOf(
            "expenses" to "expenses_screen",
            "khata" to "khata_screen",
            "insights" to "insights_pdf_screen",
            "settings" to "settings_screen",
            "agent" to "agent_screen"
        )

        destinations.forEach { (route, screenTag) ->
            composeRule.onNodeWithTag("nav_item_$route").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(screenTag).assertExists()
        }
    }

    @Test
    fun settingsExposesCsvImportAndReadOnlyDiagnostics() {
        composeRule.onNodeWithTag("nav_item_settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("csv_import_card").assertExists()
        composeRule.onNodeWithTag("agent_self_test_card").assertExists()
        composeRule.onNodeWithTag("run_agent_self_test").assertExists()
    }
}
