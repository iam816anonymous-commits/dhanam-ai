package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
    fun coreScreenActionEntryPointsAreVisible() {
        composeRule.onNodeWithTag("nav_item_expenses").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_expense_fab").assertExists()
        composeRule.onNodeWithTag("expense_search_field").assertExists()

        composeRule.onNodeWithTag("nav_item_khata").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_loan_fab").assertExists()
        composeRule.onNodeWithTag("khata_search_field").assertExists()

        composeRule.onNodeWithTag("nav_item_insights").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("export_pdf_button").assertExists()
        composeRule.onNodeWithTag("export_csv_button").assertExists()

        composeRule.onNodeWithTag("nav_item_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Import expense history (CSV)").assertExists()
        composeRule.onNodeWithText("Agent self-test").assertExists()
        composeRule.onNodeWithText("Check on-device AI support").assertExists()
        composeRule.onNodeWithText("Download Gemini Nano model").assertExists()
        composeRule.onNodeWithText("Use local rules only").assertExists()
    }

    @Test
    fun invalidFinancialFormInputShowsValidationInsteadOfSavingFallbackValues() {
        composeRule.onNodeWithTag("nav_item_expenses").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_expense_fab").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Enter a valid amount greater than zero.").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithTag("nav_item_khata").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_loan_fab").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Enter the person's name.").assertExists()
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
