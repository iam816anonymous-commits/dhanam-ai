package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.room.withTransaction
import androidx.lifecycle.viewModelScope
import com.example.DhanamApplication
import com.example.ai.AdaptiveBudgetEngine
import com.example.ai.AgentFinancialBrain
import com.example.ai.AgentSelfTestResult
import com.example.ai.AgentSelfTestRunner
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
import com.example.export.CsvExpenseImporter
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
import kotlinx.coroutines.withContext
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
    val preferenceManager = app.preferenceManager
    val voiceAgentManager = VoiceAgentManager(application)

    private val agentEngine = ExpenseAgentEngine(expenseDao, loanDao, budgetDao, preferenceManager, app.merchantCache, app.database)
    private var pendingAgentDeletion: Expense? = null

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
    val voiceLanguage: StateFlow<String> = voiceAgentManager.voiceLanguage

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

    fun setVoiceErrorMessage(msg: String?) {
        _voiceErrorMessage.value = msg
    }

    fun clearVoiceError() {
        _voiceErrorMessage.value = null
    }

    // Jarvis Living Entity States
    private val _isNoting = MutableStateFlow(false)
    val isNoting: StateFlow<Boolean> = _isNoting.asStateFlow()

    private val _lastJarvisNote = MutableStateFlow<String?>(null)
    val lastJarvisNote: StateFlow<String?> = _lastJarvisNote.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _isProcessingAgent = MutableStateFlow(false)
    val isProcessingAgent: StateFlow<Boolean> = _isProcessingAgent.asStateFlow()

    // Unified Real-Time Visual Agent State Stream for global UI indicators
    val agentState: StateFlow<com.example.ui.components.JarvisCoreState> = combine(
        voiceAgentManager.isListening,
        _isThinking,
        _isProcessingAgent,
        _isNoting,
        voiceAgentManager.isSpeaking
    ) { listening, thinking, processing, noting, speaking ->
        when {
            noting -> com.example.ui.components.JarvisCoreState.NOTING
            processing -> com.example.ui.components.JarvisCoreState.PROCESSING
            thinking -> com.example.ui.components.JarvisCoreState.THINKING
            listening -> com.example.ui.components.JarvisCoreState.LISTENING
            speaking -> com.example.ui.components.JarvisCoreState.SPEAKING
            else -> com.example.ui.components.JarvisCoreState.IDLE
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.ui.components.JarvisCoreState.IDLE
    )

    // Real-Time Agent On-Screen Touch & Physical Interaction Stream
    private val _activeTouchInteraction = MutableStateFlow<com.example.ui.components.AgentTouchInteraction?>(null)
    val activeTouchInteraction: StateFlow<com.example.ui.components.AgentTouchInteraction?> = _activeTouchInteraction.asStateFlow()

    fun triggerAgentTouch(
        titleTe: String,
        titleEn: String,
        subTe: String = "",
        subEn: String = "",
        targetXFraction: Float = 0.5f,
        targetYFraction: Float = 0.5f,
        iconEmoji: String = "👆",
        durationMs: Long = 2800L
    ) {
        val interaction = com.example.ui.components.AgentTouchInteraction(
            actionTitleTe = titleTe,
            actionTitleEn = titleEn,
            actionSubtitleTe = subTe,
            actionSubtitleEn = subEn,
            targetXFraction = targetXFraction,
            targetYFraction = targetYFraction,
            iconEmoji = iconEmoji
        )
        _activeTouchInteraction.value = interaction
        viewModelScope.launch {
            kotlinx.coroutines.delay(durationMs)
            if (_activeTouchInteraction.value?.id == interaction.id) {
                _activeTouchInteraction.value = null
            }
        }
    }

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

    val isCloudAiOptIn: StateFlow<Boolean> = preferenceManager.cloudAiOptIn

    fun setCloudAiOptIn(optIn: Boolean) {
        preferenceManager.setCloudAiOptIn(optIn)
    }

    fun setAppPin(pin: String?) {
        preferenceManager.setAppPin(pin)
    }

    fun changeAppPin(oldPin: String, newPin: String): Boolean {
        return preferenceManager.changeAppPin(oldPin, newPin)
    }

    fun removeAppPin(currentPin: String): Boolean {
        return preferenceManager.removeAppPin(currentPin)
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
            app.database.withTransaction {
                expenseDao.deleteAllExpenses()
                loanDao.deleteAllLoans()
                budgetDao.deleteAllCategoryBudgets()
            }
            _agentMessages.value = emptyList()
            refreshMonthMetrics()
            refreshSavingsTips()
        }
    }

    fun setVoiceLanguage(lang: String) {
        voiceAgentManager.setVoiceLanguage(lang)
    }

    fun startVoiceListeningWithLanguage(
        language: String,
        onResult: (String) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        _showVoiceListeningDialog.value = true
        _voiceErrorMessage.value = null
        voiceAgentManager.startListening(
            language = language,
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
        if (input.isBlank() || _isProcessingAgent.value || _isThinking.value) return

        val isTe = preferenceManager.getPreferredLanguage() == "te" || input.any { it in '\u0C00'..'\u0C7F' }
        val processedInput = com.example.voice.VoicePostProcessor.filterTranscript(input, isTe)

        val userMsg = AgentChatMessage(isUser = true, text = processedInput)
        _agentMessages.value = _agentMessages.value + userMsg
        
        // Instant visual feedback: Agent is thinking/analyzing intent
        _isThinking.value = true

        viewModelScope.launch(Dispatchers.IO) {
            // Give a short visible thinking frame for natural perception, then transition to processing
            kotlinx.coroutines.delay(200)
            _isThinking.value = false
            _isProcessingAgent.value = true

            val pendingDeletion = pendingAgentDeletion
            val confirmationText = processedInput.trim().lowercase(Locale.ROOT)
            val result = try {
                when {
                    pendingDeletion != null && confirmationText in setOf(
                        "cancel", "cancel delete", "no", "keep it", "రద్దు", "వద్దు"
                    ) -> {
                        pendingAgentDeletion = null
                        AgentResult(
                            success = true,
                            type = ResultType.ERROR,
                            message = if (preferenceManager.getPreferredLanguage() == "te")
                                "సరే, ఖర్చు అలాగే ఉంచాను." else "Cancelled. The expense was kept."
                        )
                    }
                    pendingDeletion != null && confirmationText in setOf(
                        "confirm delete", "confirm", "yes", "yes delete", "delete it", "అవును", "అవును తొలగించు"
                    ) -> {
                        expenseDao.deleteExpense(pendingDeletion)
                        pendingAgentDeletion = null
                        AgentResult(
                            success = true,
                            type = ResultType.EXPENSE_RECORDED,
                            message = if (preferenceManager.getPreferredLanguage() == "te")
                                "'${pendingDeletion.title}' ఖర్చును తొలగించాను."
                            else "Deleted expense '${pendingDeletion.title}' (₹${pendingDeletion.amount.toInt()})."
                        )
                    }
                    pendingDeletion != null -> AgentResult(
                        success = false,
                        type = ResultType.CONFIRMATION_REQUIRED,
                        message = if (preferenceManager.getPreferredLanguage() == "te")
                            "తొలగింపును నిర్ధారించడానికి CONFIRM DELETE అని లేదా ఆపడానికి CANCEL అని చెప్పండి."
                        else "A deletion is pending. Reply CONFIRM DELETE to proceed or CANCEL to keep the expense."
                    )
                    else -> agentEngine.processUserInput(processedInput, preferenceManager.getPreferredLanguage())
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                com.example.util.AppLogger.e(
                    "MainViewModel",
                    "Agent command failed; no success response will be shown",
                    error
                )
                AgentResult(
                    success = false,
                    type = ResultType.ERROR,
                    message = if (preferenceManager.getPreferredLanguage() == "te") {
                        "ఈ చర్యను పూర్తి చేయలేకపోయాను. మీ రికార్డులు సరిగా ఉన్నాయో తనిఖీ చేసి మళ్లీ ప్రయత్నించండి."
                    } else {
                        "I couldn't complete that action. Please verify your records and try again."
                    }
                )
            }
            if (result.confirmationRequired) {
                pendingAgentDeletion = result.pendingDeletionExpense
            }

            val agentResponse = AgentChatMessage(
                isUser = false,
                text = result.message,
                agentResult = result
            )

            _agentMessages.value = _agentMessages.value + agentResponse
            _isProcessingAgent.value = false

            // Living Jarvis Noting feedback with visible on-screen touch animation
            val hasItems = result.recordedExpenses.isNotEmpty() || result.recordedExpense != null ||
                    result.recordedLoans.isNotEmpty() || result.recordedLoan != null
            if (hasItems) {
                _isNoting.value = true
                val count = result.recordedExpenses.size.coerceAtLeast(1)
                _lastJarvisNote.value = "⚡ Jarvis noted $count item${if (count > 1) "s" else ""} (₹${result.totalRecordedAmount.toInt()})"

                // Trigger visible on-screen touch pointer & ripple
                if (result.recordedExpenses.isNotEmpty() || result.recordedExpense != null) {
                    val firstExp = result.recordedExpense ?: result.recordedExpenses.first()
                    triggerAgentTouch(
                        titleTe = "ఖర్చు నమోదు చేసింది: ₹${result.totalRecordedAmount.toInt()}",
                        titleEn = "Recorded: ₹${result.totalRecordedAmount.toInt()} (${firstExp.category})",
                        subTe = if (count > 1) "$count ఖర్చులు లెడ్జర్ లో రాసింది" else "'${firstExp.title}' విభాగం: ${firstExp.category}",
                        subEn = if (count > 1) "Added $count transactions to ledger" else "Added '${firstExp.title}'",
                        targetXFraction = 0.5f,
                        targetYFraction = 0.42f,
                        iconEmoji = "✍️"
                    )
                } else if (result.recordedLoans.isNotEmpty() || result.recordedLoan != null) {
                    val firstLoan = result.recordedLoan ?: result.recordedLoans.first()
                    triggerAgentTouch(
                        titleTe = "అప్పు నమోదు చేసింది: ₹${firstLoan.amount.toInt()}",
                        titleEn = "Recorded Lending: ₹${firstLoan.amount.toInt()}",
                        subTe = "${firstLoan.personName} ఖాతాలో రాసింది",
                        subEn = "Added to ${firstLoan.personName}'s khata",
                        targetXFraction = 0.5f,
                        targetYFraction = 0.42f,
                        iconEmoji = "🤝"
                    )
                }

                viewModelScope.launch {
                    delay(2500)
                    _isNoting.value = false
                }
            } else if (result.message.contains("రద్దు") || result.message.contains("Undid")) {
                triggerAgentTouch(
                    titleTe = "లావాదేవీ రద్దు చేసింది",
                    titleEn = "Undid Last Transaction",
                    subTe = "లెడ్జర్ నుండి తీసివేయబడింది",
                    subEn = "Removed latest record from ledger",
                    targetXFraction = 0.5f,
                    targetYFraction = 0.45f,
                    iconEmoji = "↩️"
                )
            } else if (result.message.contains("Settled") || result.message.contains("జమ")) {
                triggerAgentTouch(
                    titleTe = "బాకీ జమ చేసింది",
                    titleEn = "Applied Loan Repayment",
                    subTe = "ఖాటా లెడ్జర్ నవీకరించబడింది",
                    subEn = "Updated borrower's khata",
                    targetXFraction = 0.5f,
                    targetYFraction = 0.45f,
                    iconEmoji = "💵"
                )
            }

            // In-app Jarvis navigation commands with visible screen touch & tab tap animation
            when (result.navigationAction) {
                "KHATA" -> {
                    triggerAgentTouch(
                        titleTe = "ఖాతా ట్యాబ్ ఓపెన్ చేసింది",
                        titleEn = "Tapped 'Khata' Screen",
                        subTe = "అప్పులు & వసూళ్ల లెడ్జర్",
                        subEn = "Switched to Lending Khata",
                        targetXFraction = 0.50f,
                        targetYFraction = 0.94f,
                        iconEmoji = "🧭"
                    )
                    _currentScreen.value = com.example.ui.navigation.Screen.KHATA
                }
                "EXPENSES" -> {
                    triggerAgentTouch(
                        titleTe = "ఖర్చుల ట్యాబ్ ఓపెన్ చేసింది",
                        titleEn = "Tapped 'Expenses' Screen",
                        subTe = "ఖర్చుల పూర్తి చరిత్ర",
                        subEn = "Switched to All Expenses",
                        targetXFraction = 0.30f,
                        targetYFraction = 0.94f,
                        iconEmoji = "📊"
                    )
                    _currentScreen.value = com.example.ui.navigation.Screen.EXPENSES
                }
                "INSIGHTS" -> {
                    triggerAgentTouch(
                        titleTe = "రిపోర్ట్ ట్యాబ్ ఓపెన్ చేసింది",
                        titleEn = "Tapped 'Report' Screen",
                        subTe = "PDF & బడ్జెట్ విశ్లేషణ",
                        subEn = "Switched to Monthly Report",
                        targetXFraction = 0.70f,
                        targetYFraction = 0.94f,
                        iconEmoji = "📈"
                    )
                    _currentScreen.value = com.example.ui.navigation.Screen.INSIGHTS
                }
                "SETTINGS" -> {
                    triggerAgentTouch(
                        titleTe = "సెట్టింగ్స్ ఓపెన్ చేసింది",
                        titleEn = "Tapped 'Settings' Screen",
                        subTe = "భద్రత & భాష కాన్ఫిగరేషన్",
                        subEn = "Switched to App Settings",
                        targetXFraction = 0.90f,
                        targetYFraction = 0.94f,
                        iconEmoji = "⚙️"
                    )
                    _currentScreen.value = com.example.ui.navigation.Screen.SETTINGS
                }
                "AGENT" -> {
                    triggerAgentTouch(
                        titleTe = "జార్విస్ హోమ్ కి మారింది",
                        titleEn = "Tapped 'Home' Screen",
                        subTe = "హోమ్ సంభాషణ",
                        subEn = "Returned to Main Entity",
                        targetXFraction = 0.10f,
                        targetYFraction = 0.94f,
                        iconEmoji = "🤖"
                    )
                    _currentScreen.value = com.example.ui.navigation.Screen.AGENT
                }
                "LOCK" -> lockApp()
                "REBALANCE" -> {
                    triggerAgentTouch(
                        titleTe = "బడ్జెట్ రీబ్యాలెన్స్ చేసింది",
                        titleEn = "Rebalanced Category Budgets",
                        subTe = "ఖర్చుల సరళి ఆధారంగా బడ్జెట్ నవీకరించబడింది",
                        subEn = "Adapted limits based on spending",
                        targetXFraction = 0.5f,
                        targetYFraction = 0.35f,
                        iconEmoji = "⚖️"
                    )
                    reallocateBudgetsAdaptively(preferenceManager.getOverallBudget())
                }
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
            // Do not send a warning based on an invented default limit; the user must have a saved budget.
            val catBudget = categoryBudgets.value.firstOrNull { it.category.equals(category, ignoreCase = true) }?.monthlyLimit
                ?: return
            if (catBudget > 0.0 && catTotal > catBudget) {
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
            try {
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
            } catch (ex2: Exception) {
                com.example.util.AppLogger.e("MainViewModel", "Failed to launch reminder chooser", ex2)
                Toast.makeText(context, "No app available to share message", Toast.LENGTH_SHORT).show()
            }
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

    /** Runs read-only diagnostics against routing, CSV parsing, spending analysis and Room queries. */
    suspend fun runAgentSelfTest(): List<AgentSelfTestResult> =
        AgentSelfTestRunner.run(app.database)

    /**
     * Imports only validated expense rows from a user-selected CSV. Existing transactions are
     * preserved; duplicate rows are skipped using a stable transaction fingerprint.
     */
    fun importExpensesFromCsv(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("Could not open the selected CSV file.")
                    input.bufferedReader(Charsets.UTF_8).use { reader -> CsvExpenseImporter.parse(reader) }
                }
                if (!parsed.headerFound) {
                    onResult(false, parsed.issues.firstOrNull()?.reason ?: "This file does not contain a supported expense CSV table.")
                    return@launch
                }
                if (parsed.expenses.isEmpty()) {
                    val details = parsed.issues.firstOrNull()?.reason ?: "No valid expense rows were found."
                    onResult(false, details)
                    return@launch
                }

                var inserted = 0
                var duplicates = 0
                withContext(Dispatchers.IO) {
                    app.database.withTransaction {
                        val known = expenseDao.getAllExpenses()
                            .map(CsvExpenseImporter::fingerprint)
                            .toMutableSet()
                        parsed.expenses.forEach { expense ->
                            if (known.add(CsvExpenseImporter.fingerprint(expense))) {
                                expenseDao.insertExpense(expense)
                                inserted++
                            } else {
                                duplicates++
                            }
                        }
                    }
                }
                refreshMonthMetrics()
                val invalid = parsed.issues.size
                val message = "Imported $inserted expense(s); skipped $duplicates duplicate(s) and $invalid invalid row(s)."
                onResult(inserted > 0 || duplicates > 0, message)
            } catch (error: Exception) {
                com.example.util.AppLogger.e("MainViewModel", "Expense CSV import failed", error)
                onResult(false, "Import failed. Check that the file is a valid Dhanam AI or transaction CSV and try again.")
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
