package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.preferences.PreferenceManager
import com.example.notifications.NotificationReminderManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * DhanamBudgetKhataWorker:
 * Lightweight, battery-aware periodic background worker optimized for Android 8.1 (API 27)
 * and low-memory architectures (e.g. MediaTek Helio P23).
 *
 * Responsibilities:
 * 1. Evaluates current monthly budget adherence. Dispatches high-priority system notifications
 *    if spending reaches or exceeds 80% (and 100%) of the budget.
 * 2. Checks Lending Khata records for dues maturing within the next 48 hours and issues friendly reminders.
 */
class DhanamBudgetKhataWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val db = AppDatabase.getInstance(context)
            val preferenceManager = PreferenceManager(context)
            val isTelugu = preferenceManager.getPreferredLanguage() == "te"

            // 1. Budget Adherence Check (Current Month)
            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfMonth = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            val endOfMonth = cal.timeInMillis - 1

            val expenses = db.expenseDao().getExpensesInRange(startOfMonth, endOfMonth)
            val totalSpent = expenses.sumOf { it.amount }
            val overallBudget = preferenceManager.getOverallBudget()

            if (overallBudget > 0.0) {
                val ratio = totalSpent / overallBudget
                if (ratio >= 0.80) {
                    val lastWarn = preferenceManager.getLastBudgetWarningTimestamp()
                    val now = System.currentTimeMillis()
                    // Prevent spam: notify at most once every 18 hours unless crossed 100%
                    if (now - lastWarn > 18 * 3600 * 1000L || (ratio >= 1.0 && now - lastWarn > 6 * 3600 * 1000L)) {
                        NotificationReminderManager.showBudgetThresholdAlert(
                            context = context,
                            spent = totalSpent,
                            overallBudget = overallBudget,
                            ratioPercent = (ratio * 100).toInt(),
                            isTelugu = isTelugu
                        )
                        preferenceManager.setLastBudgetWarningTimestamp(now)
                    }
                }
            }

            // 2. Lending Khata Upcoming Dues Check (Next 48 Hours)
            val now = System.currentTimeMillis()
            val window48h = now + (48 * 3600 * 1000L)
            val loans = db.loanDao().getAllLoans()

            val upcomingLoans = loans.filter { loan ->
                !loan.isSettled &&
                        loan.dueDateMillis != null &&
                        loan.dueDateMillis!! in now..window48h
            }

            val overdueLoans = loans.filter { loan ->
                !loan.isSettled &&
                        loan.dueDateMillis != null &&
                        loan.dueDateMillis!! < now
            }

            for (loan in upcomingLoans) {
                if (NotificationReminderManager.shouldNotifyLoan(context, loan.id)) {
                    NotificationReminderManager.showRepaymentReminderNotification(
                        context = context,
                        loan = loan,
                        isTelugu = isTelugu
                    )
                }
            }

            for (loan in overdueLoans) {
                if (NotificationReminderManager.shouldNotifyLoan(context, loan.id)) {
                    NotificationReminderManager.showOverdueLoanNotification(
                        context = context,
                        loan = loan,
                        isTelugu = isTelugu
                    )
                }
            }

            Result.success()
        } catch (e: Exception) {
            com.example.util.AppLogger.e("DhanamBudgetKhataWorker", "Worker failed during execution", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "dhanam_budget_khata_periodic_worker"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<DhanamBudgetKhataWorker>(
                6, TimeUnit.HOURS,
                30, TimeUnit.MINUTES // 30-minute flex interval
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
