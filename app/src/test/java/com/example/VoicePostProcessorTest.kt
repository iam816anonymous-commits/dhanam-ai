package com.example

import com.example.voice.VoicePostProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePostProcessorTest {

    @Test
    fun testChocolateReplacedWithRupayaluInMonetaryContext() {
        // 1. Amount + chocolate + monetary action verb ("ఇచ్చాను")
        val input1 = "500 chocolate ఇచ్చాను"
        val output1 = VoicePostProcessor.filterTranscript(input1, isTelugu = true)
        assertEquals("500 రూపాయలు ఇచ్చాను", output1)

        // 2. Person + amount + chocolate + action verb
        val input2 = "రమేష్ కి 500 chocolate ఇచ్చాను"
        val output2 = VoicePostProcessor.filterTranscript(input2, isTelugu = true)
        assertEquals("రమేష్ కి 500 రూపాయలు ఇచ్చాను", output2)

        // 3. Amount + chocolate + expense verb ("ఖర్చు")
        val input3 = "200 chocolate ఖర్చు"
        val output3 = VoicePostProcessor.filterTranscript(input3, isTelugu = true)
        assertEquals("200 రూపాయలు ఖర్చు", output3)

        // 4. Large amount with chocolate
        val input4 = "1000 chocolate"
        val output4 = VoicePostProcessor.filterTranscript(input4, isTelugu = true)
        assertEquals("1000 రూపాయలు", output4)
    }

    @Test
    fun testRealChocolatePurchasesPreserved() {
        // 1. Explicit currency already present ("50 రూపాయల చాక్లెట్")
        val input1 = "50 రూపాయల చాక్లెట్"
        val output1 = VoicePostProcessor.filterTranscript(input1, isTelugu = true)
        assertTrue(output1.contains("చాక్లెట్"))
        assertTrue(output1.contains("రూపాయల"))

        // 2. Purchase verb present ("చాక్లెట్ కొన్నాను")
        val input2 = "చాక్లెట్ కొన్నాను 50"
        val output2 = VoicePostProcessor.filterTranscript(input2, isTelugu = true)
        assertTrue(output2.contains("చాక్లెట్"))

        // 3. Item followed by rupees
        val input3 = "చాక్లెట్ 20 రూపాయలు"
        val output3 = VoicePostProcessor.filterTranscript(input3, isTelugu = true)
        assertTrue(output3.contains("చాక్లెట్"))
    }

    @Test
    fun testTeluguPhoneticArtifactsCorrection() {
        // "ఆపు ఇచ్చాను" -> "అప్పు ఇచ్చాను"
        val input1 = "రమేష్ కి 500 ఆపు ఇచ్చాను"
        val output1 = VoicePostProcessor.filterTranscript(input1, isTelugu = true)
        assertTrue(output1.contains("అప్పు ఇచ్చాను"))

        // "కార్చు" -> "ఖర్చు"
        val input2 = "పెట్రోల్ 300 కార్చు"
        val output2 = VoicePostProcessor.filterTranscript(input2, isTelugu = true)
        assertTrue(output2.contains("ఖర్చు"))

        // "బాకి" -> "బాకీ"
        val input3 = "సురేష్ బాకి ఇచ్చాడు"
        val output3 = VoicePostProcessor.filterTranscript(input3, isTelugu = true)
        assertTrue(output3.contains("బాకీ"))
    }

    @Test
    fun testTeluguNumberWordsNormalization() {
        val input1 = "రెండు వందల బిర్యానీ"
        val output1 = VoicePostProcessor.filterTranscript(input1, isTelugu = true)
        assertTrue(output1.contains("200"))

        val input2 = "ఐదు వందల పెట్రోల్"
        val output2 = VoicePostProcessor.filterTranscript(input2, isTelugu = true)
        assertTrue(output2.contains("500"))

        val input3 = "రమేష్ కి వెయ్యి ఇచ్చాను"
        val output3 = VoicePostProcessor.filterTranscript(input3, isTelugu = true)
        assertTrue(output3.contains("1000"))
    }
}
