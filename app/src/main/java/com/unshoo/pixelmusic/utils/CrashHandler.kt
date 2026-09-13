package com.unshoo.pixelmusic.utils

import android.content.Context
import android.content.SharedPreferences
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.unshoo.pixelmusic.BuildConfig

/**
 * Data class representing a saved crash log entry.
 */
data class CrashLogData(
    val timestamp: Long,
    val formattedDate: String,
    val exceptionMessage: String,
    val stackTrace: String,
    val appVersion: String = "",
    val commitId: String = ""
) {
    val displayVersion: String
        get() = when {
            appVersion.isBlank() -> "Unknown"
            appVersion.startsWith("v", ignoreCase = true) -> appVersion
            else -> "v$appVersion"
        }

    val displayCommitId: String
        get() = when {
            commitId.isBlank() -> "Unknown"
            commitId.startsWith("#") -> commitId.removePrefix("#")
            else -> commitId
        }

    /**
     * Returns the full crash log formatted for display or sharing.
     */
    fun getFullLog(): String {
        return buildString {
            appendLine("=== PixelMusic Crash Report ===")
            appendLine("Date: $formattedDate")
            appendLine("version: $displayVersion commit:#$displayCommitId")
            appendLine("Exception: $exceptionMessage")
            appendLine()
            appendLine("Stack Trace:")
            appendLine(stackTrace)
        }
    }
}

/**
 * Custom UncaughtExceptionHandler that saves crash information to SharedPreferences
 * so it can be displayed to the user when the app restarts.
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val PREFS_NAME = "crash_handler_prefs"
    private const val KEY_HAS_CRASH = "has_crash"
    private const val KEY_TIMESTAMP = "crash_timestamp"
    private const val KEY_EXCEPTION_MESSAGE = "crash_exception_message"
    private const val KEY_STACK_TRACE = "crash_stack_trace"
    private const val KEY_APP_VERSION = "crash_app_version"
    private const val KEY_COMMIT_ID = "crash_commit_id"
    private const val MAX_STACK_TRACE_CHARS = 16_000

    private lateinit var appContext: Context
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Installs this crash handler as the default uncaught exception handler.
     * Should be called in Application.onCreate().
     */
    fun install(context: Context) {
        appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            saveCrashLog(throwable)
        } catch (e: Exception) {
            // Ignore any errors during crash saving
        }

        // Call the default handler to allow normal crash behavior
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun saveCrashLog(throwable: Throwable) {
        val timestamp = System.currentTimeMillis()
        val isOom = throwable is OutOfMemoryError
        val stackTrace = if (isOom) {
            // Avoid StringWriter/PrintWriter expansion during an OOM. A full stack trace can
            // require many allocations and may recursively fail before the original crash is saved.
            buildCompactStackTrace(throwable)
        } else {
            getStackTraceString(throwable).take(MAX_STACK_TRACE_CHARS)
        }
        val exceptionMessage = throwable.message ?: throwable.javaClass.simpleName
        val appVersion = BuildConfig.VERSION_NAME
        val commitId = BuildConfig.GIT_COMMIT_HASH

        // Use commit() instead of apply() to ensure data is written synchronously
        // before the process terminates
        prefs.edit().apply {
            putBoolean(KEY_HAS_CRASH, true)
            putLong(KEY_TIMESTAMP, timestamp)
            putString(KEY_EXCEPTION_MESSAGE, exceptionMessage)
            putString(KEY_STACK_TRACE, stackTrace)
            putString(KEY_APP_VERSION, appVersion)
            putString(KEY_COMMIT_ID, commitId)
            commit() // Synchronous write - ensures data is saved before process dies
        }
    }

    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }

    private fun buildCompactStackTrace(throwable: Throwable): String {
        val builder = StringBuilder(1024)
        builder.append(throwable.javaClass.name)
        throwable.message?.let { builder.append(": ").append(it) }
        throwable.stackTrace.take(24).forEach { element ->
            builder.append('\n').append("    at ").append(element)
        }
        return builder.toString()
    }

    /**
     * Checks if there is a saved crash log from a previous session.
     */
    fun hasCrashLog(): Boolean {
        if (!::appContext.isInitialized) return false
        return prefs.getBoolean(KEY_HAS_CRASH, false)
    }

    /**
     * Retrieves the saved crash log data.
     * Returns null if no crash log exists.
     */
    fun getCrashLog(): CrashLogData? {
        if (!hasCrashLog()) return null

        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0)
        val exceptionMessage = prefs.getString(KEY_EXCEPTION_MESSAGE, "Unknown error") ?: "Unknown error"
        val stackTrace = prefs.getString(KEY_STACK_TRACE, "") ?: ""
        val appVersion = prefs.getString(KEY_APP_VERSION, null) ?: BuildConfig.VERSION_NAME
        val commitId = prefs.getString(KEY_COMMIT_ID, null) ?: BuildConfig.GIT_COMMIT_HASH

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(timestamp))

        return CrashLogData(
            timestamp = timestamp,
            formattedDate = formattedDate,
            exceptionMessage = exceptionMessage,
            stackTrace = stackTrace,
            appVersion = appVersion,
            commitId = commitId
        )
    }

    /**
     * Clears the saved crash log.
     * Should be called after the user has acknowledged the crash report.
     */
    fun clearCrashLog() {
        if (!::appContext.isInitialized) return
        prefs.edit().clear().apply()
    }
}
