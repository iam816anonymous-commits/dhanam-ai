package com.example

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.data.local.AppDatabase
import com.example.data.preferences.PreferenceManager
import com.example.util.AppLogger

class DhanamApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferenceManager: PreferenceManager
        private set

    lateinit var merchantCache: com.example.ai.AdaptiveMerchantCache
        private set

    private var backgroundTimestamp: Long = 0L

    @Volatile
    var shouldRelockOnResume: Boolean = false

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferenceManager = PreferenceManager(this)
        merchantCache = com.example.ai.AdaptiveMerchantCache(this)

        // Observe application lifecycle for auto-relock in background
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    backgroundTimestamp = System.currentTimeMillis()
                }

                override fun onStart(owner: LifecycleOwner) {
                    if (backgroundTimestamp > 0L) {
                        val timeoutSec = preferenceManager.getAutoLockTimeoutSeconds()
                        val elapsedSec = (System.currentTimeMillis() - backgroundTimestamp) / 1000L
                        if (elapsedSec >= timeoutSec && (preferenceManager.hasAppPin() || preferenceManager.isBiometricEnabled())) {
                            shouldRelockOnResume = true
                        }
                    }
                    backgroundTimestamp = 0L
                }
            })
        } catch (e: Exception) {
            AppLogger.w("DhanamApplication", "ProcessLifecycleOwner registration deferred", e)
        }

        // Initialize Android 8.0+ notification channels
        com.example.notifications.NotificationReminderManager.initNotificationChannel(this)

        // Schedule battery-aware background budget & khata evaluation worker (WorkManager)
        try {
            com.example.worker.DhanamBudgetKhataWorker.schedulePeriodic(this)
        } catch (e: Exception) {
            AppLogger.e("DhanamApplication", "Failed to schedule periodic worker", e)
        }
    }
}
