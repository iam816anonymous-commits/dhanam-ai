package com.example.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class CsvExpenseImporterTest {
    @Test
    fun parsesDhanamExportAndQuotedCommaFields() {
        val csv = """
            DHANAM AI - FINANCIAL REPORT
            Report Period,October 2026
            Total Expenses (INR),120
            --- EXPENSES TRANSACTIONS ---
            ID,Date,Time,Title,Category,Amount (INR),Payment Mode,Notes
            1,27/10/2026,09:30 AM,"Tea, snacks",Food,120,UPI,"bought, locally"
            --- LENDING KHATA (BORROWERS / LOANS) ---
            ID,Borrower Name,Phone
            1,Someone,123
        """.trimIndent()

        val result = CsvExpenseImporter.parse(StringReader(csv))

        assertTrue(result.headerFound)
        assertEquals(1, result.expenses.size)
        assertEquals("Tea, snacks", result.expenses.single().title)
        assertEquals("Food", result.expenses.single().category)
        assertEquals(120.0, result.expenses.single().amount, 0.001)
        assertEquals("bought, locally", result.expenses.single().notes)
        assertEquals(0, result.issues.size)
    }

    @Test
    fun rejectsInvalidRowsAndDoesNotInventTransactions() {
        val csv = """
            Date,Title,Category,Amount,Payment Mode,Notes
            27/10/2026,Groceries,Food,500,UPI,
            31/02/2026,Invalid date,Food,20,Cash,
            27/10/2026,Invalid amount,Food,NaN,Cash,
            27/10/2026,,Food,10,Cash,
        """.trimIndent()

        val result = CsvExpenseImporter.parse(StringReader(csv))

        assertEquals(1, result.expenses.size)
        assertEquals("Groceries", result.expenses.single().title)
        assertEquals(3, result.issues.size)
    }

    @Test
    fun missingPaymentMetadataIsReportedAsUnknownRatherThanInvented() {
        val result = CsvExpenseImporter.parse(
            StringReader("Date,Title,Category,Amount\n27/10/2026,Groceries,Food,25")
        )

        assertEquals(1, result.expenses.size)
        assertEquals("Unknown", result.expenses.single().paymentMode)
    }

    @Test
    fun rejectsOversizedCsvBeforeParsingItsRecords() {
        val oversized = "x".repeat(5_000_001)
        var failed = false
        try {
            CsvExpenseImporter.parse(StringReader(oversized))
        } catch (error: IllegalArgumentException) {
            failed = error.message?.contains("maximum supported size") == true
        }
        assertTrue(failed)
    }

    @Test
    fun fingerprintIsStableForSameImportedTransaction() {
        val csv = "Date,Time,Title,Category,Amount,Payment Mode,Notes\n27/10/2026,09:30 AM,Tea,Food,20,UPI,shop"
        val expense = CsvExpenseImporter.parse(StringReader(csv)).expenses.single()

        assertEquals(CsvExpenseImporter.fingerprint(expense), CsvExpenseImporter.fingerprint(expense.copy(id = 999)))
    }

    @Test
    fun reportsMissingHeaderAndMalformedQuoting() {
        val noHeader = CsvExpenseImporter.parse(StringReader("nothing,here\n1,2"))
        assertFalse(noHeader.headerFound)
        assertTrue(noHeader.issues.isNotEmpty())

        var failed = false
        try {
            CsvExpenseImporter.parse(StringReader("Date,Title,Category,Amount\n27/10/2026,\"unfinished,Food,10"))
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)

        failed = false
        try {
            CsvExpenseImporter.parse(StringReader("Date,Title,Category,Amount\n27/10/2026,Gro\"ceries,Food,10"))
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue("A quote inside an unquoted field must be rejected", failed)
    }
}
