package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.local.CategoryTotal
import com.example.data.model.CategoryBudget
import com.example.data.model.Expense
import com.example.data.model.Loan
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    fun generateAndShareMonthlyReport(
        context: Context,
        monthName: String,
        year: Int,
        totalExpenses: Double,
        overallBudget: Double,
        categoryTotals: List<CategoryTotal>,
        categoryBudgets: List<CategoryBudget>,
        expensesList: List<Expense>,
        loansList: List<Loan>,
        savingsTips: List<String>
    ): File? {
        var document: PdfDocument? = null
        return try {
            val doc = PdfDocument()
            document = doc

            // Standard A4 dimensions: 595 x 842 points
            val pageWidth = 595
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var yPos = 40f
        val margin = 40f
        val contentWidth = pageWidth - (margin * 2)

        // 1. Header Background Banner
        paint.color = Color.rgb(15, 23, 42) // Dark Navy #0F172A
        canvas.drawRoundRect(margin, yPos, pageWidth - margin, yPos + 75f, 12f, 12f, paint)

        // Header Title
        paint.color = Color.rgb(245, 158, 11) // Gold
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DHANAM AI • MONTHLY FINANCIAL REPORT", margin + 18f, yPos + 32f, paint)

        // Header Subtitle & Date
        paint.color = Color.rgb(203, 213, 225) // Light Gray
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val dateStr = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        canvas.drawText("Period: $monthName $year  |  Generated on: $dateStr", margin + 18f, yPos + 56f, paint)

        yPos += 95f

        // 2. Metrics Summary Cards (Row of 3 cards)
        val cardWidth = (contentWidth - 20f) / 3f
        val cardHeight = 60f

        // Card 1: Total Spent
        paint.color = Color.rgb(241, 245, 249)
        canvas.drawRoundRect(margin, yPos, margin + cardWidth, yPos + cardHeight, 8f, 8f, paint)
        paint.color = Color.rgb(100, 116, 139)
        paint.textSize = 9f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("TOTAL EXPENSES", margin + 12f, yPos + 20f, paint)
        paint.color = Color.rgb(220, 38, 38) // Red
        paint.textSize = 15f
        canvas.drawText("₹${totalExpenses.toInt()}", margin + 12f, yPos + 44f, paint)

        // Card 2: Budget Adherence
        val card2X = margin + cardWidth + 10f
        paint.color = Color.rgb(241, 245, 249)
        canvas.drawRoundRect(card2X, yPos, card2X + cardWidth, yPos + cardHeight, 8f, 8f, paint)
        paint.color = Color.rgb(100, 116, 139)
        paint.textSize = 9f
        canvas.drawText("MONTHLY BUDGET", card2X + 12f, yPos + 20f, paint)
        val budgetPct = if (overallBudget > 0) ((totalExpenses / overallBudget) * 100).toInt() else 0
        paint.color = if (budgetPct > 100) Color.rgb(220, 38, 38) else Color.rgb(16, 185, 129)
        paint.textSize = 15f
        canvas.drawText("₹${overallBudget.toInt()} ($budgetPct%)", card2X + 12f, yPos + 44f, paint)

        // Card 3: Pending Lending Khata
        val card3X = card2X + cardWidth + 10f
        paint.color = Color.rgb(241, 245, 249)
        canvas.drawRoundRect(card3X, yPos, card3X + cardWidth, yPos + cardHeight, 8f, 8f, paint)
        paint.color = Color.rgb(100, 116, 139)
        paint.textSize = 9f
        canvas.drawText("PENDING LENT (KHATA)", card3X + 12f, yPos + 20f, paint)
        val totalPendingLoans = loansList.filter { !it.isSettled }.sumOf { it.remainingAmount }
        paint.color = Color.rgb(245, 158, 11) // Amber
        paint.textSize = 15f
        canvas.drawText("₹${totalPendingLoans.toInt()}", card3X + 12f, yPos + 44f, paint)

        yPos += cardHeight + 25f

        // 3. Section: Category Spending Breakdown
        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 13f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("CATEGORY BREAKDOWN & BUDGET STATUS", margin, yPos, paint)

        yPos += 12f
        paint.color = Color.rgb(226, 232, 240)
        paint.strokeWidth = 1.5f
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paint)
        yPos += 16f

        // Table Header
        paint.color = Color.rgb(100, 116, 139)
        paint.textSize = 10f
        canvas.drawText("Category", margin + 6f, yPos, paint)
        canvas.drawText("Spent", margin + 190f, yPos, paint)
        canvas.drawText("Budget", margin + 290f, yPos, paint)
        canvas.drawText("Share", margin + 390f, yPos, paint)
        canvas.drawText("Status", margin + 460f, yPos, paint)

        yPos += 14f

        val sortedCategories = categoryTotals.sortedByDescending { it.total }
        for (cat in sortedCategories.take(7)) {
            val limit = categoryBudgets.firstOrNull { it.category == cat.category }?.monthlyLimit ?: 5000.0
            val pct = if (totalExpenses > 0) ((cat.total / totalExpenses) * 100).toInt() else 0
            val isOver = cat.total > limit

            paint.color = Color.rgb(30, 41, 59)
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 9.5f
            canvas.drawText(cat.category, margin + 6f, yPos, paint)
            canvas.drawText("₹${cat.total.toInt()}", margin + 190f, yPos, paint)
            canvas.drawText("₹${limit.toInt()}", margin + 290f, yPos, paint)
            canvas.drawText("$pct%", margin + 390f, yPos, paint)

            paint.color = if (isOver) Color.rgb(220, 38, 38) else Color.rgb(16, 185, 129)
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(if (isOver) "OVERSPENT" else "OK", margin + 460f, yPos, paint)

            yPos += 16f
        }

        yPos += 15f

        // 4. Section: Money Given to Someone (Lending / Khata)
        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 13f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("LENDING & REPAYMENT KHATA SUMMARY", margin, yPos, paint)

        yPos += 12f
        paint.color = Color.rgb(226, 232, 240)
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paint)
        yPos += 16f

        if (loansList.isEmpty()) {
            paint.color = Color.rgb(100, 116, 139)
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 9.5f
            canvas.drawText("No money lent records for this period.", margin + 6f, yPos, paint)
            yPos += 20f
        } else {
            paint.color = Color.rgb(100, 116, 139)
            paint.textSize = 10f
            canvas.drawText("Person", margin + 6f, yPos, paint)
            canvas.drawText("Lent Amount", margin + 170f, yPos, paint)
            canvas.drawText("Balance Due", margin + 270f, yPos, paint)
            canvas.drawText("Due Date", margin + 370f, yPos, paint)
            canvas.drawText("Status", margin + 465f, yPos, paint)
            yPos += 14f

            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            for (loan in loansList.take(5)) {
                paint.color = Color.rgb(30, 41, 59)
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 9.5f
                canvas.drawText(loan.personName, margin + 6f, yPos, paint)
                canvas.drawText("₹${loan.amount.toInt()}", margin + 170f, yPos, paint)
                canvas.drawText("₹${loan.remainingAmount.toInt()}", margin + 270f, yPos, paint)
                val dueDateStr = loan.dueDateMillis?.let { sdf.format(Date(it)) } ?: "Not set"
                canvas.drawText(dueDateStr, margin + 370f, yPos, paint)

                paint.color = when {
                    loan.isSettled -> Color.rgb(16, 185, 129)
                    loan.isOverdue -> Color.rgb(220, 38, 38)
                    else -> Color.rgb(245, 158, 11)
                }
                paint.typeface = Typeface.DEFAULT_BOLD
                val statusText = when {
                    loan.isSettled -> "Settled"
                    loan.isOverdue -> "Overdue"
                    else -> "Pending"
                }
                canvas.drawText(statusText, margin + 465f, yPos, paint)
                yPos += 16f
            }
        }

        yPos += 15f

        // 5. Section: AI Savings Tips & Recommendations
        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 13f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("AI SAVINGS INSIGHTS & BUDGET TIPS", margin, yPos, paint)

        yPos += 12f
        paint.color = Color.rgb(226, 232, 240)
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paint)
        yPos += 16f

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 9.5f
        paint.color = Color.rgb(51, 65, 85)
        for (tip in savingsTips.take(3)) {
            val formattedTip = if (tip.length > 95) tip.substring(0, 92) + "..." else tip
            canvas.drawText("• $formattedTip", margin + 6f, yPos, paint)
            yPos += 16f
        }

        // Footer
        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 8.5f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Generated by Dhanam AI • Private, secure on-device expense & khata assistant", margin, pageHeight - 30f, paint)

        doc.finishPage(page)

        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val pdfFile = File(reportsDir, "Dhanam_Report_${monthName}_$year.pdf")
        val outputStream = FileOutputStream(pdfFile)
        doc.writeTo(outputStream)
        doc.close()
        outputStream.flush()
        outputStream.close()
        pdfFile
    } catch (e: Throwable) {
        com.example.util.AppLogger.e("PdfReportGenerator", "Failed generating PDF report", e)
        try { document?.close() } catch (_: Throwable) {}
        null
        }
    }

    fun openOrSharePdf(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Dhanam AI Monthly Financial Summary")
                putExtra(Intent.EXTRA_TEXT, "Here is my monthly expense and khata summary report generated with Dhanam AI.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share or View Monthly PDF Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            com.example.util.AppLogger.e("PdfReportGenerator", "Failed to share PDF report", e)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "No app available to open or view PDF report", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
