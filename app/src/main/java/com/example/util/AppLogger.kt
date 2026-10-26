package com.example.util

import android.util.Log
import com.example.BuildConfig

/**
 * AppLogger:
 * Centralized, safe logger for Dhanam AI.
 * Strips verbose logs and stack traces in release builds to protect privacy
 * and memory on low-end devices, while maintaining a circular buffer of the
 * last sanitized error event for the user-facing "Report a problem" diagnostic screen.
 */
object AppLogger {

    data class DiagnosticError(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String,
        val message: String,
        val errorCode: String? = null
    )

    private val recentErrors = ArrayDeque<DiagnosticError>(10)

    @Synchronized
    fun getLastErrors(): List<DiagnosticError> = recentErrors.toList()

    @Synchronized
    fun getLatestError(): DiagnosticError? = recentErrors.lastOrNull()

    @Synchronized
    private fun recordError(tag: String, message: String, errorCode: String? = null) {
        if (recentErrors.size >= 10) {
            recentErrors.removeFirst()
        }
        recentErrors.add(DiagnosticError(tag = tag, message = message.take(120), errorCode = errorCode))
    }

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            try { Log.d(tag, message) } catch (_: Throwable) {}
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            try { Log.i(tag, message) } catch (_: Throwable) {}
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        recordError(tag, message, errorCode = throwable?.javaClass?.simpleName)
        if (BuildConfig.DEBUG) {
            try {
                if (throwable != null) {
                    Log.w(tag, message, throwable)
                } else {
                    Log.w(tag, message)
                }
            } catch (_: Throwable) {}
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null, errorCode: String? = null) {
        recordError(tag, message, errorCode = errorCode ?: throwable?.javaClass?.simpleName)
        if (BuildConfig.DEBUG) {
            try {
                if (throwable != null) {
                    Log.e(tag, message, throwable)
                } else {
                    Log.e(tag, message)
                }
            } catch (_: Throwable) {}
        }
    }
}
