package dev.kyouzo.solace.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.kyouzo.solace.R
import dev.kyouzo.solace.data.local.AppDatabase

/** Local geofence alerts: notifies when a tracked person enters or leaves a saved place. */
object PlaceMonitor {
    /** [who] is a stable key ("me" or "partner"); it also selects how the notification refers to them. */
    suspend fun check(context: Context, who: String, lat: Double, lon: Double) {
        val app = context.applicationContext
        val places = AppDatabase.get(app).locationDao().places()
        if (places.isEmpty()) return
        val prefs = app.getSharedPreferences("place_state", Context.MODE_PRIVATE)
        val distance = FloatArray(1)
        for (place in places) {
            Location.distanceBetween(lat, lon, place.latitude, place.longitude, distance)
            val inside = distance[0] <= place.radiusMeters
            val key = "$who:${place.id}"
            val previous = if (prefs.contains(key)) prefs.getBoolean(key, false) else null
            prefs.edit().putBoolean(key, inside).apply()
            // First observation only records state, so installing the app never fires a false alert.
            if (previous != null && previous != inside) notify(app, "$who:${place.id}:$inside".hashCode(), app.getString(if (inside) R.string.place_arrived else R.string.place_left, app.getString(if (who == "me") R.string.who_you else R.string.who_partner), place.name))
        }
    }

    private fun notify(context: Context, id: Int, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("places", context.getString(R.string.notif_channel_places), NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(context, "places").setSmallIcon(R.drawable.ic_stat_solace).setContentTitle("Solace").setContentText(text).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
