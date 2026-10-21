package com.makay.cleaner.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local last-crash report (format=2). No messages/URLs/paths. */
object CrashLogger {
    private const val TAG = "MakayCleanerCrash"
    private const val FILE = "last_crash.txt"
    private const val MAX_CAUSE_DEPTH = 4
    private const val MAX_FRAMES_PER_CAUSE = 24
    private const val MAX_REPORT_CHARS = 12_000

    fun install(context: Context) {
        val app = context.applicationContext
        clearLegacyReport(app)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(app, thread.name, throwable) }
            Log.e(TAG, "Uncaught exception: ${throwable.javaClass.name}")
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun write(context: Context, thread: String, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            appendLine("format=2")
            appendLine("time=$stamp")
            appendLine("thread=${thread.take(80)}")
            val seen = java.util.Collections.newSetFromMap(
                java.util.IdentityHashMap<Throwable, Boolean>()
            )
            var current: Throwable? = throwable
            var depth = 0
            while (current != null && depth < MAX_CAUSE_DEPTH && seen.add(current)) {
                appendLine("exception[$depth]=${current.javaClass.name}")
                val frames = current.stackTrace
                frames.take(MAX_FRAMES_PER_CAUSE).forEach { frame ->
                    appendLine("  at ${frame.className}.${frame.methodName}:L${frame.lineNumber}")
                }
                if (frames.size > MAX_FRAMES_PER_CAUSE) {
                    appendLine("  ... ${frames.size - MAX_FRAMES_PER_CAUSE} frames omitted")
                }
                current = current.cause
                depth++
            }
            if (current != null) appendLine("... cause chain truncated")
        }
        File(context.filesDir, FILE).writeText(report.take(MAX_REPORT_CHARS))
    }

    private fun clearLegacyReport(context: Context) {
        val file = File(context.filesDir, FILE)
        runCatching {
            val format = if (file.isFile) {
                file.bufferedReader().use { it.readLine() }
            } else {
                null
            }
            if (file.isFile && format != "format=2") {
                file.writeText("Previous crash details cleared for privacy.\n")
            }
        }
    }

    fun read(context: Context): String? {
        val f = File(context.filesDir, FILE)
        return if (f.exists()) f.readText() else null
    }
}
