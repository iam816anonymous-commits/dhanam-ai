package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DhanamApplication
import com.example.ai.AdaptiveBudgetEngine
import com.example.ai.AgentFinancialBrain
import com.example.ai.AgentResult
import com.example.ai.ExpenseAgentEngine
import com.example.ai.ResultType
import com.example.data.local.CategoryTotal
import com.example.data.model.CategoryBudget
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.Loan
import com.example.data.preferences.PreferenceManager
import com.example.export.CsvReportGenerator
import com.example.export.PdfReportGenerator
import com.example.voice.VoiceAgentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AgentChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val agentResult: AgentResult? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DhanamApplication
    private val expenseDao = app.database.expenseDao()
    private val loanDao = app.database.loanDao()
    private val budgetDao = app.database.budgetDao()
    private val preferenceManager = app.preferenceManager
    val voiceAgentManager = VoiceAgentManager(application)

    private val agentEngine = ExpenseAgentEngine(expenseDao, loanDao, budgetDao, preferenceManager, app.merchantCache)

    // User Preferences
    val themeMode: StateFlow<String> = preferenceManager.themeMode
    val isBiometricEnabled: StateFlow<Boolean> = preferenceManager.isBiometricEnabledFlow
    val preferredLanguage: StateFlow<String> = preferenceManager.preferredLanguage
    val overallBudget: StateFlow<Double> = preferenceManager.overallBudget
    val voiceFeedbackEnabled: StateFlow<Boolean> = preferenceManager.voiceFeedbackEnabled
    val appPin: StateFlow<String?> = preferenceManager.appPin

    // In-Built Voice Engine Streams
    val isListening: StateFlow<Boolean> = voiceAgentManager.isListening
    val partialTranscript: StateFlow<String> = voiceAgentManager.partialTranscript
    val rmsDb: StateFlow<Float> = voiceAgentManager.rmsDb
    val speechError: StateFlow<String?> = voiceAgentManager.speechError
    val isTeluguTtsAvailable: StateFlow<Boolean> = voiceAgentManager.isTeluguTtsAvailable
    val isSpeaking: StateFlow<Boolean> = voiceAgentManager.isSpeaking

    // Security lock state - only locked on startup if biometrics are enrolled OR App PIN is set
    private val _isAppLocked = MutableStateFlow(
        (preferenceManager.isBiometricEnabled() && com.example.security.BiometricAuthManager.isBiometricEnrolled(application)) ||
                (preferenceManager.getAppPin() != null)
    )
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    // Database Streams
    val allExpenses: StateFlow<List<Expense>> = expenseDao.getAllExpensesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLoans: StateFlow<List<Loan>> = loanDao.getAllLoansFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryBudgets: StateFlow<List<CategoryBudget>> = budgetDao.getAllCategoryBudgetsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Agent Chat State
    private val _agentMessages = MutableStateFlow<List<AgentChatMessage>>(emptyList())
    val agentMessages: StateFlow<List<AgentChatMessage>> = _agentMessages.asStateFlow()

    // Current Screen for in-app Jarvis navigation
    private val _currentScreen = MutableStateFlow(com.example.ui.navigation.Screen.AGENT)
    val currentScreen: StateFlow<com.example.ui.navigation.Screen> = _currentScreen.asStateFlow()

    fun setCurrentScreen(screen: com.example.ui.navigation.Screen) {
        _currentScreen.value = screen
    }

    // Voice Listening Dialog State (Universal across all screens)
    private val _showVoiceListeningDialog = MutableStateFlow(false)
    val showVoiceListeningDialog: StateFlow<Boolean> = _showVoiceListeningDialog.asStateFlow()

    private val _voiceErrorMessage = MutableStateFlow<String?>(null)
    val voiceErrorMessage: StateFlow<String?> = _voiceErrorMessage.asStateFlow()

    fun setShowVoiceListeningDialog(show: Boolean) {
        _showVoiceListeningDialog.value = show
        if (!show) {
            _voiceErrorMessage.value = null
        }
    }

    // Jarvis Living Entity States
    private val _isNoting = MutableStateFlow(false)
    val isNoting: StateFlow<Boolean> = _isNoting.asStateFlow()

    private val _lastJarvisNote = MutableStateFlow<String?>(null)
    val lastJarvisNote: StateFlow<String?> = _lastJarvisNote.asStateFlow()

    private val _isProcessingAgent = MutableStateFlow(false)
    val isProcessingAgent: StateFlow<Boolean> = _isProcessingAgent.asStateFlow()

    private val _savingsTips = MutableStateFlow<List<String>>(emptyList())
    val savingsTips: StateFlow<List<String>> = _savingsTips.asStateFlow()

    private val _categoryTotals = MutableStateFlow<List<CategoryTotal>>(emptyList())
    val categoryTotals: StateFlow<List<CategoryTotal>> = _categoryTotals.asStateFlow()

    private val _adaptivePlan = MutableStateFlow<AdaptiveBudgetEngine.AdaptiveBudgetPlan?>(null)
    val adaptivePlan: StateFlow<AdaptiveBudgetEngine.AdaptiveBudgetPlan?> = _adaptivePlan.asStateFlow()

    // Local Financial Health Metrics computed on-device
    val financialHealth: StateFlow<AgentFinancialBrain.LocalFinancialHealth> = combine(
        _categoryTotals,
        overallBudget,
        categoryBudgets,
        allLoans
    ) { totals, budget, catBudgets, loans ->
        val spent = totals.sumOf { it.total }
        AgentFinancialBrain.calculateLocalFinancialHealth(
            totalSpent = spent,
            monthlyBudget = budget,
            categoryTotals = totals,
            categoryBudgets = catBudgets,
            loans = loans
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AgentFinancialBrain.calculateLocalFinancialHealth(0.0, 35000.0, emptyList(), emptyList(), emptyList())
    )

    private val _currentMonthTimestamp = MutableStateFlow(System.currentTimeMillis())
    val currentMonthTimestamp: StateFlow<Long> = _currentMonthTimestamp.asStateFlow()

    private val _selectedMonthTimestamp = MutableStateFlow(System.currentTimeMillis())
    val selectedMonthTimestamp: StateFlow<Long> = _selectedMonthTimestamp.asStateFlow()

    private val _isAllMonthsSelected = MutableStateFlow(false)
    val isAllMonthsSelected: StateFlow<Boolean> = _isAllMonthsSelected.asStateFlow()

    // Distinct past and current months extracted from database records + past 6 calendar months
    val availableMonths: StateFlow<List<Long>> = allExpenses.map { expenses ->
        val monthSet = mutableSetOf<Long>()
        val cal = Calendar.getInstance()

        // Current month
        cal.timeInMillis = System.currentTimeMillis()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        monthSet.add(cal.timeInMillis)

        // Include preceding 6 months so user can browse previous months immediately
        for (i in 1..6) {
            val pastCal = Calendar.getInstance()
            pastCal.timeInMillis = cal.timeInMillis
            pastCal.add(Calendar.MONTH, -i)
            monthSet.add(pastCal.timeInMillis)
        }

        // Include any historical months that contain expense transactions
        expenses.forEach { exp ->
            val expCal = Calendar.getInstance()
            expCal.timeInMillis = exp.timestamp
            expCal.set(Calendar.DAY_OF_MONTH, 1)
            expCal.set(Calendar.HOUR_OF_DAY, 0)
            expCal.set(Calendar.MINUTE, 0)
            expCal.set(Calendar.SECOND, 0)
            expCal.set(Calendar.MILLISECOND, 0)
            monthSet.add(expCal.timeInMillis)
        }

        monthSet.sortedDescending()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered expenses corresponding strictly to the chosen month (or all months if isAllMonthsSelected is true)
    val expensesForSelectedMonth: StateFlow<List<Expense>> = combine(
        allExpenses,
        _selectedMonthTimestamp,
        _isAllMonthsSelected
    ) { expenses, selectedMonth, isAll ->
        if (isAll) {
            expenses
        } else {
            val cal = Calendar.getInstance()
            cal.timeInMillis = selectedMonth
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis

            cal.add(Calendar.MONTH, 1)
            cal.add(Calendar.MILLISECOND, -1)
            val end = cal.timeInMillis

            expenses.filter { it.timestamp in start..end }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        val isTe = preferenceManager.getPreferredLanguage() == "te"
        val greeting = if (isTe) {
            "నమస్కారం! నేను మీ ఫోన్‌లో నివసించే చిన్న ఆర్థిక సహాయకుడిని (Dhanam AI). మీ ప్రతి పైసా ఖర్చును లెక్కించడం, మీ బడ్జెట్‌ను కాపాడడం మరియు అప్పుల ఖాటాను గుర్తుపెట్టుకోవడమే నా పని. మీ ఖర్చు లేదా అప్పు వివరాలను చెప్పండి!"
        } else {
            "Hello! I am Dhanam, your dedicated little on-device financial companion entity. I live exclusively to watch over your daily expenses, guard your budget, and track your lending khata. Tell me your expense or loan details!"
        }
        _agentMessages.value = listOf(
            AgentChatMessage(isUser = false, text = greeting)
        )

        refreshMonthMetrics()
        refreshSavingsTips()

        // Proactive initialization: Auto-allocate category budgets if not yet set
        viewModelScope.launch(Dispatchers.IO) {
            val existing = budgetDao.getAllCategoryBudgets()
            if (existing.isEmpty()) {
                reallocateBudgetsAdaptively(preferenceManager.getOverallBudget())
            } else {
                // Initialize adaptive plan metadata
                val startOfAll = 0L
                val now = System.currentTimeMillis()
                val totals = expenseDao.getCategoryTotalsInRange(startOfAll, now)
                val allExps = expenseDao.getExpensesInRange(startOfAll, now)
                _adaptivePlan.value = AdaptiveBudgetEngine.computeAdaptiveBudgets(
                    overallBudget = preferenceManager.getOverallBudget(),
                    historicalTotals = totals,
                    totalExpenseCount = allExps.size
                )
            }
        }

        // Failsafe check: If biometric was enabled in prefs but device has no registered biometrics,
        // automatically disable it so the user is never locked out without registered credentials
        val hasEnrolledBiometrics = com.example.security.BiometricAuthManager.isBiometricEnrolled(app)
        val hasPin = preferenceManager.getAppPin() != null
        if (preferenceManager.isBiometricEnabled() && !hasEnrolledBiometrics) {
            preferenceManager.setBiometricEnabled(false)
        }
        if (!hasEnrolledBiometrics && !hasPin) {
            _isAppLocked.value = false
        }
    }

    fun unlockApp() {
        _isAppLocked.value = false
    }

    fun lockApp() {
        val hasEnrolledBiometrics = com.example.security.BiometricAuthManager.isBiometricEnrolled(app)
        val hasPin = preferenceManager.getAppPin() != null
        if ((preferenceManager.isBiometricEnabled() && hasEnrolledBiometrics) || hasPin) {
            _isAppLocked.value = true
        }
    }

    fun setAppPin(pin: String?) {
        preferenceManager.setAppPin(pin)
    }

    fun verifyAppPin(pin: String): Boolean {
        if (preferenceManager.verifyPin(pin)) {
            unlockApp()
            return true
        }
        return false
    }

    fun setThemeMode(mode: String) {
        preferenceManager.setThemeMode(mode)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        preferenceManager.setBiometricEnabled(enabled)
        if (!enabled && preferenceManager.getAppPin() == null) {
            _isAppLocked.value = false
        }
    }

    fun setVoiceFeedbackEnabled(enabled: Boolean) {
        preferenceManager.setVoiceFeedbackEnabled(enabled)
        if (!enabled) {
            voiceAgentManager.stopSpeaking()
        }
    }

    fun setPreferredLanguage(lang: String) {
        preferenceManager.setPreferredLanguage(lang)
        refreshSavingsTips()
    }

    fun clearAgentChat() {
        _agentMessages.value = emptyList()
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            expenseDao.deleteAllExpenses()
            loanDao.deleteAllLoans()
            _agentMessages.value = emptyList()
            refreshMonthMetrics()
            refreshSavingsTips()
        }
    }

    fun startVoiceListening(
        isTelugu: Boolean,
        onResult: (String) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        _showVoiceListeningDialog.value = true
        _voiceErrorMessage.value = null
        voiceAgentManager.startListening(
            isTelugu = isTelugu,
            onResult = { res ->
                _showVoiceListeningDialog.value = false
                _voiceErrorMessage.value = null
                onResult(res)
            },
            onError = { err ->
                _voiceErrorMessage.value = err
                onError?.invoke(err)
            }
        )
    }

    fun stopVoiceListening() {
        voiceAgentManager.stopListening()
        _showVoiceListeningDialog.value = false
        _voiceErrorMessage.value = null
    }

    fun submitVoiceListeningImmediately() {
        voiceAgentManager.submitListeningImmediately()
        _showVoiceListeningDialog.value = false
        _voiceErrorMessage.value = null
    }

    fun cancelVoiceListening() {
        voiceAgentManager.cancelListening()
        _showVoiceListeningDialog.value = false
        _voiceErrorMessage.value = null
    }

    fun reallocateBudgetsAdaptively(budgetAmount: Double = preferenceManager.getOverallBudget()) {
        viewModelScope.launch(Dispatchers.IO) {
            val startOfAll = 0L
            val now = System.currentTimeMillis()
            val totals = expenseDao.getCategoryTotalsInRange(startOfAll, now)
            val allExps = expenseDao.getExpensesInRange(startOfAll, now)
            val plan = AdaptiveBudgetEngine.computeAdaptiveBudgets(
                overallBudget = budgetAmount,
                historicalTotals = totals,
                totalExpenseCount = allExps.size
            )
            budgetDao.insertAll(plan.categoryBudgets)
            _adaptivePlan.value = plan
            refreshMonthMetrics()
        }
    }

    fun setOverallMonthlyBudget(amount: Double) {
        preferenceManager.setOverallBudget(amount)
        reallocateBudgetsAdaptively(amount)
        refreshMonthMetrics()
    }

    fun updateCategoryBudget(category: String, limit: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            budgetDao.insertOrUpdateBudget(CategoryBudget(category, limit))
            refreshMonthMetrics()
        }
    }

    fun processAgentInput(input: String) {
        if (input.isBlank() || _isProcessingAgent.value) return

        val isTe = preferenceManager.getPreferredLanguage() == "te" || input.any { it in '\u0C00'..'\u0C7F' }
        val processedInput = com.example.voice.VoicePostProcessor.filterTranscript(input, isTe)

        val userMsg = AgentChatMessage(isUser = true, text = processedInput)
        _agentMessages.value = _agentMessages.value + userMsg
        _isProcessingAgent.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val result = agentEngine.processUserInput(processedInput, preferenceManager.getPreferredLanguage())
            val agentResponse = AgentChatMessage(
                isUser = false,
                text = result.message,
                agentResult = result
            )

            _agentMessages.value = _agentMessages.value + agentResponse
            _isProcessingAgent.value = false

            // Living Jarvis Noting feedback
            val hasItems = result.recordedExpenses.isNotEmpty() || result.recordedExpense != null ||
                    result.recordedLoans.isNotEmpty() || result.recordedLoan != null
            if (hasItems) {
                _isNoting.value = true
                val count = result.recordedExpenses.size.coerceAtLeast(1)
                _lastJarvisNote.value = "⚡ Jarvis noted $count item${if (count > 1) "s" else ""} (₹${result.totalRecordedAmount.toInt()})"
                viewModelScope.launch {
                    delay(2500)
                    _isNoting.value = false
                }
            }

            // In-app Jarvis navigation commands
            when (result.navigationAction) {
                "KHATA" -> _currentScreen.value = com.example.ui.navigation.Screen.KHATA
                "EXPENSES" -> _currentScreen.value = com.example.ui.navigation.Screen.EXPENSES
                "INSIGHTS" -> _currentScreen.value = com.example.ui.navigation.Screen.INSIGHTS
                "SETTINGS" -> _currentScreen.value = com.example.ui.navigation.Screen.SETTINGS
                "AGENT" -> _currentScreen.value = com.example.ui.navigation.Screen.AGENT
                "LOCK" -> lockApp()
                "REBALANCE" -> reallocateBudgetsAdaptively(preferenceManager.getOverallBudget())
            }

            refreshMonthMetrics()
            refreshSavingsTips()

            // Proactively learn from new spending and adapt category budget limits!
            if (result.recordedExpense != null || result.recordedExpenses.isNotEmpty()) {
                reallocateBudgetsAdaptively(preferenceManager.getOverallBudget())
            }

            // Voice response feedback
            if (preferenceManager.isVoiceFeedbackEnabled()) {
                val spoken = result.spokenSummary ?: result.message
                val isTe = preferenceManager.getPreferredLanguage() == "te"
                val textTe = result.spokenSummaryTe ?: spoken
                val textEn = result.spokenSummaryEn ?: spoken
                voiceAgentManager.speak(textTe, textEn, isTe)
            }

            // Post system notification for overspending alerts if user granted permission
            if (result.isOverspent && result.overspentWarning != null) {
                val exp = result.recordedExpense ?: result.recordedExpenses.firstOrNull()
                if (exp != null) {
                    val isTe = preferenceManager.getPreferredLanguage() == "te"
                    com.example.notifications.NotificationReminderManager.showOverspendingAlertNotification(
                        context = app,
                        category = exp.category,
                        spent = exp.amount,
                        limit = preferenceManager.getOverallBudget(),
                        isTelugu = isTe
                    )
                }
            }
        }
    }

    fun undoLastAgentAction(result: AgentResult) {
        viewModelScope.launch(Dispatchers.IO) {
            if (result.recordedExpenses.isNotEmpty()) {
                result.recordedExpenses.forEach { exp -> expenseDao.deleteExpense(exp) }
            } else {
                result.recordedExpense?.let { expenseDao.deleteExpense(it) }
            }

            if (result.recordedLoans.isNotEmpty()) {
                result.recordedLoans.forEach { loan -> loanDao.deleteLoan(loan) }
            } else {
                result.recordedLoan?.let { loanDao.deleteLoan(it) }
            }

            val isTe = preferenceManager.getPreferredLanguage() == "te"
            val undoMsg = if (isTe) "సరే, నమోదు చేసిన రికార్డులను తొలగించాను." else "Action undone, records removed."
            _agentMessages.value = _agentMessages.value + AgentChatMessage(isUser = false, text = undoMsg)
            refreshMonthMetrics()
            reallocateBudgetsAdaptively(preferenceManager.getOverallBudget())
        }
    }

    fun selectMonth(timestamp: Long) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        _selectedMonthTimestamp.value = cal.timeInMillis
        _currentMonthTimestamp.value = cal.timeInMillis
        _isAllMonthsSelected.value = false
        refreshMonthMetrics()
    }

    fun selectAllMonths() {
        _isAllMonthsSelected.value = true
        refreshMonthMetrics()
    }

    fun previousMonth() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = _selectedMonthTimestamp.value
        cal.add(Calendar.MONTH, -1)
        selectMonth(cal.timeInMillis)
    }

    fun nextMonth() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = _selectedMonthTimestamp.value
        cal.add(Calendar.MONTH, 1)
        selectMonth(cal.timeInMillis)
    }

    fun resetToCurrentMonth() {
        selectMonth(System.currentTimeMillis())
    }

    fun refreshMonthMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isAllMonthsSelected.value) {
                val totals = expenseDao.getCategoryTotalsInRange(0L, Long.MAX_VALUE)
                _categoryTotals.value = totals
            } else {
                val cal = Calendar.getInstance()
                cal.timeInMillis = _selectedMonthTimestamp.value
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                cal.add(Calendar.MONTH, 1)
                cal.add(Calendar.MILLISECOND, -1)
                val end = cal.timeInMillis

                val totals = expenseDao.getCategoryTotalsInRange(start, end)
                _categoryTotals.value = totals
            }
        }
    }

    fun refreshSavingsTips() {
        viewModelScope.launch(Dispatchers.IO) {
            val isTe = preferenceManager.getPreferredLanguage() == "te"
            _savingsTips.value = agentEngine.generateMonthlySavingsTips(isTe)
        }
    }

    fun addManualExpense(
        title: String,
        amount: Double,
        category: String,
        paymentMode: String,
        notes: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val exp = Expense(
                title = title,
                amount = amount,
                category = category,
                paymentMode = paymentMode,
                notes = notes,
                timestamp = System.currentTimeMillis()
            )
            expenseDao.insertExpense(exp)
            refreshMonthMetrics()
            refreshSavingsTips()
            checkAndNotifyOverspending(category)
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch(Dispatchers.IO) {
            expenseDao.updateExpense(expense)
            refreshMonthMetrics()
            refreshSavingsTips()
            checkAndNotifyOverspending(expense.category)
        }
    }

    private fun checkAndNotifyOverspending(category: String) {
        try {
            val catTotal = _categoryTotals.value.firstOrNull { it.category.equals(category, ignoreCase = true) }?.total ?: 0.0
            val catBudget = categoryBudgets.value.firstOrNull { it.category.equals(category, ignoreCase = true) }?.monthlyLimit ?: 5000.0
            if (catTotal > catBudget) {
                val isTe = preferenceManager.getPreferredLanguage() == "te"
                com.example.notifications.NotificationReminderManager.showOverspendingAlertNotification(
                    getApplication(),
                    category,
                    catTotal,
                    catBudget,
                    isTe
                )
            }
        } catch (e: Exception) {
            // Non-critical background alert failure guard
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch(Dispatchers.IO) {
            expenseDao.deleteExpense(expense)
            refreshMonthMetrics()
            refreshSavingsTips()
        }
    }

    fun addManualLoan(
        personName: String,
        amount: Double,
        dueDateDays: Int,
        notes: String,
        phoneNumber: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, dueDateDays)
            val loan = Loan(
                personName = personName,
                amount = amount,
                dueDateMillis = cal.timeInMillis,
                notes = notes,
                phoneNumber = phoneNumber,
                isSettled = false
            )
            loanDao.insertLoan(loan)
        }
    }

    fun updateLoan(loan: Loan) {
        viewModelScope.launch(Dispatchers.IO) {
            loanDao.updateLoan(loan)
        }
    }

    fun extendLoanDueDate(loan: Loan, additionalDays: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val baseTime = loan.dueDateMillis ?: System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                timeInMillis = baseTime
                add(Calendar.DAY_OF_YEAR, additionalDays)
            }
            val updated = loan.copy(dueDateMillis = cal.timeInMillis)
            loanDao.updateLoan(updated)
        }
    }

    fun recordRepayment(loan: Loan, repaymentAmount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val newPaid = loan.amountPaid + repaymentAmount
            val updated = loan.copy(
                amountPaid = newPaid,
                isSettled = newPaid >= loan.amount
            )
            loanDao.updateLoan(updated)
        }
    }

    fun deleteLoan(loan: Loan) {
        viewModelScope.launch(Dispatchers.IO) {
            loanDao.deleteLoan(loan)
        }
    }

    fun sendRepaymentReminder(context: Context, loan: Loan) {
        val isTe = preferenceManager.getPreferredLanguage() == "te"
        val dueStr = loan.dueDateMillis?.let {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
        } ?: ""

        val reminderText = if (isTe) {
            "నమస్తే ${loan.personName} గారు, మీరు తీసుకున్న ₹${loan.remainingAmount.toInt()} బాకీ రీపేమెంట్ తేదీ $dueStr అని సున్నితంగా గుర్తు చేస్తున్నాను. వీలైనంత త్వరగా చెల్లించగలరు. ధన్యవాదాలు!"
        } else {
            "Hi ${loan.personName}, a friendly reminder regarding the ₹${loan.remainingAmount.toInt()} lent balance due on $dueStr. Please arrange for repayment at your convenience. Thank you!"
        }
        // Also trigger high-priority system notification reminder on device
        com.example.notifications.NotificationReminderManager.showRepaymentReminderNotification(context, loan, isTe)

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, reminderText)
                if (loan.phoneNumber.isNotBlank()) {
                    val cleanPhone = loan.phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
                    setPackage("com.whatsapp")
                    putExtra("jid", "$cleanPhone@s.whatsapp.net")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, reminderText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                "Send Friendly Repayment Reminder"
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    fun exportAndShareMonthlyPdf(context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = _selectedMonthTimestamp.value
            val monthName = if (_isAllMonthsSelected.value) "All_Months_History" else SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
            val year = cal.get(Calendar.YEAR)

            val totalSpent = _categoryTotals.value.sumOf { it.total }
            val budgets = categoryBudgets.value
            val expenses = expensesForSelectedMonth.value
            val loans = allLoans.value
            val tips = _savingsTips.value

            val pdfFile = PdfReportGenerator.generateAndShareMonthlyReport(
                context = context,
                monthName = monthName,
                year = year,
                totalExpenses = totalSpent,
                overallBudget = overallBudget.value,
                categoryTotals = _categoryTotals.value,
                categoryBudgets = budgets,
                expensesList = expenses,
                loansList = loans,
                savingsTips = tips
            )

            if (pdfFile != null && pdfFile.exists()) {
                PdfReportGenerator.openOrSharePdf(context, pdfFile)
                onResult(true, "PDF Generated successfully: ${pdfFile.name}")
            } else {
                onResult(false, "Could not generate PDF report.")
            }
        }
    }

    fun exportAndShareCsv(context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = _selectedMonthTimestamp.value
            val monthName = if (_isAllMonthsSelected.value) "All_Months_History" else SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
            val year = cal.get(Calendar.YEAR)

            val expenses = expensesForSelectedMonth.value
            val loans = allLoans.value

            val csvFile = CsvReportGenerator.generateAndShareCsv(
                context = context,
                expensesList = expenses,
                loansList = loans,
                monthName = monthName,
                year = year
            )

            if (csvFile != null && csvFile.exists()) {
                CsvReportGenerator.openOrShareCsv(context, csvFile)
                onResult(true, "CSV Export generated: ${csvFile.name}")
            } else {
                onResult(false, "Could not generate CSV export.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceAgentManager.shutdown()
    }
}
