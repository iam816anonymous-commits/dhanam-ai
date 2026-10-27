package com.example.ai

import com.example.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentCapabilityRegistryTest {
    @Test
    fun everyAppScreenHasAResolvableNavigationCapability() {
        val navigation = AgentCapabilityRegistry.all.filter { it.action == AgentActionKind.NAVIGATION }

        assertEquals(Screen.entries.toSet(), navigation.map { it.screen }.toSet())
        navigation.forEach { capability ->
            assertEquals(
                capability.screen,
                AgentCapabilityRegistry.resolveNavigation(capability.aliases.first())?.screen
            )
        }
    }

    @Test
    fun everyDestructiveCapabilityRequiresConfirmation() {
        assertTrue(AgentCapabilityRegistry.validate().isEmpty())
        assertTrue(
            AgentCapabilityRegistry.all
                .filter { it.destructive }
                .all { it.requiresConfirmation }
        )
    }

    @Test
    fun longerAliasesWinOverGenericAliases() {
        assertEquals(Screen.EXPENSES, AgentCapabilityRegistry.resolveNavigation("please show expenses")?.screen)
        assertEquals(Screen.KHATA, AgentCapabilityRegistry.resolveNavigation("open lending khata")?.screen)
        assertEquals(Screen.SETTINGS, AgentCapabilityRegistry.resolveNavigation("open settings")?.screen)
    }
}
