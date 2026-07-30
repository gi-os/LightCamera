package com.gios.lightcamera

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last crash, kept where the app can show it to you.
 *
 * A sideloaded app on a phone with no developer tools to hand is a black box: it either works or
 * it "immediately crashes", and the stack trace — the one piece of information that would settle
 * it in a second — is in a logcat nobody has a cable for. So the handler writes it to a file and
 * the settings screen offers it back.
 *
 * This exists because v1.5.6 died in a view-model constructor: `viewModelScope` runs on
 * `Dispatchers.Main.immediate`, so a collector started in `init` ran *synchronously inside the
 * constructor*, a `StateFlow` handed it the current value straight away, and it wrote to a field
 * declared further down the class — still null. One line of stack trace would have named it.
 *
 * Deliberately not crash *reporting*: nothing leaves the phone, there is no network call here and
 * no identifier of any kind. It is a text file in the app's own storage that the app will show
 * you, and delete when you ask.
 */
object CrashLog {

    private const val FILE = "last-crash.txt"

    /** Chain onto whatever was already installed rather than replacing it. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            // Always hand on: swallowing it would leave the process wedged instead of dying,
            // which is worse than crashing and is not this object's decision to make.
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val when0 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val version = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        file(context).writeText(
            buildString {
                appendLine("Roll $version — $when0")
                appendLine("thread: ${thread.name}")
                appendLine()
                append(stack)
            },
        )
    }

    private fun file(context: Context) = File(context.filesDir, FILE)

    /** The stack trace of the last crash, or null if this build has never fallen over. */
    fun last(context: Context): String? = runCatching {
        val f = file(context)
        if (f.exists() && f.length() > 0) f.readText() else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
