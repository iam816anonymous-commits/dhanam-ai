package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.preferences.PreferenceManager

class DhanamApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferenceManager: PreferenceManager
        private set

    lateinit var merchantCache: com.example.ai.AdaptiveMerchantCache
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferenceManager = PreferenceManager(this)
        merchantCache = com.example.ai.AdaptiveMerchantCache(this)

        // Initialize Android 8.0+ notification channel
        com.example.notifications.NotificationReminderManager.initNotificationChannel(this)

        // Schedule battery-aware background budget & khata evaluation worker (WorkManager)
        try {
            com.example.worker.DhanamBudgetKhataWorker.schedulePeriodic(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
