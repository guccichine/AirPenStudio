package studio.airpen.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(app: Context) {
        val ctx = app.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            write(ctx, error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun write(context: Context, error: Throwable) {
        try {
            val sw = StringWriter()
            sw.append(Date().toString()).append('\n')
            sw.append(error.toString()).append('\n')
            error.printStackTrace(PrintWriter(sw))
            File(context.applicationContext.filesDir, FILE).writeText(sw.toString())
            Log.e("AirPenCrash", "saved crash", error)
        } catch (_: Throwable) {
        }
    }

    fun read(context: Context): String? = try {
        val f = File(context.applicationContext.filesDir, FILE)
        if (f.exists() && f.length() > 0) f.readText() else null
    } catch (_: Throwable) {
        null
    }

    fun clear(context: Context) {
        try {
            File(context.applicationContext.filesDir, FILE).delete()
        } catch (_: Throwable) {
        }
    }
}
