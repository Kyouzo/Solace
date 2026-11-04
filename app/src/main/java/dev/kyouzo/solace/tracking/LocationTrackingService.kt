package dev.kyouzo.solace.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import dev.kyouzo.solace.R
import dev.kyouzo.solace.data.local.AppDatabase
import kotlinx.coroutines.*
import java.util.UUID

class LocationTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: FusedLocationProviderClient
    private val dao by lazy { AppDatabase.get(this).locationDao() }
    private val capture by lazy {
        val identity = getSharedPreferences("identity", Context.MODE_PRIVATE)
        val deviceId = identity.getString("device_id", null) ?: UUID.randomUUID().toString().also { identity.edit().putString("device_id", it).apply() }
        LocationCapture(identity.getString("user_id", "local")!!, deviceId)
    }
    private val callback = object : LocationCallback() { override fun onLocationResult(result: LocationResult) { result.lastLocation?.let(::store) } }

    override fun onCreate() { super.onCreate(); client = LocationServices.getFusedLocationProviderClient(this); createChannel() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { startForeground(42, notification(), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0); requestUpdates(); return START_STICKY }
    @Suppress("MissingPermission") private fun requestUpdates() { client.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 20_000).setMinUpdateIntervalMillis(10_000).setMaxUpdateDelayMillis(120_000).build(), callback, mainLooper) }
    private fun store(location: Location) = scope.launch { val prior = dao.last(); if (LocationFilter.accepts(location, prior)) { val (point, cloud, peer) = capture.create(location, batteryLevel()); dao.insertCapturedPoint(point, cloud, peer) } }
    private fun batteryLevel(): Int? { val battery = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null; val level = battery.getIntExtra("level", -1); val scale = battery.getIntExtra("scale", -1); return if (level >= 0 && scale > 0) level * 100 / scale else null }
    override fun onDestroy() { client.removeLocationUpdates(callback); scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun createChannel() { if (Build.VERSION.SDK_INT >= 26) (getSystemService(NotificationManager::class.java)).createNotificationChannel(NotificationChannel("tracking", "Location sharing", NotificationManager.IMPORTANCE_LOW)) }
    private fun notification() = NotificationCompat.Builder(this, "tracking").setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("Solace location sharing").setContentText("Recording locations securely on this device").setOngoing(true).build()
}
