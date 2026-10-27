package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.ui.components.OverspendingBanner
import com.example.ui.theme.AlertRed
import com.example.ui.theme.EmeraldPrimaryLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ExpenseSortOrder(val labelEn: String, val labelTe: String) {
    DATE_DESC("Newest", "కొత్తవి"),
    DATE_ASC("Oldest", "పాతవి"),
    AMOUNT_DESC("Highest ₹", "ఎక్కువ మొత్తం"),
    AMOUNT_ASC("Lowest ₹", "తక్కువ మొత్తం")
}

private fun matchesCategoryAlias(rawValue: String, category: ExpenseCategory): Boolean {
    val raw = rawValue.trim().lowercase(Locale.ROOT)
    val canonical = category.displayNameEn.lowercase(Locale.ROOT)
    if (raw == canonical || raw == category.name.lowercase(Locale.ROOT) ||
        raw == category.displayNameTe.lowercase(Locale.ROOT)
    ) return true

    return when (category) {
        ExpenseCategory.FOOD -> raw in setOf("food", "groceries", "grocery")
        ExpenseCategory.DINING -> raw in setOf("dining", "snacks", "restaurant")
        ExpenseCategory.TRANSPORT -> raw in setOf("transport", "fuel", "petrol", "travel")
        ExpenseCategory.SHOPPING -> raw in setOf("shopping", "clothes", "clothing")
        ExpenseCategory.BILLS -> raw in setOf("bills", "utilities", "utility")
        ExpenseCategory.ENTERTAINMENT -> raw in setOf("entertainment", "movies", "movie")
        ExpenseCategory.HEALTH -> raw in setOf("health", "medical", "medicine")
        ExpenseCategory.EDUCATION -> raw in setOf("education", "fees", "fee")
        ExpenseCategory.LENDING -> raw in setOf("lending", "money lent", "debt")
        ExpenseCategory.OTHER -> raw in setOf("other", "uncategorized", "other miscellaneous")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val expenses by viewModel.expensesForSelectedMonth.collectAsState()
    val categoryTotals by viewModel.categoryTotals.collectAsState()
    val categoryBudgets by viewModel.categoryBudgets.collectAsState()
    val overallBudget by viewModel.overallBudget.collectAsState()
    val selectedMonthTimestamp by viewModel.selectedMonthTimestamp.collectAsState()
    val isAllMonthsSelected by viewModel.isAllMonthsSelected.collectAsState()
    val availableMonths by viewModel.availableMonths.collectAsState()
    val preferredLang by viewModel.preferredLanguage.collectAsState()
    val isTelugu = preferredLang == "te"

    val currentCal = Calendar.getInstance()
    val selectedCal = Calendar.getInstance().apply { timeInMillis = selectedMonthTimestamp }
    val isCurrentMonth = !isAllMonthsSelected &&
            currentCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
            currentCal.get(Calendar.MONTH) == selectedCal.get(Calendar.MONTH)

    val monthLabel = if (isAllMonthsSelected) {
        if (isTelugu) "అన్ని నెలల చరిత్ర" else "All Months History"
    } else {
        formatMonthDisplay(selectedMonthTimestamp, isTelugu)
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var sortOrder by remember { mutableStateOf(ExpenseSortOrder.DATE_DESC) }
    var showAddDialog by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var categoryToEditBudget by remember { mutableStateOf<String?>(null) }

    val totalSpent = categoryTotals.sumOf { it.total }
    val remainingBudget = (overallBudget - totalSpent).coerceAtLeast(0.0)
    val budgetProgress = if (overallBudget > 0) (totalSpent / overallBudget).toFloat().coerceIn(0f, 1f) else 0f
    val isOverallOverspent = totalSpent > overallBudget

    // Only report overspending against a budget the user actually configured.
    val overspentCategory = categoryBudgets.firstNotNullOfOrNull { budget ->
        val category = ExpenseCategory.entries.firstOrNull { matchesCategoryAlias(budget.category, it) }
            ?: return@firstNotNullOfOrNull null
        val spent = categoryTotals.filter { matchesCategoryAlias(it.category, category) }.sumOf { it.total }
        if (budget.monthlyLimit.isFinite() && budget.monthlyLimit > 0.0 && spent > budget.monthlyLimit) {
            budget to spent
        } else {
            null
        }
    }

    val filteredExpenses = expenses.filter { exp ->
        val matchesCategory = selectedCategoryFilter == null || exp.category.equals(selectedCategoryFilter, ignoreCase = true)
        val matchesQuery = searchQuery.isBlank() || exp.title.contains(searchQuery, ignoreCase = true) ||
                exp.notes.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }.let { list ->
        when (sortOrder) {
            ExpenseSortOrder.DATE_DESC -> list.sortedByDescending { it.timestamp }
            ExpenseSortOrder.DATE_ASC -> list.sortedBy { it.timestamp }
            ExpenseSortOrder.AMOUNT_DESC -> list.sortedByDescending { it.amount }
            ExpenseSortOrder.AMOUNT_ASC -> list.sortedBy { it.amount }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_expense_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        },
        modifier = modifier.testTag("expenses_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(2.dp))
                MonthNavigationBar(
                    selectedMonthTimestamp = selectedMonthTimestamp,
                    isAllMonthsSelected = isAllMonthsSelected,
                    availableMonths = availableMonths,
                    isTelugu = isTelugu,
                    isCurrentMonth = isCurrentMonth,
                    monthLabel = monthLabel,
                    onSelectMonth = { viewModel.selectMonth(it) },
                    onSelectAllMonths = { viewModel.selectAllMonths() },
                    onPreviousMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() },
                    onResetToCurrent = { viewModel.resetToCurrentMonth() }
                )
            }

            item {
                // Monthly Summary Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    isAllMonthsSelected -> if (isTelugu) "అన్ని నెలల మొత్తం ఖర్చులు" else "All Months Total Spending"
                                    isCurrentMonth -> if (isTelugu) "ఈ నెల ఖర్చులు ($monthLabel)" else "This Month's Spending ($monthLabel)"
                                    else -> if (isTelugu) "గత నెల ఖర్చులు: $monthLabel" else "Previous Month: $monthLabel"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isOverallOverspent) AlertRed.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isOverallOverspent) (if (isTelugu) "పరిమితి దాటింది" else "Over Budget")
                                    else (if (isTelugu) "పరిమితిలో ఉంది" else "On Track"),
                                    color = if (isOverallOverspent) AlertRed else SuccessGreen,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "₹${totalSpent.toInt()}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 34.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { budgetProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (isOverallOverspent) AlertRed else EmeraldPrimaryLight,
                            trackColor = MaterialTheme.colorScheme.surface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isTelugu) "నెల బడ్జెట్: ₹${overallBudget.toInt()}" else "Monthly Budget: ₹${overallBudget.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = if (isTelugu) "మిగిలినది: ₹${remainingBudget.toInt()}" else "Remaining: ₹${remainingBudget.toInt()}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isOverallOverspent) AlertRed else SuccessGreen
                            )
                        }
                    }
                }
            }

            // Overspending Banner if any category crossed limit
            if (overspentCategory != null) {
                item {
                    val budget = overspentCategory.first
                    val spent = overspentCategory.second
                    OverspendingBanner(
                        title = if (isTelugu) "${budget.category} బడ్జెట్ మించిపోయింది!" else "Overspending in ${budget.category}!",
                        message = if (isTelugu)
                            "మీరు ఈ విభాగానికి కేటాయించిన ₹${budget.monthlyLimit.toInt()} కంటే ఎక్కువ ఖర్చు చేశారు (ఖర్చు: ₹${spent.toInt()})."
                        else
                            "You have spent ₹${spent.toInt()} against the monthly limit of ₹${budget.monthlyLimit.toInt()}."
                    )
                }
            }

            // Category Breakdown Section
            item {
                Text(
                    text = if (isTelugu) "విభాగాల వారీగా ఖర్చులు (Category Breakdown)" else "Category Budgets & Spending",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ExpenseCategory.entries.filter { it != ExpenseCategory.LENDING }) { cat ->
                        val catTotal = categoryTotals
                            .filter { matchesCategoryAlias(it.category, cat) }
                            .sumOf { it.total }
                        val limit = categoryBudgets.firstOrNull { matchesCategoryAlias(it.category, cat) }
                            ?.monthlyLimit
                            ?.takeIf { it.isFinite() && it > 0.0 }
                        val pct = limit?.let { (catTotal / it).toFloat().coerceIn(0f, 1f) } ?: 0f
                        val isOver = limit != null && catTotal > limit

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedCategoryFilter == cat.displayNameEn)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .width(150.dp)
                                .clickable {
                                    selectedCategoryFilter = if (selectedCategoryFilter == cat.displayNameEn) null else cat.displayNameEn
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isTelugu) cat.displayNameTe.split("&")[0].trim() else cat.displayNameEn.split("&")[0].trim(),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { categoryToEditBudget = cat.displayNameEn },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Adjust Budget",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "₹${catTotal.toInt()}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isOver) AlertRed else MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                if (limit != null) {
                                    LinearProgressIndicator(
                                        progress = { pct },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = if (isOver) AlertRed else EmeraldPrimaryLight
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(5.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = limit?.let { "Limit: ₹${it.toInt()}" }
                                        ?: if (isTelugu) "పరిమితి సెట్ చేయలేదు" else "No limit set",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Search Bar & Filter Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(if (isTelugu) "ఖర్చులను వెతకండి..." else "Search expenses...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("expense_search_field"),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    if (selectedCategoryFilter != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { selectedCategoryFilter = null }) {
                            Text(if (isTelugu) "అన్నీ" else "Reset")
                        }
                    }
                }
            }

            // Quick Sort Chips Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isTelugu) "క్రమం:" else "Sort:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(ExpenseSortOrder.entries) { order ->
                            val isSelected = sortOrder == order
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { sortOrder = order }
                            ) {
                                Text(
                                    text = if (isTelugu) order.labelTe else order.labelEn,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Transactions Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            isAllMonthsSelected -> if (isTelugu) "అన్ని నెలల ఖర్చులు (${filteredExpenses.size})" else "All Months Expenses (${filteredExpenses.size})"
                            isCurrentMonth -> if (isTelugu) "ఈ నెల ఖర్చులు (${filteredExpenses.size})" else "This Month's Expenses (${filteredExpenses.size})"
                            else -> if (isTelugu) "$monthLabel ఖర్చులు (${filteredExpenses.size})" else "$monthLabel Expenses (${filteredExpenses.size})"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Expenses List
            if (filteredExpenses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isTelugu) "ఖర్చులు ఏవీ కనుగొనబడలేదు. AI ఏజెంట్ లేదా + బటన్ ద్వారా జోడించండి."
                            else "No expenses found. Speak to Dhanam AI or tap + to add.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredExpenses, key = { it.id }) { expense ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteExpense(expense)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true,
                        backgroundContent = {
                            val isSwipingDelete = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSwipingDelete) AlertRed else Color.Transparent)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isTelugu) "తొలగించు" else "Delete",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    ) {
                        ExpenseRowCard(
                            expense = expense,
                            isTelugu = isTelugu,
                            onClick = { expenseToEdit = expense },
                            onDeleteClick = { viewModel.deleteExpense(expense) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(70.dp))
            }
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            isTelugu = isTelugu,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, amt, cat, mode, notes ->
                viewModel.addManualExpense(title, amt, cat, mode, notes)
                showAddDialog = false
            }
        )
    }

    expenseToEdit?.let { expense ->
        EditExpenseDialog(
            expense = expense,
            isTelugu = isTelugu,
            onDismiss = { expenseToEdit = null },
            onConfirm = { updated ->
                viewModel.updateExpense(updated)
                expenseToEdit = null
            }
        )
    }

    categoryToEditBudget?.let { catName ->
        val currentLimit = categoryBudgets.firstOrNull { it.category.equals(catName, ignoreCase = true) }?.monthlyLimit ?: 0.0
        SetCategoryBudgetDialog(
            category = catName,
            currentLimit = currentLimit,
            isTelugu = isTelugu,
            onDismiss = { categoryToEditBudget = null },
            onConfirm = { newLimit ->
                viewModel.updateCategoryBudget(catName, newLimit)
                categoryToEditBudget = null
            }
        )
    }
}

@Composable
fun ExpenseRowCard(
    expense: Expense,
    isTelugu: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val (catIcon, catBgColor, catIconColor) = remember(expense.category) {
        when {
            expense.category.contains("Food", true) || expense.category.contains("Grocer", true) || expense.category.contains("కూరగాయ", true) ->
                Triple(Icons.Default.ShoppingCart, Color(0xFFE8F5E9), Color(0xFF1B5E20))
            expense.category.contains("Dining", true) || expense.category.contains("Snack", true) || expense.category.contains("రెస్టారెంట్", true) ->
                Triple(Icons.Default.Restaurant, Color(0xFFFFF3E0), Color(0xFFE65100))
            expense.category.contains("Transport", true) || expense.category.contains("Fuel", true) || expense.category.contains("పెట్రోల్", true) ->
                Triple(Icons.Default.DirectionsCar, Color(0xFFE8EAF6), Color(0xFF283593))
            expense.category.contains("Shopping", true) || expense.category.contains("Clothes", true) || expense.category.contains("షాపింగ్", true) ->
                Triple(Icons.Default.ShoppingBag, Color(0xFFF3E5F5), Color(0xFF7B1FA2))
            expense.category.contains("Bill", true) || expense.category.contains("Recharge", true) || expense.category.contains("బిల్లు", true) ->
                Triple(Icons.AutoMirrored.Filled.ReceiptLong, Color(0xFFE1F5FE), Color(0xFF0277BD))
            expense.category.contains("Movie", true) || expense.category.contains("Entertainment", true) || expense.category.contains("వినోదం", true) ->
                Triple(Icons.Default.Movie, Color(0xFFEDE7F6), Color(0xFF4527A0))
            expense.category.contains("Health", true) || expense.category.contains("Medical", true) || expense.category.contains("వైద్యం", true) ->
                Triple(Icons.Default.LocalHospital, Color(0xFFFFEBEE), Color(0xFFC62828))
            expense.category.contains("Education", true) || expense.category.contains("Fee", true) || expense.category.contains("చదువు", true) ->
                Triple(Icons.Default.School, Color(0xFFE0F2F1), Color(0xFF004D40))
            expense.category.contains("Lend", true) || expense.category.contains("Debt", true) || expense.category.contains("అప్పు", true) ->
                Triple(Icons.Default.Handshake, Color(0xFFFFF8E1), Color(0xFFF57F17))
            else ->
                Triple(Icons.Default.Category, Color(0xFFECEFF1), Color(0xFF37474F))
        }
    }

    val formattedAmount = remember(expense.amount) {
        try {
            java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(expense.amount.toLong())
        } catch (e: Exception) {
            expense.amount.toInt().toString()
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp, pressedElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Category Icon Badge with soft rounded pill background
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(catBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = catIcon,
                        contentDescription = expense.category,
                        tint = catIconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = expense.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Category Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = expense.category,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        // Payment Mode Badge
                        if (expense.paymentMode.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EmeraldPrimaryLight.copy(alpha = 0.12f),
                                border = BorderStroke(0.5.dp, EmeraldPrimaryLight.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = expense.paymentMode.uppercase(Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    fontSize = 9.sp,
                                    color = EmeraldPrimaryLight,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Formatted Date
                        val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(expense.timestamp))
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    if (expense.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "📝 ${expense.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount and Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = "₹$formattedAmount",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = EmeraldPrimaryLight
                        )
                    )
                }

                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Expense",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    isTelugu: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (title: String, amount: Double, category: String, paymentMode: String, notes: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ExpenseCategory.FOOD.displayNameEn) }
    var selectedPaymentMode by remember { mutableStateOf("UPI") }
    var notes by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var amountValidationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isTelugu) "కొత్త ఖర్చు నమోదు" else "Add New Expense",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = {
                        amountStr = it
                        amountValidationError = null
                    },
                    label = { Text(if (isTelugu) "మొత్తం (Amount ₹)" else "Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                amountValidationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isTelugu) "వివరణ (Title)" else "Description") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (isTelugu) "విభాగం (Category)" else "Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        ExpenseCategory.entries.filter { it != ExpenseCategory.LENDING }.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(if (isTelugu) cat.displayNameTe else cat.displayNameEn) },
                                onClick = {
                                    selectedCategory = cat.displayNameEn
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Payment Mode row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("UPI", "Cash", "Card", "NetBanking").forEach { mode ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedPaymentMode == mode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedPaymentMode = mode }
                        ) {
                            Text(
                                text = mode,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (selectedPaymentMode == mode) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(if (isTelugu) "గమనికలు (Notes)" else "Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (amt == null || !amt.isFinite() || amt <= 0.0) {
                        amountValidationError = if (isTelugu) "సున్నా కంటే ఎక్కువ సరైన మొత్తాన్ని నమోదు చేయండి." else "Enter a valid amount greater than zero."
                    } else if (title.isBlank()) {
                        amountValidationError = if (isTelugu) "వివరణ నమోదు చేయండి." else "Enter a description."
                    } else {
                        onConfirm(title, amt, selectedCategory, selectedPaymentMode, notes)
                    }
                },
                enabled = true
            ) {
                Text(if (isTelugu) "జోడించు" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isTelugu) "రద్దు" else "Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseDialog(
    expense: Expense,
    isTelugu: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Expense) -> Unit
) {
    var title by remember { mutableStateOf(expense.title) }
    var amountStr by remember { mutableStateOf("${expense.amount.toInt()}") }
    var selectedCategory by remember { mutableStateOf(expense.category) }
    var selectedPaymentMode by remember { mutableStateOf(expense.paymentMode.ifBlank { "UPI" }) }
    var notes by remember { mutableStateOf(expense.notes) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var amountValidationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isTelugu) "ఖర్చు వివరాల సవరణ" else "Edit Expense",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = {
                        amountStr = it
                        amountValidationError = null
                    },
                    label = { Text(if (isTelugu) "మొత్తం (Amount ₹)" else "Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                amountValidationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isTelugu) "వివరణ (Title)" else "Description") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (isTelugu) "విభాగం (Category)" else "Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        ExpenseCategory.entries.filter { it != ExpenseCategory.LENDING }.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(if (isTelugu) cat.displayNameTe else cat.displayNameEn) },
                                onClick = {
                                    selectedCategory = cat.displayNameEn
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Payment Mode row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("UPI", "Cash", "Card", "NetBanking").forEach { mode ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedPaymentMode == mode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedPaymentMode = mode }
                        ) {
                            Text(
                                text = mode,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (selectedPaymentMode == mode) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(if (isTelugu) "గమనికలు (Notes)" else "Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (amt == null || !amt.isFinite() || amt <= 0.0) {
                        amountValidationError = if (isTelugu) "సున్నా కంటే ఎక్కువ సరైన మొత్తాన్ని నమోదు చేయండి." else "Enter a valid amount greater than zero."
                    } else if (title.isBlank()) {
                        amountValidationError = if (isTelugu) "వివరణ నమోదు చేయండి." else "Enter a description."
                    } else {
                        val updated = expense.copy(
                            title = title,
                            amount = amt,
                            category = selectedCategory,
                            paymentMode = selectedPaymentMode,
                            notes = notes
                        )
                        onConfirm(updated)
                    }
                },
                enabled = true
            ) {
                Text(if (isTelugu) "అప్‌డేట్ చేయి" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isTelugu) "రద్దు" else "Cancel")
            }
        }
    )
}

@Composable
fun SetCategoryBudgetDialog(
    category: String,
    currentLimit: Double,
    isTelugu: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var limitStr by remember { mutableStateOf("${currentLimit.toInt()}") }
    var limitValidationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isTelugu) "$category బడ్జెట్ సవరణ" else "Set $category Budget",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isTelugu) "ఈ విభాగానికి ప్రస్తుత నెలవారీ పరిమితి: ₹${currentLimit.toInt()}"
                    else "Current monthly budget limit: ₹${currentLimit.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick Increment/Decrement Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(-500, 500, 1000, 2000).forEach { delta ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val current = limitStr.toDoubleOrNull()
                                    if (current == null || !current.isFinite() || current <= 0.0) {
                                        limitValidationError = if (isTelugu) "ముందుగా సరైన పరిమితిని నమోదు చేయండి." else "Enter a valid limit before adjusting."
                                    } else {
                                        val next = (current + delta).coerceAtLeast(100.0)
                                        limitStr = "${next.toInt()}"
                                        limitValidationError = null
                                    }
                                }
                        ) {
                            Text(
                                text = if (delta > 0) "+₹$delta" else "-₹${-delta}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = limitStr,
                    onValueChange = {
                        limitStr = it
                        limitValidationError = null
                    },
                    label = { Text(if (isTelugu) "నెలవారీ పరిమితి (Limit ₹)" else "Monthly Limit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                limitValidationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = limitStr.toDoubleOrNull()
                    if (limit == null || !limit.isFinite() || limit <= 0.0) {
                        limitValidationError = if (isTelugu) "సున్నా కంటే ఎక్కువ సరైన పరిమితిని నమోదు చేయండి." else "Enter a valid limit greater than zero."
                    } else {
                        onConfirm(limit)
                    }
                },
                enabled = true
            ) {
                Text(if (isTelugu) "సేవ్ చేయి" else "Save Limit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isTelugu) "రద్దు" else "Cancel")
            }
        }
    )
}

@Composable
fun MonthNavigationBar(
    selectedMonthTimestamp: Long,
    isAllMonthsSelected: Boolean,
    availableMonths: List<Long>,
    isTelugu: Boolean,
    isCurrentMonth: Boolean,
    monthLabel: String,
    onSelectMonth: (Long) -> Unit,
    onSelectAllMonths: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onResetToCurrent: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Month Nav Switcher Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Previous Month Arrow (<)
                IconButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Month",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Center Month Dropdown Button
                Box {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable { showDropdown = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isAllMonthsSelected) Icons.Default.History else Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = monthLabel,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isTelugu) "🌐 అన్ని నెలల చరిత్ర (All Time)" else "🌐 All Months History (All Time)",
                                    fontWeight = if (isAllMonthsSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSelectAllMonths()
                                showDropdown = false
                            }
                        )
                        availableMonths.forEach { monthTs ->
                            val isSel = !isAllMonthsSelected && monthTs == selectedMonthTimestamp
                            val cal = Calendar.getInstance().apply { timeInMillis = monthTs }
                            val nowCal = Calendar.getInstance()
                            val isThisMonth = cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                                    cal.get(Calendar.MONTH) == nowCal.get(Calendar.MONTH)
                            val label = formatMonthDisplay(monthTs, isTelugu) + if (isThisMonth) {
                                if (isTelugu) " (ప్రస్తుత నెల)" else " (Current Month)"
                            } else ""

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "📅 $label",
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onSelectMonth(monthTs)
                                    showDropdown = false
                                }
                            )
                        }
                    }
                }

                // Next Month Arrow (>)
                IconButton(
                    onClick = onNextMonth,
                    enabled = !isCurrentMonth && !isAllMonthsSelected,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Month",
                        tint = if (!isCurrentMonth && !isAllMonthsSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Horizontal Month Pills Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // "All History" Chip
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAllMonthsSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.clickable { onSelectAllMonths() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isAllMonthsSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isTelugu) "అన్ని నెలలు" else "All History",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isAllMonthsSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isAllMonthsSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Month Chips
                items(availableMonths) { monthTs ->
                    val isSelected = !isAllMonthsSelected && monthTs == selectedMonthTimestamp
                    val cal = Calendar.getInstance().apply { timeInMillis = monthTs }
                    val nowCal = Calendar.getInstance()
                    val isThisMonth = cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                            cal.get(Calendar.MONTH) == nowCal.get(Calendar.MONTH)

                    val shortName = formatShortMonth(monthTs, isTelugu)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else if (isThisMonth) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { onSelectMonth(monthTs) }
                    ) {
                        Text(
                            text = if (isThisMonth) "$shortName (Now)" else shortName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected || isThisMonth) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else if (isThisMonth) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Notice badge when looking at previous month archive or all history
            if (!isCurrentMonth || isAllMonthsSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isAllMonthsSelected) {
                            if (isTelugu) "📂 పూర్తి చరిత్రను చూస్తున్నారు" else "📂 Viewing all-time history"
                        } else {
                            if (isTelugu) "📅 గత నెల రికార్డులు చూస్తున్నారు ($monthLabel)" else "📅 Viewing archive for $monthLabel"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isTelugu) "ప్రస్తుత నెలకు వెళ్ళండి ➔" else "Back to Current Month ➔",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onResetToCurrent() }
                    )
                }
            }
        }
    }
}

fun formatMonthDisplay(timestamp: Long, isTelugu: Boolean): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val monthIndex = cal.get(Calendar.MONTH)
    val year = cal.get(Calendar.YEAR)
    val teluguMonths = arrayOf(
        "జనవరి", "ఫిబ్రవరి", "మార్చి", "ఏప్రిల్", "మే", "జూన్",
        "జూలై", "ఆగస్టు", "సెప్టెంబర్", "అక్టోబర్", "నవంబర్", "డిసెంబర్"
    )
    return if (isTelugu && monthIndex in 0..11) {
        "${teluguMonths[monthIndex]} $year"
    } else {
        SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(Date(timestamp))
    }
}

fun formatShortMonth(timestamp: Long, isTelugu: Boolean): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val monthIndex = cal.get(Calendar.MONTH)
    val yearShort = cal.get(Calendar.YEAR) % 100
    val shortEn = SimpleDateFormat("MMM", Locale.ENGLISH).format(Date(timestamp))
    val teluguShortMonths = arrayOf(
        "జనవరి", "ఫిబ్రవరి", "మార్చి", "ఏప్రిల్", "మే", "జూన్",
        "జూలై", "ఆగస్టు", "సెప్టెంబర్", "అక్టోబర్", "నవంబర్", "డిసెంబర్"
    )
    return if (isTelugu && monthIndex in 0..11) {
        "${teluguShortMonths[monthIndex]} '$yearShort"
    } else {
        "$shortEn '$yearShort"
    }
}
