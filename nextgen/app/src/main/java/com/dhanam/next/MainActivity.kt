package com.dhanam.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanam.next.data.local.ExpenseEntity
import com.dhanam.next.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF081512)
private val Panel = Color(0xFF10231E)
private val Emerald = Color(0xFF37C99A)
private val Gold = Color(0xFFE7BD68)
private val Muted = Color(0xFF9BB3AA)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Emerald, secondary = Gold, background = Ink, surface = Panel, onBackground = Color(0xFFEAF4EF), onSurface = Color(0xFFEAF4EF))) {
                DhanamApp(viewModel)
            }
        }
    }
}

private enum class Destination(val label: String) { AGENT("Agent"), EXPENSES("Expenses"), KHATA("Khata"), INSIGHTS("Insights"), SETTINGS("Settings") }

@Composable
private fun DhanamApp(viewModel: MainViewModel) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf(Destination.AGENT) }
    var showAdd by remember { mutableStateOf(false) }
    var command by remember { mutableStateOf("") }
    var assistantMessage by remember { mutableStateOf("I'm ready. Your financial records stay on this device.") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    val monthExpenses = expenses.filter { sameMonth(it.occurredAtEpochMillis, System.currentTimeMillis()) }
    val monthPaise = monthExpenses.sumOf { it.amountPaise }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                Destination.entries.forEach { item ->
                    val icon = when (item) {
                        Destination.AGENT -> Icons.Default.Assistant
                        Destination.EXPENSES -> Icons.Default.History
                        Destination.KHATA -> Icons.Default.AttachMoney
                        Destination.INSIGHTS -> Icons.Default.Analytics
                        Destination.SETTINGS -> Icons.Default.Settings
                    }
                    NavigationBarItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(icon, contentDescription = item.label) }, label = { androidx.compose.material3.Text(item.label, fontSize = 10.sp) })
                }
            }
        },
        floatingActionButton = {
            if (destination == Destination.EXPENSES) FloatingActionButton(onClick = { showAdd = true }, containerColor = Gold, contentColor = Ink) { Icon(Icons.Default.Add, contentDescription = "Add expense") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Ink).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.size(42.dp).background(Emerald.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Assistant, contentDescription = null, tint = Emerald, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    androidx.compose.material3.Text("DHANAM AI", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    androidx.compose.material3.Text("Money, understood.", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                androidx.compose.material3.Text("OFFLINE", color = Emerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            when (destination) {
                Destination.AGENT -> {
                    androidx.compose.material3.Text("Your financial companion", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.padding(18.dp)) {
                            androidx.compose.material3.Text("LOCAL INTELLIGENCE", color = Emerald, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.Text(assistantMessage, fontSize = 17.sp, lineHeight = 24.sp)
                            Spacer(Modifier.height(14.dp))
                            androidx.compose.material3.Text("Try: “tea 30”, “petrol 250 UPI”", color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(command, { command = it }, modifier = Modifier.fillMaxWidth(), label = { androidx.compose.material3.Text("Tell me an expense") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { viewModel.processCommand(command) { assistantMessage = it }; command = "" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { androidx.compose.material3.Text("Process offline") }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SummaryCard(monthPaise, monthExpenses.size)
                    Spacer(Modifier.height(14.dp))
                    androidx.compose.material3.Text("RECENT ACTIVITY", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        items(expenses.take(4), key = { it.id }) { ExpenseRow(it, {}) }
                    }
                }
                Destination.EXPENSES -> {
                    androidx.compose.material3.Text("Your transactions", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    androidx.compose.material3.Text(expenses.size.toString() + " saved records · stored locally", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                    if (expenses.isEmpty()) EmptyState("No expenses yet", "Add your first transaction. No demo records are inserted.")
                    else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(expenses, key = { it.id }) { expense -> ExpenseRow(expense) { deleteId = expense.id; feedback = "Delete “" + expense.title + "”?" } }
                    }
                }
                Destination.KHATA -> FeatureFoundation("Lending Khata", "Track money lent, due dates, repayments, and outstanding balances.", "No fake loan records are shown. Lending and repayment workflows are scheduled for the next phase.")
                Destination.INSIGHTS -> FeatureFoundation("Insights", "Understand monthly spending, category trends, and safe-to-spend estimates.", "Insights will be computed from actual saved records; budgets are not invented.")
                Destination.SETTINGS -> FeatureFoundation("Settings & privacy", "Local-first mode is active. Core financial operations do not require internet.", "Optional on-device and cloud AI will be added only with runtime checks and explicit consent.")
            }
        }
    }

    if (showAdd) AddExpenseDialog(onDismiss = { showAdd = false }, onSave = { title, amount, category, payment ->
        viewModel.addManualExpense(title, amount, category, payment) { feedback = it }
        showAdd = false
    })
    if (feedback != null) AlertDialog(
        onDismissRequest = { feedback = null; deleteId = null },
        title = { androidx.compose.material3.Text(if (deleteId != null) "Confirm deletion" else "Dhanam AI") },
        text = { androidx.compose.material3.Text(feedback.orEmpty()) },
        confirmButton = { TextButton(onClick = {
            val id = deleteId
            if (id != null) viewModel.deleteExpense(id) { assistantMessage = it }
            deleteId = null
            feedback = null
        }) { androidx.compose.material3.Text(if (deleteId != null) "Delete" else "OK") } },
        dismissButton = { if (deleteId != null) TextButton(onClick = { deleteId = null; feedback = null }) { androidx.compose.material3.Text("Cancel") } }
    )
}

@Composable
private fun SummaryCard(monthPaise: Long, count: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            androidx.compose.material3.Text("SPENT THIS MONTH", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Text(formatRupees(monthPaise), color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Text(count.toString() + " transactions · local database", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseEntity, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.size(38.dp).background(Emerald.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text(expense.title.take(1).uppercase(), color = Emerald, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                androidx.compose.material3.Text(expense.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                androidx.compose.material3.Text(expense.category + " · " + SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(expense.occurredAtEpochMillis)), color = Muted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                androidx.compose.material3.Text("−" + formatRupees(expense.amountPaise), color = Gold, fontWeight = FontWeight.Bold)
                if (onDelete != {}) IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete expense", tint = Muted, modifier = Modifier.size(17.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Text(body, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FeatureFoundation(title: String, body: String, note: String) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        androidx.compose.material3.Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.Text(body, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            androidx.compose.material3.Text(note, Modifier.padding(18.dp), color = Gold, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun AddExpenseDialog(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Other") }
    var payment by remember { mutableStateOf("Unknown") }
    val categories = listOf("Food & Groceries", "Dining & Snacks", "Transport", "Bills & Utilities", "Health & Medical", "Shopping", "Other")
    val payments = listOf("Unknown", "UPI", "Cash", "Card", "NetBanking")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Add expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(title, { title = it }, label = { androidx.compose.material3.Text("Title") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { androidx.compose.material3.Text("Amount (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                androidx.compose.material3.Text("Category: " + category, color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    categories.take(4).forEach { value -> TextButton(onClick = { category = value }) { androidx.compose.material3.Text(if (category == value) "✓ " + value else value, fontSize = 9.sp) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    payments.forEach { value -> TextButton(onClick = { payment = value }) { androidx.compose.material3.Text(if (payment == value) "✓ " + value else value, fontSize = 10.sp) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, amount, category, payment) }) { androidx.compose.material3.Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Cancel") } }
    )
}

private fun formatRupees(paise: Long): String = "₹" + java.math.BigDecimal.valueOf(paise, 2).stripTrailingZeros().toPlainString()
private fun sameMonth(firstMillis: Long, secondMillis: Long): Boolean {
    val first = java.util.Calendar.getInstance().apply { timeInMillis = firstMillis }
    val second = java.util.Calendar.getInstance().apply { timeInMillis = secondMillis }
    return first.get(java.util.Calendar.YEAR) == second.get(java.util.Calendar.YEAR) &&
        first.get(java.util.Calendar.MONTH) == second.get(java.util.Calendar.MONTH)
}
