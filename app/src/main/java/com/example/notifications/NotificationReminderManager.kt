package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.Loan
import com.example.util.AppLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationReminderManager {

    private const val TAG = "NotificationReminder"

    const val CHANNEL_BUDGET = "dhanam_channel_budget"
    const val CHANNEL_REPAYMENT = "dhanam_channel_repayment"
    const val CHANNEL_TIPS = "dhanam_channel_tips"

    private const val PREFS_NOTIFIED = "dhanam_notified_loans"
    private const val KEY_PREFIX_LOAN_NOTIFIED = "loan_notified_"

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // 1. Budget & Overspending Channel
            val budgetChannel = NotificationChannel(
                CHANNEL_BUDGET,
                "Budget & Overspending Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts when category or overall spending crosses 80% or 100% of limits."
                enableVibration(true)
            }

            // 2. Lending Khata Repayment Channel
            val repaymentChannel = NotificationChannel(
                CHANNEL_REPAYMENT,
                "Khata Repayment & Overdue Dues",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Timely reminders for borrowers due dates and overdue lending settlements."
                enableVibration(true)
            }

            // 3. Financial Tips & Insights Channel
            val tipsChannel = NotificationChannel(
                CHANNEL_TIPS,
                "Financial Insights & Savings Tips",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Personalized savings recommendations and weekly financial health summaries."
            }

            notificationManager.createNotificationChannels(listOf(budgetChannel, repaymentChannel, tipsChannel))
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun getLaunchIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    /**
     * Checks if loan notification was already dispatched within the last 24 hours.
     */
    fun shouldNotifyLoan(context: Context, loanId: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NOTIFIED, Context.MODE_PRIVATE)
        val lastTs = prefs.getLong(KEY_PREFIX_LOAN_NOTIFIED + loanId, 0L)
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 3600 * 1000L
        return (now - lastTs) >= oneDayMs
    }

    fun recordLoanNotified(context: Context, loanId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NOTIFIED, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_PREFIX_LOAN_NOTIFIED + loanId, System.currentTimeMillis()).apply()
    }

    fun cancelLoanNotification(context: Context, loanId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(loanId.toInt() + 1000)
        notificationManager?.cancel(loanId.toInt() + 3000)
    }

    fun showRepaymentReminderNotification(context: Context, loan: Loan, isTelugu: Boolean) {
        if (!hasNotificationPermission(context)) {
            AppLogger.d(TAG, "Notification permission not granted for repayment reminder")
            return
        }
        initNotificationChannel(context)

        val dueStr = loan.dueDateMillis?.let {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
        } ?: ""

        val title = if (isTelugu) "రీపేమెంట్ రిమైండర్: ${loan.personName}" else "Repayment Due: ${loan.personName}"
        val text = if (isTelugu) {
            "${loan.personName} కి ఇచ్చిన ₹${loan.remainingAmount.toInt()} బాకీ రీపేమెంట్ తేదీ $dueStr."
        } else {
            "₹${loan.remainingAmount.toInt()} remaining lent balance is due on $dueStr."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_REPAYMENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF00BFA5.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(getLaunchIntent(context))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(loan.id.toInt() + 1000, builder.build())
        recordLoanNotified(context, loan.id)
    }

    fun showOverdueLoanNotification(context: Context, loan: Loan, isTelugu: Boolean) {
        if (!hasNotificationPermission(context)) {
            AppLogger.d(TAG, "Notification permission not granted for overdue loan")
            return
        }
        initNotificationChannel(context)

        val dueStr = loan.dueDateMillis?.let {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
        } ?: ""

        val title = if (isTelugu) "⚠️ గడువు ముగిసిన అప్పు: ${loan.personName}" else "⚠️ Overdue Lending: ${loan.personName}"
        val text = if (isTelugu) {
            "${loan.personName} కి ఇచ్చిన ₹${loan.remainingAmount.toInt()} బాకీ గడువు $dueStr న ముగిసింది. దయచేసి సంప్రదించండి."
        } else {
            "₹${loan.remainingAmount.toInt()} from ${loan.personName} was due on $dueStr and is currently overdue."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_REPAYMENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF5252.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(getLaunchIntent(context))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(loan.id.toInt() + 3000, builder.build())
        recordLoanNotified(context, loan.id)
    }

    fun showOverspendingAlertNotification(
        context: Context,
        category: String,
        spent: Double,
        limit: Double,
        isTelugu: Boolean
    ) {
        if (!hasNotificationPermission(context)) return
        initNotificationChannel(context)

        val title = if (isTelugu) "బడ్జెట్ హెచ్చరిక: $category" else "Budget Overspending Alert: $category"
        val text = if (isTelugu) {
            "$category విభాగంలో బడ్జెట్ ₹${limit.toInt()} దాటి ₹${spent.toInt()} ఖర్చు అయింది!"
        } else {
            "Spending in $category (₹${spent.toInt()}) has exceeded the monthly limit of ₹${limit.toInt()}!"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF9100.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(getLaunchIntent(context))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(2001, builder.build())
    }

    fun showBudgetThresholdAlert(
        context: Context,
        spent: Double,
        overallBudget: Double,
        ratioPercent: Int,
        isTelugu: Boolean
    ) {
        if (!hasNotificationPermission(context)) return
        initNotificationChannel(context)

        val title = if (isTelugu) {
            "నెలవారీ బడ్జెట్ హెచ్చరిక ($ratioPercent%)"
        } else {
            "Monthly Budget Alert ($ratioPercent%)"
        }

        val text = if (isTelugu) {
            "ఈ నెల మొత్తం బడ్జెట్ ₹${overallBudget.toInt()} లో ₹${spent.toInt()} ఖర్చు చేశారు ($ratioPercent%)."
        } else {
            "You have spent ₹${spent.toInt()} of your ₹${overallBudget.toInt()} monthly budget ($ratioPercent%)."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(if (ratioPercent >= 100) 0xFFFF5252.toInt() else 0xFFFF9100.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(getLaunchIntent(context))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(2002, builder.build())
    }

    fun showSavingsTipNotification(context: Context, tip: String, isTelugu: Boolean) {
        if (!hasNotificationPermission(context)) return
        initNotificationChannel(context)

        val title = if (isTelugu) "💡 పొదుపు చిట్కా" else "💡 Smart Financial Tip"
        val builder = NotificationCompat.Builder(context, CHANNEL_TIPS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF00BFA5.toInt())
            .setContentTitle(title)
            .setContentText(tip)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tip))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(getLaunchIntent(context))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(2003, builder.build())
    }

    fun showTestNotification(context: Context, isTelugu: Boolean) {
        val title = if (isTelugu) "ధనం AI నోటిఫికేషన్ సిద్ధంగా ఉంది" else "Dhanam AI Reminders Active"
        val message = if (isTelugu) "మీ బడ్జెట్ మరియు ఖాతా రిమైండర్లు సమయానికి అందుతాయి." else "Your budget and lending khata reminders will arrive on time."
        showSavingsTipNotification(context, message, isTelugu)
    }
}
