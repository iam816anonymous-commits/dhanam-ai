package com.example.export

import com.example.data.model.Expense
import java.io.Reader
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class CsvExpenseImportIssue(
    val recordNumber: Int,
    val reason: String
)

data class CsvExpenseImportResult(
    val expenses: List<Expense>,
    val issues: List<CsvExpenseImportIssue>,
    val headerFound: Boolean
)

/**
 * Parses the expense table produced by Dhanam AI exports and simple transaction CSVs.
 * It is deliberately side-effect free: callers validate duplicates and write to Room separately.
 */
object CsvExpenseImporter {
    private const val MAX_CSV_CHARACTERS = 5_000_000
    private const val MAX_CSV_RECORDS = 100_000
    private const val MAX_CSV_COLUMNS = 100

    fun parse(reader: Reader): CsvExpenseImportResult {
        val raw = readBounded(reader).removePrefix("\uFEFF")
        if (raw.isBlank()) return CsvExpenseImportResult(emptyList(), emptyList(), false)

        val records = parseCsvRecords(raw)
        val headerIndex = records.indexOfFirst { row ->
            val headers = row.map(::normalizeHeader)
            headers.any { it == "date" || it == "transaction date" } &&
                headers.any { it == "title" || it == "description" || it == "expense" || it == "merchant" } &&
                headers.any { it.startsWith("amount") || it == "value" }
        }
        if (headerIndex < 0) return CsvExpenseImportResult(
            emptyList(),
            listOf(CsvExpenseImportIssue(1, "Could not find a transaction header containing date, title, and amount.")),
            false
        )

        val headers = records[headerIndex].map(::normalizeHeader)
        val dateIndex = headers.indexOfFirst { it == "date" || it == "transaction date" }
        val timeIndex = headers.indexOfFirst { it == "time" || it == "transaction time" }
        val titleIndex = headers.indexOfFirst { it == "title" || it == "description" || it == "expense" || it == "merchant" }
        val categoryIndex = headers.indexOfFirst { it == "category" || it == "expense category" }
        val amountIndex = headers.indexOfFirst { it.startsWith("amount") || it == "value" }
        val paymentIndex = headers.indexOfFirst { it == "payment mode" || it == "payment method" || it == "payment" }
        val notesIndex = headers.indexOfFirst { it == "notes" || it == "note" || it == "memo" }

        val expenses = mutableListOf<Expense>()
        val issues = mutableListOf<CsvExpenseImportIssue>()
        records.drop(headerIndex + 1)
            .takeWhile { row -> row.firstOrNull()?.trim()?.startsWith("--- LENDING KHATA", ignoreCase = true) != true }
            .forEachIndexed { offset, row ->
            val recordNumber = headerIndex + offset + 2
            if (row.all { it.isBlank() } || row.firstOrNull()?.trim()?.startsWith("---") == true) return@forEachIndexed
            if (row.size <= maxOf(dateIndex, titleIndex, amountIndex)) {
                issues += CsvExpenseImportIssue(recordNumber, "Record has fewer columns than the header.")
                return@forEachIndexed
            }
            val title = row.getOrNull(titleIndex).orEmpty().trim()
            val amountText = row.getOrNull(amountIndex).orEmpty()
                .replace(",", "")
                .replace("₹", "")
                .replace(Regex("(?i)\\bINR\\b"), "")
                .trim()
            val amount = amountText.toDoubleOrNull()
            val dateText = row.getOrNull(dateIndex).orEmpty().trim()
            val timeText = if (timeIndex >= 0) row.getOrNull(timeIndex).orEmpty().trim() else ""
            val timestamp = parseTimestamp(dateText, timeText)

            val reason = when {
                title.isBlank() -> "Title is empty."
                amount == null || !amount.isFinite() || amount <= 0.0 -> "Amount must be a finite number greater than zero."
                timestamp == null -> "Date or time is invalid."
                else -> null
            }
            if (reason != null) {
                issues += CsvExpenseImportIssue(recordNumber, reason)
                return@forEachIndexed
            }

            val category = row.getOrNull(categoryIndex).orEmpty().trim().ifBlank { "Other" }
            // Missing payment metadata must remain unknown; do not invent UPI usage in reports.
            val paymentMode = row.getOrNull(paymentIndex).orEmpty().trim().ifBlank { "Unknown" }
            val notes = row.getOrNull(notesIndex).orEmpty().trim()
            expenses += Expense(
                title = title,
                amount = amount!!,
                category = category,
                timestamp = timestamp!!,
                notes = notes,
                paymentMode = paymentMode
            )
        }
        return CsvExpenseImportResult(expenses, issues, true)
    }

    /** Stable transaction key used to make re-importing the same file idempotent. */
    fun fingerprint(expense: Expense): String = listOf(
        expense.title.trim().lowercase(Locale.ROOT),
        "%.2f".format(Locale.ROOT, expense.amount),
        expense.category.trim().lowercase(Locale.ROOT),
        (expense.timestamp / 60_000L).toString(),
        expense.paymentMode.trim().lowercase(Locale.ROOT),
        expense.notes.trim().lowercase(Locale.ROOT)
    ).joinToString("|")

    private fun normalizeHeader(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s*\\([^)]*\\)"), "")
            .replace(Regex("[^\\p{L}\\p{N} ]"), "")
            .replace(Regex("\\s+"), " ")

    private fun parseTimestamp(dateText: String, timeText: String): Long? {
        val dateFormats = listOf("dd/MM/yyyy", "d/M/yyyy", "yyyy-MM-dd", "MM/dd/yyyy")
        val date = dateFormats.firstNotNullOfOrNull { parseStrict(dateText, it) } ?: return null
        val calendar = Calendar.getInstance()
        calendar.time = date
        if (timeText.isNotBlank()) {
            val parsedTime = listOf("hh:mm a", "h:mm a", "HH:mm", "H:mm")
                .firstNotNullOfOrNull { parseStrict(timeText.uppercase(Locale.ROOT), it) } ?: return null
            val timeCalendar = Calendar.getInstance().apply { time = parsedTime }
            calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
        }
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun parseStrict(value: String, pattern: String): java.util.Date? {
        val format = SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getDefault()
        }
        val position = ParsePosition(0)
        val parsed = format.parse(value, position)
        return parsed?.takeIf { position.index == value.length }
    }

    private fun readBounded(reader: Reader): String {
        val output = StringBuilder()
        val buffer = CharArray(8_192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            require(output.length + count <= MAX_CSV_CHARACTERS) {
                "CSV exceeds the maximum supported size of 5 million characters."
            }
            output.append(buffer, 0, count)
        }
        return output.toString()
    }

    private fun parseCsvRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var afterQuote = false
        var index = 0

        fun finishField() {
            require(row.size < MAX_CSV_COLUMNS) {
                "CSV contains more than $MAX_CSV_COLUMNS columns in one record."
            }
            row += field.toString()
            field.setLength(0)
            afterQuote = false
        }

        fun finishRecord() {
            finishField()
            if (row.any { it.isNotEmpty() }) {
                require(records.size < MAX_CSV_RECORDS) {
                    "CSV contains more than $MAX_CSV_RECORDS records."
                }
                records += row
            }
            row = mutableListOf()
        }

        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' && inQuotes && index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' && inQuotes -> {
                    inQuotes = false
                    afterQuote = true
                }
                char == '"' && !inQuotes -> {
                    require(field.isEmpty() && !afterQuote) {
                        "CSV contains a quote inside an unquoted field."
                    }
                    inQuotes = true
                }
                afterQuote && char == ',' -> finishField()
                afterQuote && (char == '\n' || char == '\r') -> {
                    finishRecord()
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                }
                afterQuote && char.isWhitespace() -> Unit
                afterQuote -> throw IllegalArgumentException("CSV contains unexpected text after a closing quote.")
                char == ',' && !inQuotes -> finishField()
                (char == '\n' || char == '\r') && !inQuotes -> {
                    finishRecord()
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                }
                else -> field.append(char)
            }
            index++
        }
        require(!inQuotes) { "CSV contains an unterminated quoted field." }
        if (row.isNotEmpty() || field.isNotEmpty() || afterQuote) finishRecord()
        return records
    }
}
