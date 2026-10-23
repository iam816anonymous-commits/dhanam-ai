package com.example

import com.example.ai.ConversationalFuzzyExtractor
import com.example.data.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationalFuzzyExtractorTest {

    @Test
    fun testTitleExtractionStripsCurrencyTokens() {
        // "రూపాయలు చాక్లెట్" should extract cleanly as "చాక్లెట్"
        val title1 = ConversationalFuzzyExtractor.extractTitle("రూపాయలు చాక్లెట్ 50", ExpenseCategory.FOOD, true)
        assertEquals("చాక్లెట్", title1)

        val title2 = ConversationalFuzzyExtractor.extractTitle("50 రూపాయల చాక్లెట్", ExpenseCategory.FOOD, true)
        assertEquals("చాక్లెట్", title2)

        val title3 = ConversationalFuzzyExtractor.extractTitle("చాక్లెట్ 50 రూపాయలు", ExpenseCategory.FOOD, true)
        assertEquals("చాక్లెట్", title3)

        val title4 = ConversationalFuzzyExtractor.extractTitle("Petrol 500 rupees", ExpenseCategory.TRANSPORT, false)
        assertEquals("Petrol", title4)
    }

    @Test
    fun testLoanClassificationVsExpense() {
        // "i gave this 500" should be loan
        assertTrue(ConversationalFuzzyExtractor.isLoanPhrase("i gave this 500"))
        assertTrue(ConversationalFuzzyExtractor.isLoanPhrase("రమేష్ కి 500 ఇచ్చాను"))
        assertTrue(ConversationalFuzzyExtractor.isLoanPhrase("500 ఇచ్చాను"))
        assertTrue(ConversationalFuzzyExtractor.isLoanPhrase("అప్పు ఇచ్చాను"))

        // "పెట్రోల్ కి 500 ఇచ్చాను" has category Transport, should NOT be classified as loan
        assertFalse(ConversationalFuzzyExtractor.isLoanPhrase("పెట్రోల్ కి 500 ఇచ్చాను"))
        // "కూరగాయలు 200" should NOT be classified as loan
        assertFalse(ConversationalFuzzyExtractor.isLoanPhrase("కూరగాయలు 200 కొన్నాను"))
    }

    @Test
    fun testLoanRepaymentIntentClassification() {
        val intent1 = ConversationalFuzzyExtractor.classifyIntent("రమేష్ 500 తిరిగి ఇచ్చాడు")
        assertEquals(ConversationalFuzzyExtractor.DetectedIntent.LOAN_REPAYMENT, intent1)

        val intent2 = ConversationalFuzzyExtractor.classifyIntent("Ramesh returned 500")
        assertEquals(ConversationalFuzzyExtractor.DetectedIntent.LOAN_REPAYMENT, intent2)

        val intent3 = ConversationalFuzzyExtractor.classifyIntent("బాకీ చెల్లించారు 1000")
        assertEquals(ConversationalFuzzyExtractor.DetectedIntent.LOAN_REPAYMENT, intent3)
    }

    @Test
    fun testDueDateIntentClassification() {
        val intent1 = ConversationalFuzzyExtractor.classifyIntent("Tomorrow")
        assertEquals(ConversationalFuzzyExtractor.DetectedIntent.SET_LOAN_DUE_DATE, intent1)

        val intent2 = ConversationalFuzzyExtractor.classifyIntent("రేపు")
        assertEquals(ConversationalFuzzyExtractor.DetectedIntent.SET_LOAN_DUE_DATE, intent2)

        val intent3 = ConversationalFuzzyExtractor.classifyIntent("1 week")
        assertEquals(ConversationalFuzzyExtractor.DetectedIntent.SET_LOAN_DUE_DATE, intent3)

        val intent4 = ConversationalFuzzyExtractor.classifyIntent("వచ్చే వారం")
        assertEquals(ConversationalFuzzyExtractor.DetectedIntent.SET_LOAN_DUE_DATE, intent4)
    }

    @Test
    fun testClauseExtractionAndMultipliers() {
        // 5k = 5000
        val num1 = ConversationalFuzzyExtractor.extractNumber("bike service 5k")
        assertEquals(5000.0, num1, 0.01)

        // 1.5 lakh = 150000
        val num2 = ConversationalFuzzyExtractor.extractNumber("gold 1.5 lakh")
        assertEquals(150000.0, num2, 0.01)

        // Multi-clause splitting
        val clauses = ConversationalFuzzyExtractor.splitIntoClauses("milk 50 and petrol 200")
        assertEquals(2, clauses.size)

        val clause1 = ConversationalFuzzyExtractor.extractClause(clauses[0], false)
        assertNotNull(clause1)
        assertEquals(50.0, clause1!!.amount, 0.01)

        val clause2 = ConversationalFuzzyExtractor.extractClause(clauses[1], false)
        assertNotNull(clause2)
        assertEquals(200.0, clause2!!.amount, 0.01)
    }
}
