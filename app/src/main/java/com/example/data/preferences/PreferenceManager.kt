package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dhanam_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(isBiometricEnabled())
    val isBiometricEnabledFlow: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _overallBudget = MutableStateFlow(getOverallBudget())
    val overallBudget: StateFlow<Double> = _overallBudget.asStateFlow()

    private val _preferredLanguage = MutableStateFlow(getPreferredLanguage())
    val preferredLanguage: StateFlow<String> = _preferredLanguage.asStateFlow()

    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        _isBiometricEnabled.value = enabled
    }

    fun getOverallBudget(): Double = prefs.getFloat(KEY_OVERALL_BUDGET, 35000f).toDouble()

    fun setOverallBudget(budget: Double) {
        prefs.edit().putFloat(KEY_OVERALL_BUDGET, budget.toFloat()).apply()
        _overallBudget.value = budget
    }

    fun getPreferredLanguage(): String = prefs.getString(KEY_LANGUAGE, "en") ?: "en"

    fun setPreferredLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
        _preferredLanguage.value = lang
    }

    private val _voiceFeedbackEnabled = MutableStateFlow(isVoiceFeedbackEnabled())
    val voiceFeedbackEnabled: StateFlow<Boolean> = _voiceFeedbackEnabled.asStateFlow()

    fun isVoiceFeedbackEnabled(): Boolean = prefs.getBoolean(KEY_VOICE_FEEDBACK, true)

    fun setVoiceFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_FEEDBACK, enabled).apply()
        _voiceFeedbackEnabled.value = enabled
    }

    private val _appPin = MutableStateFlow(getAppPin())
    val appPin: StateFlow<String?> = _appPin.asStateFlow()

    fun getAppPin(): String? = prefs.getString(KEY_APP_PIN, null)

    fun hasAppPin(): Boolean = !prefs.getString(KEY_APP_PIN, null).isNullOrBlank()

    fun setAppPin(pin: String?) {
        if (pin.isNullOrBlank()) {
            prefs.edit().remove(KEY_APP_PIN).apply()
            _appPin.value = null
        } else {
            val hashed = hashPin(pin)
            prefs.edit().putString(KEY_APP_PIN, hashed).apply()
            _appPin.value = hashed
        }
    }

    fun verifyPin(inputPin: String): Boolean {
        val stored = prefs.getString(KEY_APP_PIN, null) ?: return false
        // Support migration if previously stored plain 4 digits
        if (stored.length == 4 && stored == inputPin) {
            setAppPin(inputPin) // upgrade to hash
            return true
        }
        return stored == hashPin(inputPin)
    }

    private fun hashPin(pin: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val salt = "Dhanam_Security_Salt_v2#"
            val bytes = md.digest((salt + pin).toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            pin
        }
    }

    fun hasSeededDemo(): Boolean = prefs.getBoolean(KEY_DEMO_SEEDED, false)

    fun setDemoSeeded(seeded: Boolean) {
        prefs.edit().putBoolean(KEY_DEMO_SEEDED, seeded).apply()
    }

    fun getLastBudgetWarningTimestamp(): Long = prefs.getLong(KEY_LAST_BUDGET_WARNING, 0L)

    fun setLastBudgetWarningTimestamp(ts: Long) {
        prefs.edit().putLong(KEY_LAST_BUDGET_WARNING, ts).apply()
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode" // "SYSTEM", "LIGHT", "DARK"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_APP_PIN = "app_pin"
        private const val KEY_OVERALL_BUDGET = "overall_budget"
        private const val KEY_LANGUAGE = "preferred_language" // "te" or "en"
        private const val KEY_VOICE_FEEDBACK = "voice_feedback"
        private const val KEY_DEMO_SEEDED = "demo_seeded"
        private const val KEY_LAST_BUDGET_WARNING = "last_budget_warning_ts"
    }
}
