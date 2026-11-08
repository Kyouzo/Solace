package dev.kyouzo.solace.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import dev.kyouzo.solace.R
import dev.kyouzo.solace.data.local.AppDatabase
import dev.kyouzo.solace.data.remote.SyncRepository
import dev.kyouzo.solace.data.remote.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class LocationTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storeLock = Mutex() // fixes arrive quickly; filter against the previous point strictly in order
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val dao by lazy { AppDatabase.get(this).locationDao() }
    private val precision by lazy { TrackingPrecision.current(this) }
    private val capture by lazy {
        val identity = getSharedPreferences("identity", Context.MODE_PRIVATE)
        val deviceId = identity.getString("device_id", null) ?: UUID.randomUUID().toString().also { identity.edit().putString("device_id", it).apply() }
        LocationCapture(identity.getString("user_id", "local")!!, deviceId)
    }
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val fixes = result.locations
            scope.launch { storeLock.withLock { fixes.forEach { store(it) } } }
        }
    }

    override fun onCreate() { super.onCreate(); createChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(42, notification(), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        requestUpdates()
        return START_STICKY
    }

    @Suppress("MissingPermission")
    private fun requestUpdates() {
        client.removeLocationUpdates(callback)
        val request = LocationRequest.Builder(precision.priority, precision.intervalMs)
            .setMinUpdateIntervalMillis(precision.intervalMs / 2)
            .setMinUpdateDistanceMeters(precision.minUpdateDistanceM)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setWaitForAccurateLocation(false)
            .build() // no max-update-delay: fixes are delivered live, not batched
        client.requestLocationUpdates(request, callback, mainLooper)
    }

    private suspend fun store(location: Location) {
        if (!LocationFilter.accepts(location, dao.last(), precision)) return
        val (point, cloud, peer) = capture.create(location, batteryLevel())
        dao.insertCapturedPoint(point, cloud, peer)
        PlaceMonitor.check(this, "me", point.latitude, point.longitude)
        if (SyncRepository.sharingEnabled(this)) SyncScheduler.kick(this)
    }

    private fun batteryLevel(): Int? {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = battery.getIntExtra("level", -1)
        val scale = battery.getIntExtra("scale", -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else null
    }

    override fun onDestroy() { client.removeLocationUpdates(callback); scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("tracking", getString(R.string.notif_channel_tracking), NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification() = NotificationCompat.Builder(this, "tracking")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.notif_tracking_title))
        .setContentText(getString(R.string.notif_tracking_text))
        .setOngoing(true)
        .build()
}
