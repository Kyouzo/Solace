package dev.kyouzo.solace.tracking

import android.content.Context
import com.google.android.gms.location.Priority

/**
 * How aggressively the foreground service samples GPS.
 *
 * MAXIMUM asks the fused provider for 1-second, high-accuracy fixes and records any movement of 1 m or more.
 * It costs noticeably more battery than BALANCED, and GPS error is typically a few metres, so very small
 * steps are real signal only when the fix itself is good (hence the accuracy cap).
 */
enum class TrackingPrecision(
    val priority: Int,
    val intervalMs: Long,
    val minUpdateDistanceM: Float,
    val minMoveM: Double,
    val maxAccuracyM: Float
) {
    MAXIMUM(Priority.PRIORITY_HIGH_ACCURACY, 1_000L, 1f, 1.0, 35f),
    BALANCED(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L, 10f, 5.0, 100f);

    fun next(): TrackingPrecision = entries[(ordinal + 1) % entries.size]

    companion object {
        private const val KEY = "precision"
        private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        fun current(context: Context): TrackingPrecision = entries.firstOrNull { it.name == prefs(context).getString(KEY, null) } ?: MAXIMUM
        fun set(context: Context, value: TrackingPrecision) { prefs(context).edit().putString(KEY, value.name).apply() }
    }
}
