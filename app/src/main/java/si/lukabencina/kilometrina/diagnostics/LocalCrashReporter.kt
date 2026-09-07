package si.lukabencina.kilometrina.diagnostics

import android.content.Context
import java.time.Instant

object LocalCrashReporter {
    private const val PREFS = "local_crash_diagnostics"
    private const val KEY_LAST_CRASH = "last_crash"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val summary = buildString {
                    appendLine("Čas: ${Instant.now()}")
                    appendLine("Nit: ${thread.name}")
                    appendLine("Napaka: ${throwable::class.java.name}")
                    appendLine("Sporočilo: ${throwable.message.orEmpty()}")
                    appendLine()
                    throwable.stackTrace.take(30).forEach { appendLine("at $it") }
                }.take(16_000)
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, summary)
                    .commit()
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun lastCrash(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CRASH, null)
            ?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_CRASH)
            .apply()
    }
}
