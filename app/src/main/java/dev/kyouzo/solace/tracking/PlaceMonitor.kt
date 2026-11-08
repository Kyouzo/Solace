package dev.kyouzo.solace.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.kyouzo.solace.MainActivity
import dev.kyouzo.solace.R
import dev.kyouzo.solace.data.local.AppDatabase
import dev.kyouzo.solace.ui.places.PlaceKind

/**
 * Tells you when your partner arrives at or leaves one of the shared saved places ("Sam is home", "Sam went out").
 * It runs on your phone against your partner's latest location, so it works whenever both of you are sharing.
 */
object PlaceMonitor {
    /** Fixes worse than this say too little about where someone is to change their state. */
    private const val MAX_ACCURACY_M = 50f
    /** Minimum extra distance beyond the radius before we believe someone has really left. */
    private const val MIN_LEAVE_MARGIN_M = 10f

    /**
     * Decides whether a person is inside a place, with hysteresis so small radii don't flap on GPS noise.
     * Returns true (inside), false (outside) or [previous] when the fix is too vague or too close to the edge to tell.
     */
    internal fun decide(distanceMeters: Float, radiusMeters: Float, accuracyMeters: Float, previous: Boolean?): Boolean? {
        if (accuracyMeters > MAX_ACCURACY_M) return previous
        return when {
            distanceMeters <= radiusMeters -> true
            distanceMeters > radiusMeters + accuracyMeters.coerceAtLeast(MIN_LEAVE_MARGIN_M) -> false
            else -> previous
        }
    }

    suspend fun check(context: Context, partnerName: String?, lat: Double, lon: Double, accuracy: Float) {
        val app = context.applicationContext
        val places = AppDatabase.get(app).locationDao().places()
        if (places.isEmpty()) return
        val prefs = app.getSharedPreferences("place_state", Context.MODE_PRIVATE)
        val name = partnerName?.takeIf { it.isNotBlank() } ?: app.getString(R.string.who_partner)
        val distance = FloatArray(1)
        for (place in places) {
            Location.distanceBetween(lat, lon, place.latitude, place.longitude, distance)
            val key = "partner:${place.id}"
            val previous = if (prefs.contains(key)) prefs.getBoolean(key, false) else null
            val inside = decide(distance[0], place.radiusMeters, accuracy, previous) ?: continue
            prefs.edit().putBoolean(key, inside).apply()
            // The first observation only records state, so installing the app never fires a false alert.
            if (previous != null && previous != inside) notify(app, "$key:$inside".hashCode(), message(app, name, place.name, PlaceKind.from(place.kind), inside))
        }
    }

    /** Home gets natural wording ("is home" / "went out"); every other place names the place. */
    private fun message(context: Context, who: String, place: String, kind: PlaceKind, arrived: Boolean): String = when {
        kind == PlaceKind.HOME && arrived -> context.getString(R.string.place_partner_home_in, who)
        kind == PlaceKind.HOME -> context.getString(R.string.place_partner_home_out, who)
        arrived -> context.getString(R.string.place_arrived, who, place)
        else -> context.getString(R.string.place_left, who, place)
    }

    private fun notify(context: Context, id: Int, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("places", context.getString(R.string.notif_channel_places), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, "places")
            .setSmallIcon(R.drawable.ic_stat_solace).setContentTitle(context.getString(R.string.app_name)).setContentText(text)
            .setContentIntent(open).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
