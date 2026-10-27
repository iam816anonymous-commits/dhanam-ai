package com.dhanam.next.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCommandParserTest {
    @Test fun parsesSimpleExpenseOffline() {
        val result = ExpenseCommandParser.parse("tea 30")
        assertTrue(result is ParseExpenseResult.Success)
        val item = (result as ParseExpenseResult.Success).expense
        assertEquals("tea", item.title)
        assertEquals(3000L, item.amountPaise)
        assertEquals("Dining & Snacks", item.category)
        assertEquals("Unknown", item.paymentMode)
    }

    @Test fun parsesExplicitPaymentMode() {
        val item = (ExpenseCommandParser.parse("petrol 250 UPI") as ParseExpenseResult.Success).expense
        assertEquals(25000L, item.amountPaise)
        assertEquals("UPI", item.paymentMode)
        assertEquals("Transport", item.category)
    }

    @Test fun rejectsZeroAmount() {
        assertTrue(ExpenseCommandParser.parse("tea 0") is ParseExpenseResult.Invalid)
    }

    @Test fun asksForMissingAmount() {
        assertTrue(ExpenseCommandParser.parse("tea") is ParseExpenseResult.NeedsClarification)
    }
}
