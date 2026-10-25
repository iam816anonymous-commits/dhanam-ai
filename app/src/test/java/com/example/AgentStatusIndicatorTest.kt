package com.example

import com.example.ui.components.JarvisCoreState
import com.example.ui.components.getAgentStateStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentStatusIndicatorTest {

    @Test
    fun testAllStatesHaveValidStyles() {
        for (state in JarvisCoreState.entries) {
            val style = getAgentStateStyle(state)
            assertNotNull("Style should not be null for state $state", style)
            assertTrue("Title EN should not be blank for state $state", style.titleEn.isNotBlank())
            assertTrue("Title TE should not be blank for state $state", style.titleTe.isNotBlank())
            assertTrue("Pulse duration should be positive for state $state", style.pulseDurationMs > 0)
            assertTrue("Icon emoji should not be blank for state $state", style.iconEmoji.isNotBlank())
        }
    }

    @Test
    fun testThinkingStateStyling() {
        val style = getAgentStateStyle(JarvisCoreState.THINKING)
        assertEquals("Thinking...", style.titleEn)
        assertEquals("ఆలోచిస్తోంది...", style.titleTe)
        assertEquals("🧠", style.iconEmoji)
    }

    @Test
    fun testProcessingStateStyling() {
        val style = getAgentStateStyle(JarvisCoreState.PROCESSING)
        assertEquals("Processing...", style.titleEn)
        assertEquals("లెక్కిస్తోంది...", style.titleTe)
        assertEquals("⚡", style.iconEmoji)
    }

    @Test
    fun testListeningStateStyling() {
        val style = getAgentStateStyle(JarvisCoreState.LISTENING)
        assertEquals("Listening...", style.titleEn)
        assertEquals("వింటోంది...", style.titleTe)
        assertEquals("🎙️", style.iconEmoji)
    }

    @Test
    fun testIdleStateStyling() {
        val style = getAgentStateStyle(JarvisCoreState.IDLE)
        assertEquals("Jarvis Ready", style.titleEn)
        assertEquals("జార్విస్ సిద్ధం", style.titleTe)
        assertEquals("✨", style.iconEmoji)
    }
}
