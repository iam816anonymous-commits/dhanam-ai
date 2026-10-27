package com.example.ui.screens

import android.content.Context
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Loan
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberSecondaryLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LendingKhataScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loans by viewModel.allLoans.collectAsState()
    val preferredLang by viewModel.preferredLanguage.collectAsState()
    val isTelugu = preferredLang == "te"

    var searchQuery by remember { mutableStateOf("") }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var loanToRepay by remember { mutableStateOf<Loan?>(null) }
    var loanToEdit by remember { mutableStateOf<Loan?>(null) }

    val totalLent = loans.sumOf { it.amount }
    val totalCollected = loans.sumOf { it.amountPaid }
    val totalPending = loans.filter { !it.isSettled }.sumOf { it.remainingAmount }
    val overdueCount = loans.count { it.isOverdue }

    val baseLoans = when (selectedTabIndex) {
        1 -> loans.filter { !it.isSettled }
        2 -> loans.filter { it.isSettled }
        else -> loans
    }

    val filteredLoans = if (searchQuery.isBlank()) {
        baseLoans
    } else {
        baseLoans.filter {
            it.personName.contains(searchQuery, ignoreCase = true) ||
                    it.phoneNumber.contains(searchQuery, ignoreCase = true) ||
                    it.notes.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AmberSecondaryLight,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_loan_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Record Money Lent")
            }
        },
        modifier = modifier.testTag("khata_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))

                // Summary Hero Card
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
                                text = if (isTelugu) "ఇచ్చిన అప్పుల సారాంశం (Lending Khata)" else "Lending & Khata Overview",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (overdueCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AlertRed.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (isTelugu) "$overdueCount గడువు ముగిసింది" else "$overdueCount Overdue",
                                        color = AlertRed,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (isTelugu) "రావలసిన బాకీ (Pending)" else "Pending Dues",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "₹${totalPending.toInt()}",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AmberSecondaryLight
                                    )
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isTelugu) "వసూలైనది (Recovered)" else "Recovered",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "₹${totalCollected.toInt()}",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isTelugu) "మొత్తం ఇచ్చినది: ₹${totalLent.toInt()}" else "Total Lent: ₹${totalLent.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Tabs: All / Pending / Settled
            item {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = AmberSecondaryLight,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text(if (isTelugu) "అన్నీ (${loans.size})" else "All (${loans.size})") }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            val pendingCount = loans.count { !it.isSettled }
                            Text(if (isTelugu) "బాకీలు ($pendingCount)" else "Pending ($pendingCount)")
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = {
                            val settledCount = loans.count { it.isSettled }
                            Text(if (isTelugu) "పూర్తయినవి ($settledCount)" else "Settled ($settledCount)")
                        }
                    )
                }
            }

            // Search Bar for Borrowers
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(if (isTelugu) "పేరు లేదా వివరాలతో వెతకండి..." else "Search borrower or notes...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("khata_search_field"),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }

            if (filteredLoans.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isTelugu) "ఎటువంటి అప్పుల వివరాలు లేవు. AI తో చెప్పండి లేదా + నొక్కండి."
                            else "No lending records found. Tell Dhanam AI or tap + to record.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredLoans, key = { it.id }) { loan ->
                    LoanKhataCard(
                        loan = loan,
                        isTelugu = isTelugu,
                        onSendReminder = { viewModel.sendRepaymentReminder(context, loan) },
                        onRecordPayment = { loanToRepay = loan },
                        onEdit = { loanToEdit = loan },
                        onExtendDueDate = { days -> viewModel.extendLoanDueDate(loan, days) },
                        onDelete = { viewModel.deleteLoan(loan) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(70.dp))
            }
        }
    }

    if (showAddDialog) {
        AddLoanDialog(
            isTelugu = isTelugu,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, amt, days, notes, phone ->
                viewModel.addManualLoan(name, amt, days, notes, phone)
                showAddDialog = false
            }
        )
    }

    loanToRepay?.let { loan ->
        RecordPaymentDialog(
            loan = loan,
            isTelugu = isTelugu,
            onDismiss = { loanToRepay = null },
            onConfirm = { repaymentAmt ->
                viewModel.recordRepayment(loan, repaymentAmt)
                loanToRepay = null
            }
        )
    }

    loanToEdit?.let { loan ->
        EditLoanDialog(
            loan = loan,
            isTelugu = isTelugu,
            onDismiss = { loanToEdit = null },
            onConfirm = { updated ->
                viewModel.updateLoan(updated)
                loanToEdit = null
            }
        )
    }
}

@Composable
fun LoanKhataCard(
    loan: Loan,
    isTelugu: Boolean,
    onSendReminder: () -> Unit,
    onRecordPayment: () -> Unit,
    onEdit: () -> Unit,
    onExtendDueDate: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val dateGivenStr = sdf.format(Date(loan.dateGivenMillis))
    val dueDateStr = loan.dueDateMillis?.let { sdf.format(Date(it)) } ?: "Not set"

    val daysDiff = loan.dueDateMillis?.let {
        val diffMs = it - System.currentTimeMillis()
        (diffMs / (1000 * 60 * 60 * 24)).toInt()
    } ?: 0

    val dueRelativeText = when {
        loan.isSettled -> if (isTelugu) "పూర్తిగా చెల్లించబడింది" else "Fully Settled"
        daysDiff < 0 -> if (isTelugu) "${-daysDiff} రోజులు గడువు దాటింది!" else "Overdue by ${-daysDiff} days!"
        daysDiff == 0 -> if (isTelugu) "ఈరోజే గడువు తేదీ!" else "Due today!"
        else -> if (isTelugu) "$daysDiff రోజుల్లో గడువు ($dueDateStr)" else "Due in $daysDiff days ($dueDateStr)"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Person Name and Remaining Due
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AmberSecondaryLight.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = AmberSecondaryLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = loan.personName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (loan.phoneNumber.isNotBlank()) {
                            Text(
                                text = loan.phoneNumber,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${loan.remainingAmount.toInt()}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (loan.isSettled) SuccessGreen else AmberSecondaryLight
                        )
                    )
                    Text(
                        text = if (isTelugu) "ఇచ్చిన మొత్తం: ₹${loan.amount.toInt()}" else "Total Lent: ₹${loan.amount.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Due Date / Overdue badge with Quick Extension chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        loan.isSettled -> SuccessGreen.copy(alpha = 0.12f)
                        loan.isOverdue -> AlertRed.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (loan.isSettled) Icons.Default.CheckCircle
                            else if (loan.isOverdue) Icons.Default.NotificationsActive
                            else Icons.Default.Handshake,
                            contentDescription = null,
                            tint = when {
                                loan.isSettled -> SuccessGreen
                                loan.isOverdue -> AlertRed
                                else -> AmberSecondaryLight
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = dueRelativeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = when {
                                loan.isSettled -> SuccessGreen
                                loan.isOverdue -> AlertRed
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }

                if (!loan.isSettled) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AmberSecondaryLight.copy(alpha = 0.15f),
                        modifier = Modifier.clickable { onExtendDueDate(7) }
                    ) {
                        Text(
                            text = if (isTelugu) "+7 రోజులు" else "+7 Days",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = AmberSecondaryLight,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (loan.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = loan.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Visual Repayment Progress Bar for active loans with partial recoveries
            if (loan.amountPaid > 0.0 && !loan.isSettled) {
                val progress = (loan.amountPaid / loan.amount).toFloat().coerceIn(0f, 1f)
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isTelugu) "వసూలు అయింది: ₹${loan.amountPaid.toInt()} (${(progress * 100).toInt()}%)"
                            else "Recovered: ₹${loan.amountPaid.toInt()} (${(progress * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SuccessGreen
                        )
                        Text(
                            text = if (isTelugu) "మిగిలినది: ₹${loan.remainingAmount.toInt()}" else "Remaining: ₹${loan.remainingAmount.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = SuccessGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val context = LocalContext.current

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!loan.isSettled) {
                    // Direct Phone Call Button if phone number is provided
                    if (loan.phoneNumber.isNotBlank()) {
                        IconButton(
                            onClick = {
                                try {
                                    val cleanPhone = loan.phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(dialIntent)
                                } catch (e: Exception) {
                                    com.example.util.AppLogger.e("LendingKhataScreen", "Dialer intent failed", e)
                                    android.widget.Toast.makeText(
                                        context,
                                        if (isTelugu) "డయలర్ అందుబాటులో లేదు" else "Dialer app not available",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Send Friendly Reminder (WhatsApp / SMS)
                    Button(
                        onClick = onSendReminder,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTelugu) "రిమైండర్" else "Reminder",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // Record Payment
                    Button(
                        onClick = onRecordPayment,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SuccessGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Paid, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTelugu) "చెల్లింపు" else "Record Pay",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    Text(
                        text = if (isTelugu) "✅ రుణం పూర్తిగా పరిష్కరించబడింది" else "✅ Loan Fully Settled",
                        style = MaterialTheme.typography.labelMedium.copy(color = SuccessGreen),
                        modifier = Modifier.weight(1f)
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Loan",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddLoanDialog(
    isTelugu: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, amount: Double, dueDays: Int, notes: String, phone: String) -> Unit
) {
    var personName by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var dueDaysStr by remember { mutableStateOf("7") }
    var phoneNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isTelugu) "ఇచ్చిన అప్పు నమోదు (New Loan)" else "Record Money Lent",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text(if (isTelugu) "వ్యక్తి పేరు (Person Name)" else "Person Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = {
                        amountStr = it
                        validationError = null
                    },
                    label = { Text(if (isTelugu) "ఇచ్చిన మొత్తం (Amount ₹)" else "Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = dueDaysStr,
                    onValueChange = {
                        dueDaysStr = it
                        validationError = null
                    },
                    label = { Text(if (isTelugu) "ఎన్ని రోజుల్లో తిరిగి ఇవ్వాలి? (Due in days)" else "Repayment Due in (Days)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text(if (isTelugu) "ఫోన్ నెంబర్ (వాట్సాప్ రిమైండర్ కోసం)" else "Phone Number (for WhatsApp/SMS)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(if (isTelugu) "వివరణ (Notes/Reason)" else "Reason / Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                validationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    val days = dueDaysStr.toIntOrNull()
                    when {
                        personName.isBlank() -> validationError = if (isTelugu) "వ్యక్తి పేరు నమోదు చేయండి." else "Enter the person's name."
                        amt == null || !amt.isFinite() || amt <= 0.0 -> validationError = if (isTelugu) "సరైన మొత్తాన్ని నమోదు చేయండి." else "Enter a valid amount greater than zero."
                        days == null || days !in 1..3650 -> validationError = if (isTelugu) "గడువు 1 నుండి 3650 రోజుల మధ్య ఉండాలి." else "Due days must be between 1 and 3650."
                        else -> onConfirm(personName.trim(), amt, days, notes, phoneNumber)
                    }
                },
                enabled = true
            ) {
                Text(if (isTelugu) "సేవ్ చేయి" else "Save")
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
fun RecordPaymentDialog(
    loan: Loan,
    isTelugu: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var paymentAmtStr by remember { mutableStateOf("${loan.remainingAmount.toInt()}") }
    var paymentValidationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isTelugu) "${loan.personName} చెల్లింపు నమోదు" else "Record Repayment from ${loan.personName}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isTelugu) "మొత్తం బాకీ: ₹${loan.remainingAmount.toInt()}"
                    else "Remaining balance: ₹${loan.remainingAmount.toInt()}",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Quick Settlement Preset Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                paymentAmtStr = "${loan.remainingAmount.toInt()}"
                            }
                    ) {
                        Text(
                            text = if (isTelugu) "మొత్తం బాకీ (₹${loan.remainingAmount.toInt()})" else "Full: ₹${loan.remainingAmount.toInt()}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    if (loan.remainingAmount >= 1000.0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    paymentAmtStr = "${(loan.remainingAmount / 2).toInt()}"
                                }
                        ) {
                            Text(
                                text = "50%: ₹${(loan.remainingAmount / 2).toInt()}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = paymentAmtStr,
                    onValueChange = {
                        paymentAmtStr = it
                        paymentValidationError = null
                    },
                    label = { Text(if (isTelugu) "స్వీకరించిన మొత్తం (Amount Received ₹)" else "Received Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                paymentValidationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = paymentAmtStr.toDoubleOrNull()
                    if (amt == null || !amt.isFinite() || amt <= 0.0 || amt > loan.remainingAmount) {
                        paymentValidationError = if (isTelugu) "చెల్లింపు బాకీ మొత్తానికి మించి ఉండకూడదు." else "Enter a valid payment not exceeding the outstanding balance."
                    } else {
                        onConfirm(amt)
                    }
                }
            ) {
                Text(if (isTelugu) "చెల్లించినట్లు మార్క్ చేయి" else "Confirm Payment")
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
fun EditLoanDialog(
    loan: Loan,
    isTelugu: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Loan) -> Unit
) {
    var personName by remember { mutableStateOf(loan.personName) }
    var amountStr by remember { mutableStateOf("${loan.amount.toInt()}") }
    var phoneNumber by remember { mutableStateOf(loan.phoneNumber) }
    var notes by remember { mutableStateOf(loan.notes) }
    var additionalDaysStr by remember { mutableStateOf("0") }
    var editValidationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isTelugu) "రుణ వివరాల సవరణ" else "Edit Loan Details",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text(if (isTelugu) "వ్యక్తి పేరు (Person Name)" else "Person Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(if (isTelugu) "ఇచ్చిన మొత్తం (Total Amount ₹)" else "Total Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text(if (isTelugu) "ఫోన్ నెంబర్ (Phone Number)" else "Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = additionalDaysStr,
                    onValueChange = { additionalDaysStr = it },
                    label = { Text(if (isTelugu) "గడువు పొడిగింపు (రోజులు - Extend Days)" else "Extend Due Date (Days)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(if (isTelugu) "వివరణ (Notes)" else "Notes / Reason") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    val extDays = additionalDaysStr.toIntOrNull()
                    if (amt == null || !amt.isFinite() || amt <= 0.0 || amt < loan.amountPaid) {
                        editValidationError = if (isTelugu) "మొత్తం ఇప్పటికే చెల్లించిన మొత్తానికి తక్కువగా ఉండకూడదు." else "Loan amount must be valid and not below the amount already repaid."
                        return@Button
                    }
                    if (extDays == null || extDays !in 0..3650) {
                        editValidationError = if (isTelugu) "గడువు పొడిగింపు 0 నుండి 3650 రోజుల మధ్య ఉండాలి." else "Due-date extension must be between 0 and 3650 days."
                        return@Button
                    }
                    if (personName.isBlank()) {
                        editValidationError = if (isTelugu) "వ్యక్తి పేరు నమోదు చేయండి." else "Enter the person's name."
                        return@Button
                    }
                    val newDueDate = if (extDays != 0) {
                        val base = loan.dueDateMillis ?: System.currentTimeMillis()
                        val cal = java.util.Calendar.getInstance().apply {
                            timeInMillis = base
                            add(java.util.Calendar.DAY_OF_YEAR, extDays)
                        }
                        cal.timeInMillis
                    } else {
                        loan.dueDateMillis
                    }

                    run {
                        val updated = loan.copy(
                            personName = personName,
                            amount = amt,
                            phoneNumber = phoneNumber,
                            notes = notes,
                            dueDateMillis = newDueDate,
                            isSettled = loan.amountPaid >= amt
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
