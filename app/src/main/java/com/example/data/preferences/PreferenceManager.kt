package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.nio.charset.StandardCharsets

sealed class PinVerifyResult {
    object Success : PinVerifyResult()
    object Incorrect : PinVerifyResult()
    data class LockedOut(val remainingSeconds: Long) : PinVerifyResult()
}

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dhanam_prefs", Context.MODE_PRIVATE)

    // Separate security preferences file excluded from cloud backup rules
    private val securePinPrefs: SharedPreferences =
        context.getSharedPreferences("dhanam_secure_pin", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(isBiometricEnabled())
    val isBiometricEnabledFlow: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _overallBudget = MutableStateFlow(getOverallBudget())
    val overallBudget: StateFlow<Double> = _overallBudget.asStateFlow()

    private val _preferredLanguage = MutableStateFlow(getPreferredLanguage())
    val preferredLanguage: StateFlow<String> = _preferredLanguage.asStateFlow()

    private val _cloudAiOptIn = MutableStateFlow(isCloudAiOptIn())
    val cloudAiOptIn: StateFlow<Boolean> = _cloudAiOptIn.asStateFlow()

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

    /**
     * Reads the budget as a Double without narrowing it through SharedPreferences' Float API.
     * Migrates the legacy Float representation in place while preserving existing user settings.
     */
    fun getOverallBudget(): Double {
        val storedValue = prefs.all[KEY_OVERALL_BUDGET]
        val parsed = when (storedValue) {
            is String -> storedValue.toDoubleOrNull()
            is Number -> storedValue.toDouble()
            else -> null
        }?.takeIf { it.isFinite() && it >= 0.0 }

        val budget = parsed ?: 35000.0
        if (storedValue !is String || parsed == null) {
            prefs.edit().putString(KEY_OVERALL_BUDGET, budget.toString()).apply()
        }
        return budget
    }

    fun setOverallBudget(budget: Double) {
        if (!budget.isFinite() || budget < 0.0) return
        prefs.edit().putString(KEY_OVERALL_BUDGET, budget.toString()).apply()
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

    /**
     * Cloud AI Opt-in:
     * By default false (strictly private on-device financial parsing).
     * If enabled by user, fallback queries can route to Gemini Cloud AI when local parsing is uncertain.
     */
    fun isCloudAiOptIn(): Boolean = prefs.getBoolean(KEY_CLOUD_AI_OPT_IN, false)

    fun setCloudAiOptIn(optIn: Boolean) {
        prefs.edit().putBoolean(KEY_CLOUD_AI_OPT_IN, optIn).apply()
        _cloudAiOptIn.value = optIn
    }

    // --- High Security Salted PIN Management with PBKDF2 (120,000 iterations) & Lockout Protection ---
    private val _appPin = MutableStateFlow(getAppPin())
    val appPin: StateFlow<String?> = _appPin.asStateFlow()

    fun getAppPin(): String? = securePinPrefs.getString(KEY_APP_PIN, null)
        ?: prefs.getString(KEY_APP_PIN, null) // Migration fallback

    fun hasAppPin(): Boolean = !getAppPin().isNullOrBlank()

    fun getPinLength(): Int = securePinPrefs.getInt(KEY_PIN_LENGTH, 4)

    /**
     * Initializes or updates PIN using PBKDF2-HMAC-SHA256 with 120,000 iterations and a per-install random salt.
     */
    fun setAppPin(pin: String?) {
        if (pin.isNullOrBlank()) {
            securePinPrefs.edit().clear().apply()
            prefs.edit().remove(KEY_APP_PIN).apply()
            _appPin.value = null
        } else {
            val salt = generateOrGetSalt()
            val hashed = hashPin(pin, salt)
            securePinPrefs.edit()
                .putString(KEY_APP_PIN, hashed)
                .putInt(KEY_PIN_LENGTH, pin.length)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply()
            prefs.edit().remove(KEY_APP_PIN).apply() // clean legacy storage
            _appPin.value = hashed
        }
    }

    fun changeAppPin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        setAppPin(newPin)
        return true
    }

    fun removeAppPin(currentPin: String): Boolean {
        if (!verifyPin(currentPin)) return false
        setAppPin(null)
        return true
    }

    fun verifyPinWithDetails(inputPin: String): PinVerifyResult {
        val now = System.currentTimeMillis()
        val lockoutUntil = securePinPrefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (lockoutUntil > now) {
            val remainingSec = (lockoutUntil - now) / 1000
            return PinVerifyResult.LockedOut(remainingSec)
        }

        val stored = getAppPin() ?: return PinVerifyResult.Incorrect
        val salt = getSalt()

        val isMatch = if (salt.isNotEmpty()) {
            val currentHash = hashPin(inputPin, salt)
            when {
                constantTimeEquals(stored, currentHash) -> true
                constantTimeEquals(stored, hashPin10000(inputPin, salt)) ||
                    constantTimeEquals(stored, hashPin5000(inputPin, salt)) -> {
                    // Upgrade old salted hashes immediately after successful verification.
                    setAppPin(inputPin)
                    true
                }
                else -> false
            }
        } else {
            // One-time compatibility path for pre-salt installations. A successful legacy
            // verification immediately rewrites the credential using the current KDF and salt.
            val legacySha256 = try {
                val md = MessageDigest.getInstance("SHA-256")
                val bytes = md.digest(("Dhanam_Security_Salt_v2#" + inputPin).toByteArray(Charsets.UTF_8))
                bytes.joinToString("") { "%02x".format(it) }
            } catch (_: Exception) { "" }

            if ((legacySha256.isNotEmpty() && constantTimeEquals(stored, legacySha256)) ||
                (stored.length in 4..6 && constantTimeEquals(stored, inputPin))) {
                setAppPin(inputPin)
                true
            } else {
                false
            }
        }

        return if (isMatch) {
            securePinPrefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply()
            PinVerifyResult.Success
        } else {
            val failedCount = securePinPrefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            val editor = securePinPrefs.edit().putInt(KEY_FAILED_ATTEMPTS, failedCount)

            if (failedCount >= 10) {
                // 5-minute lockout after 10 failed attempts
                val lockoutTime = now + (5 * 60 * 1000L)
                editor.putLong(KEY_LOCKOUT_UNTIL, lockoutTime).apply()
                PinVerifyResult.LockedOut(300)
            } else if (failedCount >= 5) {
                // 30-second lockout after 5 failed attempts
                val lockoutTime = now + (30 * 1000L)
                editor.putLong(KEY_LOCKOUT_UNTIL, lockoutTime).apply()
                PinVerifyResult.LockedOut(30)
            } else {
                editor.apply()
                PinVerifyResult.Incorrect
            }
        }
    }

    fun verifyPin(inputPin: String): Boolean {
        return verifyPinWithDetails(inputPin) is PinVerifyResult.Success
    }

    private fun generateOrGetSalt(): ByteArray {
        val existingHex = securePinPrefs.getString(KEY_PIN_SALT, null)
        if (!existingHex.isNullOrBlank()) {
            return hexToBytes(existingHex)
        }
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        securePinPrefs.edit().putString(KEY_PIN_SALT, bytesToHex(salt)).apply()
        return salt
    }

    private fun getSalt(): ByteArray {
        val hex = securePinPrefs.getString(KEY_PIN_SALT, null) ?: return ByteArray(0)
        return hexToBytes(hex)
    }

    private fun hashPin(pin: String, salt: ByteArray): String = hashPinWithIterations(pin, salt, CURRENT_PIN_KDF_ITERATIONS)

    private fun hashPin10000(pin: String, salt: ByteArray): String = hashPinWithIterations(pin, salt, 10000)

    private fun hashPin5000(pin: String, salt: ByteArray): String = hashPinWithIterations(pin, salt, 5000)

    private fun hashPinWithIterations(pin: String, salt: ByteArray, iterations: Int): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            bytesToHex(factory.generateSecret(spec).encoded)
        } finally {
            spec.clearPassword()
        }
    }

    private fun constantTimeEquals(first: String, second: String): Boolean =
        MessageDigest.isEqual(
            first.toByteArray(StandardCharsets.UTF_8),
            second.toByteArray(StandardCharsets.UTF_8)
        )

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun getLastBudgetWarningTimestamp(): Long = prefs.getLong(KEY_LAST_BUDGET_WARNING, 0L)

    fun setLastBudgetWarningTimestamp(ts: Long) {
        prefs.edit().putLong(KEY_LAST_BUDGET_WARNING, ts).apply()
    }

    fun getAutoLockTimeoutSeconds(): Long = prefs.getLong(KEY_AUTO_LOCK_TIMEOUT_SEC, 30L)

    fun setAutoLockTimeoutSeconds(seconds: Long) {
        prefs.edit().putLong(KEY_AUTO_LOCK_TIMEOUT_SEC, seconds).apply()
    }

    fun isScreenshotPrivacyEnabled(): Boolean = prefs.getBoolean(KEY_SCREENSHOT_PRIVACY, false)

    fun setScreenshotPrivacyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCREENSHOT_PRIVACY, enabled).apply()
    }

    companion object {
        private const val CURRENT_PIN_KDF_ITERATIONS = 120000
        private const val KEY_THEME_MODE = "theme_mode" // "SYSTEM", "LIGHT", "DARK"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_APP_PIN = "app_pin"
        private const val KEY_PIN_SALT = "app_pin_salt"
        private const val KEY_PIN_LENGTH = "app_pin_length"
        private const val KEY_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"
        private const val KEY_OVERALL_BUDGET = "overall_budget"
        private const val KEY_LANGUAGE = "preferred_language" // "te" or "en"
        private const val KEY_VOICE_FEEDBACK = "voice_feedback"
        private const val KEY_CLOUD_AI_OPT_IN = "cloud_ai_opt_in"
        private const val KEY_LAST_BUDGET_WARNING = "last_budget_warning_ts"
        private const val KEY_AUTO_LOCK_TIMEOUT_SEC = "auto_lock_timeout_sec"
        private const val KEY_SCREENSHOT_PRIVACY = "screenshot_privacy"
    }
}
