package com.example.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.Expense
import com.example.data.model.Loan
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CsvReportGenerator:
 * Lightweight, offline-first CSV/Excel export engine designed for low-memory Android devices.
 * Adheres strictly to RFC 4180 CSV standard with proper escaping for quotes, commas, and unicode characters.
 */
object CsvReportGenerator {

    fun generateAndShareCsv(
        context: Context,
        expensesList: List<Expense>,
        loansList: List<Loan>,
        monthName: String,
        year: Int
    ): File? {
        return try {
            val fileName = "Dhanam_AI_Financial_Report_${monthName}_$year.csv"
            val exportDir = File(context.cacheDir, "reports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            val csvFile = File(exportDir, fileName)

            FileOutputStream(csvFile).use { fos ->
                // Write UTF-8 BOM so Microsoft Excel correctly displays Telugu, Hindi, and special characters
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())

                    // 1. Header Information Block
                    writer.write("DHANAM AI - FINANCIAL REPORT\n")
                    writer.write("Report Period,${escapeCsv("$monthName $year")}\n")
                    writer.write("Generated On,${escapeCsv(sdfDate.format(Date()))}\n")
                    val totalExpenses = expensesList.sumOf { it.amount }
                    val totalLent = loansList.sumOf { it.amount }
                    val totalCollected = loansList.sumOf { it.amountPaid }
                    val totalPending = loansList.filter { !it.isSettled }.sumOf { it.remainingAmount }

                    writer.write("Total Expenses (INR),${totalExpenses.toInt()}\n")
                    writer.write("Total Lent in Khata (INR),${totalLent.toInt()}\n")
                    writer.write("Total Recovered (INR),${totalCollected.toInt()}\n")
                    writer.write("Total Khata Pending (INR),${totalPending.toInt()}\n")
                    writer.write("\n")

                    // 2. Expenses Section Table
                    writer.write("--- EXPENSES TRANSACTIONS ---\n")
                    writer.write("ID,Date,Time,Title,Category,Amount (INR),Payment Mode,Notes\n")
                    expensesList.forEachIndexed { idx, exp ->
                        val dateStr = sdfDate.format(Date(exp.timestamp))
                        val timeStr = sdfTime.format(Date(exp.timestamp))
                        writer.write(
                            listOf(
                                (idx + 1).toString(),
                                escapeCsv(dateStr),
                                escapeCsv(timeStr),
                                escapeCsv(exp.title),
                                escapeCsv(exp.category),
                                exp.amount.toInt().toString(),
                                escapeCsv(exp.paymentMode),
                                escapeCsv(exp.notes)
                            ).joinToString(",") + "\n"
                        )
                    }
                    writer.write("\n")

                    // 3. Lending Khata Section Table
                    writer.write("--- LENDING KHATA (BORROWERS / LOANS) ---\n")
                    writer.write("ID,Borrower Name,Phone,Date Given,Due Date,Total Lent (INR),Amount Repaid (INR),Remaining Due (INR),Status,Notes\n")
                    loansList.forEachIndexed { idx, loan ->
                        val dateGiven = sdfDate.format(Date(loan.dateGivenMillis))
                        val dueDate = loan.dueDateMillis?.let { sdfDate.format(Date(it)) } ?: "Not Set"
                        val status = if (loan.isSettled) "Fully Settled" else if (loan.isOverdue) "OVERDUE" else "Active"
                        writer.write(
                            listOf(
                                (idx + 1).toString(),
                                escapeCsv(loan.personName),
                                escapeCsv(loan.phoneNumber),
                                escapeCsv(dateGiven),
                                escapeCsv(dueDate),
                                loan.amount.toInt().toString(),
                                loan.amountPaid.toInt().toString(),
                                loan.remainingAmount.toInt().toString(),
                                escapeCsv(status),
                                escapeCsv(loan.notes)
                            ).joinToString(",") + "\n"
                        )
                    }
                }
            }
            csvFile
        } catch (e: Exception) {
            com.example.util.AppLogger.e("CsvReportGenerator", "Failed generating CSV export", e)
            null
        }
    }

    fun openOrShareCsv(context: Context, csvFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Dhanam AI Financial Records (CSV / Excel)")
                putExtra(Intent.EXTRA_TEXT, "Attached is the financial expense and khata CSV export generated from Dhanam AI.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share or Open CSV Report in Excel / Sheets").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            com.example.util.AppLogger.e("CsvReportGenerator", "Failed to share CSV report", e)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "No app available to open or share CSV file", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }
}
