package dev.kyouzo.solace.data.remote

import android.content.Context
import android.os.Build
import dev.kyouzo.solace.data.local.AppDatabase
import dev.kyouzo.solace.data.local.RemoteLocationEntity
import dev.kyouzo.solace.data.local.SavedPlaceEntity
import dev.kyouzo.solace.data.local.SyncDestination
import dev.kyouzo.solace.data.local.SyncState
import dev.kyouzo.solace.tracking.PlaceMonitor
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

data class SyncReport(val uploaded: Int, val partnerRecordedAt: Long?)
data class CoupleStatus(val paired: Boolean, val mySharing: Boolean, val partnerSharing: Boolean, val partnerName: String?)

/** Uploads the local outbox to Supabase and pulls the partner's latest location. Runs only while sharing is on. */
class SyncRepository(context: Context) {
    private val app = context.applicationContext
    private val auth = SupabaseAuthRepository(app)
    private val rest = SupabaseRest(auth)
    private val dao = AppDatabase.get(app).locationDao()
    private val prefs = app.getSharedPreferences("sync", Context.MODE_PRIVATE)

    fun session() = auth.savedSession()

    suspend fun syncNow(): Result<SyncReport> = runCatching {
        val session = auth.savedSession() ?: error("Sign in first.")
        // Refresh the server-side sharing/pairing state first so the local flag never goes stale (e.g. after reinstall).
        coupleStatus()
        syncPlaces() // best effort: a places failure must not block location sync
        var uploaded = 0
        if (sharingEnabled(app)) {
            registerDevice(session.userId)
            uploaded = uploadPending(session.userId)
        }
        val partner = pullPartner(session.userId)
        prefs.edit().putLong("last_sync_at", System.currentTimeMillis()).apply()
        SyncReport(uploaded, partner)
    }

    private suspend fun registerDevice(userId: String) {
        val identity = app.getSharedPreferences("identity", Context.MODE_PRIVATE)
        val deviceId = identity.getString("device_id", null) ?: UUID.randomUUID().toString().also { identity.edit().putString("device_id", it).apply() }
        val now = Instant.now().toString()
        val body = JSONArray().put(JSONObject().put("id", deviceId).put("user_id", userId).put("name", Build.MODEL.take(80)).put("last_seen_at", now).put("updated_at", now))
        rest.request("POST", "devices?on_conflict=id", body.toString(), mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")).getOrThrow()
    }

    private suspend fun uploadPending(userId: String): Int {
        var total = 0
        repeat(100) {
            val outbox = dao.pendingFor(SyncDestination.CLOUD, 200)
            if (outbox.isEmpty()) return total
            val points = dao.pointsByIds(outbox.map { it.recordId }).associateBy { it.id }
            // Points recorded before sign-in are stored as "local" and belong to the signed-in user; anything else is foreign.
            val (usable, unusable) = outbox.partition { o -> points[o.recordId]?.let { it.userId == "local" || it.userId == userId } == true }
            val now = System.currentTimeMillis()
            if (unusable.isNotEmpty()) dao.markOutbox(unusable.map { it.id }, SyncState.FAILED, now)
            if (usable.isEmpty()) return@repeat
            val body = JSONArray()
            usable.forEach { o ->
                val p = points.getValue(o.recordId)
                body.put(JSONObject()
                    .put("id", p.id).put("user_id", userId).put("device_id", p.deviceId)
                    .put("latitude", p.latitude).put("longitude", p.longitude).put("accuracy", p.accuracy.toDouble())
                    .put("altitude", p.altitude ?: JSONObject.NULL).put("speed", p.speed?.toDouble() ?: JSONObject.NULL)
                    .put("bearing", p.bearing?.toDouble() ?: JSONObject.NULL).put("battery_level", p.batteryLevel ?: JSONObject.NULL)
                    .put("recorded_at", Instant.ofEpochMilli(p.recordedAt).toString()))
            }
            rest.request("POST", "location_points?on_conflict=id", body.toString(), mapOf("Prefer" to "resolution=ignore-duplicates,return=minimal")).getOrThrow()
            dao.markOutbox(usable.map { it.id }, SyncState.COMPLETED, now)
            dao.markPoints(usable.map { it.recordId }, SyncState.COMPLETED)
            total += usable.size
        }
        return total
    }

    private suspend fun pullPartner(userId: String): Long? {
        val text = rest.request("GET", "latest_locations?select=user_id,recorded_at,location_points(id,latitude,longitude,accuracy,received_at)&user_id=neq.$userId").getOrThrow()
        val rows = JSONArray(text)
        // Server returns nothing unless both people have sharing on; mirror that locally so stale data disappears.
        dao.clearRemote()
        var latest: Long? = null
        var latestPoint: Pair<Double, Double>? = null
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val point = row.optJSONObject("location_points") ?: continue
            val recordedAt = OffsetDateTime.parse(row.getString("recorded_at")).toInstant().toEpochMilli()
            val receivedAt = runCatching { OffsetDateTime.parse(point.getString("received_at")).toInstant().toEpochMilli() }.getOrDefault(recordedAt)
            dao.saveRemote(RemoteLocationEntity(point.getString("id"), row.getString("user_id"), point.getDouble("latitude"), point.getDouble("longitude"), point.getDouble("accuracy").toFloat(), recordedAt, receivedAt))
            if (latest == null || recordedAt > latest) { latest = recordedAt; latestPoint = point.getDouble("latitude") to point.getDouble("longitude") }
        }
        latestPoint?.let { PlaceMonitor.check(app, "partner", it.first, it.second) }
        return latest
    }

    suspend fun coupleStatus(): Result<CoupleStatus> = rest.request("POST", "rpc/couple_status", "{}").mapCatching {
        val json = JSONObject(it)
        CoupleStatus(json.optBoolean("paired"), json.optBoolean("my_sharing"), json.optBoolean("partner_sharing"), json.optString("partner_name").takeIf { n -> n.isNotBlank() && n != "null" })
    }.onSuccess { setSharingFlag(it.mySharing); prefs.edit().putBoolean("paired", it.paired).apply() }

    /**
     * Two-way sync of saved places with the shared cloud table. Only runs while paired; before that places stay on this phone
     * and are uploaded the first time a sync happens after pairing. Order: push deletions, push new places, pull the full list.
     */
    suspend fun syncPlaces(): Result<Unit> = runCatching {
        if (auth.savedSession() == null || !prefs.getBoolean("paired", false)) return@runCatching
        val deleted = dao.deletedPlaces()
        if (deleted.isNotEmpty()) {
            rest.request("DELETE", "shared_places?id=in.(${deleted.joinToString(",") { it.id }})").getOrThrow()
            dao.purgePlaces(deleted.map { it.id })
        }
        val pending = dao.pendingPlaces()
        if (pending.isNotEmpty()) {
            val body = JSONArray()
            pending.forEach { p ->
                body.put(JSONObject().put("id", p.id).put("name", p.name).put("kind", p.kind).put("latitude", p.latitude).put("longitude", p.longitude)
                    .put("radius_meters", p.radiusMeters.toDouble()).put("created_at", Instant.ofEpochMilli(p.createdAt).toString()))
            }
            rest.request("POST", "shared_places?on_conflict=id", body.toString(), mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")).getOrThrow()
            dao.markPlacesSynced(pending.map { it.id })
        }
        val rows = JSONArray(rest.request("GET", "shared_places?select=id,name,kind,latitude,longitude,radius_meters,created_at").getOrThrow())
        val remote = (0 until rows.length()).map { i ->
            val r = rows.getJSONObject(i)
            SavedPlaceEntity(
                id = r.getString("id"), name = r.getString("name"), latitude = r.getDouble("latitude"), longitude = r.getDouble("longitude"),
                radiusMeters = r.getDouble("radius_meters").toFloat(),
                createdAt = runCatching { OffsetDateTime.parse(r.getString("created_at")).toInstant().toEpochMilli() }.getOrDefault(System.currentTimeMillis()),
                kind = r.optString("kind", "CUSTOM"), syncState = 1
            )
        }
        dao.applyRemotePlaces(remote)
    }

    suspend fun createInvite(): Result<String> = rest.request("POST", "rpc/create_pairing_invite", "{}").mapCatching { JSONTokener(it).nextValue() as? String ?: error("Unexpected response from server.") }
    suspend fun redeemInvite(code: String): Result<Unit> = rest.request("POST", "rpc/redeem_pairing_invite", JSONObject().put("code", code.trim()).toString()).map { }
    suspend fun setSharing(enabled: Boolean): Result<Unit> = rest.request("POST", "rpc/set_sharing", JSONObject().put("enabled", enabled).toString()).map { setSharingFlag(enabled) }

    private fun setSharingFlag(enabled: Boolean) { prefs.edit().putBoolean("sharing", enabled).apply() }
    fun lastSyncAt(): Long = prefs.getLong("last_sync_at", 0L)

    companion object {
        fun sharingEnabled(context: Context): Boolean = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE).getBoolean("sharing", false)
    }
}
