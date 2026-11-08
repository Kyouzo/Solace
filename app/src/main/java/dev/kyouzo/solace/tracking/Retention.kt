package dev.kyouzo.solace.tracking

import android.content.Context
import dev.kyouzo.solace.data.local.AppDatabase

/** User-chosen retention for location history stored on this phone. 0 = keep until deleted. */
object Retention {
    val options = listOf(0, 30, 90, 365)
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun days(context: Context): Int = prefs(context).getInt("retention_days", 0)
    fun set(context: Context, days: Int) { prefs(context).edit().putInt("retention_days", days).apply() }

    suspend fun purge(context: Context) {
        val days = days(context)
        if (days > 0) AppDatabase.get(context).locationDao().purgeBefore(System.currentTimeMillis() - days * 86_400_000L)
    }
}
