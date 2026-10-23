package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.Loan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationReminderManager {

    private const val CHANNEL_ID = "dhanam_alerts_channel"
    private const val CHANNEL_NAME = "Dhanam Financial Alerts & Reminders"
    private const val CHANNEL_DESC = "Notifications for loan repayment due dates, budget overspending, and savings tips."

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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

    fun showRepaymentReminderNotification(context: Context, loan: Loan, isTelugu: Boolean) {
        if (!hasNotificationPermission(context)) return
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

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(getLaunchIntent(context))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(loan.id.toInt() + 1000, builder.build())
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

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(getLaunchIntent(context))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2001, builder.build())
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
            "Monthly Budget Alert ($ratioPercent% Used)"
        }

        val text = if (isTelugu) {
            "మీరు మొత్తం బడ్జెట్ ₹${overallBudget.toInt()} లో ₹${spent.toInt()} ($ratioPercent%) ఖర్చు చేశారు. మిగిలినది: ₹${(overallBudget - spent).coerceAtLeast(0.0).toInt()}."
        } else {
            "You have spent ₹${spent.toInt()} out of your ₹${overallBudget.toInt()} monthly budget ($ratioPercent%). Remaining: ₹${(overallBudget - spent).coerceAtLeast(0.0).toInt()}."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(getLaunchIntent(context))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(3001, builder.build())
    }

    fun showTestNotification(context: Context, isTelugu: Boolean) {
        if (!hasNotificationPermission(context)) return
        initNotificationChannel(context)

        val title = if (isTelugu) "Dhanam AI నోటిఫికేషన్లు యాక్టివ్" else "Dhanam AI Alerts Active"
        val text = if (isTelugu) {
            "ఖాతా రీపేమెంట్ మరియు బడ్జెట్ హెచ్చరిక నోటిఫికేషన్లు విజయవంతంగా ఆన్ చేయబడ్డాయి."
        } else {
            "Repayment reminders and overspending alerts are configured and active."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(getLaunchIntent(context))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(999, builder.build())
    }
}
