package dev.kyouzo.solace.tracking

import android.location.Location
import dev.kyouzo.solace.data.local.LocationPointEntity
import dev.kyouzo.solace.data.local.SyncDestination
import dev.kyouzo.solace.data.local.SyncOutboxEntity
import java.util.UUID
import kotlin.math.*

object LocationFilter {
    fun accepts(location: Location, previous: LocationPointEntity?, precision: TrackingPrecision = TrackingPrecision.MAXIMUM): Boolean =
        accepts(location.latitude, location.longitude, location.accuracy, location.time, previous, precision)

    /** Pure overload (no Android types) so the filter can be unit tested on the JVM. */
    fun accepts(latitude: Double, longitude: Double, accuracy: Float, timeMillis: Long, previous: LocationPointEntity?, precision: TrackingPrecision = TrackingPrecision.MAXIMUM): Boolean {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false
        if (accuracy <= 0f || accuracy > precision.maxAccuracyM) return false
        if (previous == null) return true
        val elapsed = (timeMillis - previous.recordedAt).coerceAtLeast(1L) / 1000.0
        val distance = distanceMeters(previous.latitude, previous.longitude, latitude, longitude)
        if (distance / elapsed >= 90.0) return false // physically implausible jump
        return distance >= precision.minMoveM
    }

    internal fun distanceMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val dLat = Math.toRadians(bLat - aLat); val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).pow(2)
        return 6_371_000.0 * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
}

class LocationCapture(private val userId: String, private val deviceId: String) {
    fun create(location: Location, batteryLevel: Int?): Triple<LocationPointEntity, SyncOutboxEntity, SyncOutboxEntity> {
        val id = UUID.randomUUID().toString()
        val point = LocationPointEntity(id, userId, deviceId, location.latitude, location.longitude, location.accuracy, if (location.hasAltitude()) location.altitude else null, if (location.hasSpeed()) location.speed else null, if (location.hasBearing()) location.bearing else null, batteryLevel, location.time, null)
        val now = System.currentTimeMillis()
        return Triple(point, SyncOutboxEntity(UUID.randomUUID().toString(), id, SyncDestination.CLOUD, createdAt = now), SyncOutboxEntity(UUID.randomUUID().toString(), id, SyncDestination.PEER, createdAt = now))
    }
}
