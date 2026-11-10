package com.animevostbr.core

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
  * The last crash, kept on the device: nothing is sent anywhere. Settings shows it and lets the user
  * share the text, so a crash can be reported without a cable and adb.
  */
object CrashLog {

    private const val FILE = "last_crash.txt"
    private const val MAX_CHARS = 16_000

    fun install(context: Context, versionName: String) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file(app).writeText(report(versionName, thread.name, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Device and app details plus the stack trace; no user data. */
    fun report(versionName: String, threadName: String, error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("AnimeVostBR $versionName")
            appendLine("Время: $time")
            appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Поток: $threadName")
            appendLine()
            append(trace)
        }.take(MAX_CHARS)
    }

    fun last(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context) = File(context.filesDir, FILE)
}
