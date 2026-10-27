package com.example.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferenceManagerBudgetTest {
    private lateinit var context: Context
    private lateinit var preferences: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences("dhanam_prefs", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun budgetKeepsDecimalPrecisionWhenSaved() {
        val manager = PreferenceManager(context)
        val expected = 12345.678901
        manager.setOverallBudget(expected)

        assertEquals(expected, manager.getOverallBudget(), 0.0000001)
        assertTrue(preferences.all["overall_budget"] is String)
    }

    @Test
    fun legacyFloatBudgetIsReadAndMigratedWithoutChangingItsValue() {
        val legacyValue = 12345.67f
        preferences.edit().putFloat("overall_budget", legacyValue).commit()

        val manager = PreferenceManager(context)

        assertEquals(legacyValue.toDouble(), manager.getOverallBudget(), 0.0)
        assertTrue(preferences.all["overall_budget"] is String)
    }

    @Test
    fun invalidBudgetWritesDoNotOverwriteTheLastValidBudget() {
        val manager = PreferenceManager(context)
        manager.setOverallBudget(1200.5)

        manager.setOverallBudget(Double.NaN)
        manager.setOverallBudget(Double.POSITIVE_INFINITY)
        manager.setOverallBudget(-1.0)

        assertEquals(1200.5, manager.getOverallBudget(), 0.0)
    }
}
