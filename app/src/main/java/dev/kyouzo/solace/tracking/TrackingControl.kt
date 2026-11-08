package dev.kyouzo.solace.tracking

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts and stops the foreground location service and remembers whether recording is on. */
object TrackingControl {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tracking", Context.MODE_PRIVATE)

    fun isActive(context: Context): Boolean = prefs(context).getBoolean("active", false)

    fun start(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, LocationTrackingService::class.java))
        prefs(context).edit().putBoolean("active", true).apply()
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, LocationTrackingService::class.java))
        prefs(context).edit().putBoolean("active", false).apply()
    }

    /** Applies a changed precision setting immediately if recording is running. */
    fun restartIfActive(context: Context) {
        if (!isActive(context)) return
        context.stopService(Intent(context, LocationTrackingService::class.java))
        ContextCompat.startForegroundService(context, Intent(context, LocationTrackingService::class.java))
    }
}
