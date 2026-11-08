package dev.kyouzo.solace.data.offline

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import java.io.File

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Failed : DownloadState
    data class Active(val soFar: Long, val total: Long, val queued: Boolean, val paused: Boolean) : DownloadState
}

/**
 * Downloads map files with Android's DownloadManager (resumable, shows a system notification, survives the app being closed)
 * into the app's own storage folder. That folder is not a cache: Android doesn't clear it when space runs low,
 * and it needs no storage permission.
 */
object OfflineStore {
    private const val MB = 1_048_576L

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("offline_maps_v2", Context.MODE_PRIVATE)

    /** App-specific external storage when available (Android/data/<package>/files/maps), otherwise internal files. */
    fun dir(context: Context): File = (context.getExternalFilesDir("maps") ?: File(context.filesDir, "maps")).also { it.mkdirs() }
    fun fileFor(context: Context, region: MapRegion) = File(dir(context), region.fileName)

    fun isDownloaded(context: Context, region: MapRegion): Boolean =
        prefs(context).getBoolean("done:${region.fileName}", false) && fileFor(context, region).length() > 0

    fun downloadedFiles(context: Context): List<File> {
        val p = prefs(context)
        return dir(context).listFiles().orEmpty().filter { it.extension == "map" && it.length() > 0 && p.getBoolean("done:${it.name}", false) }
    }

    fun usedBytes(context: Context): Long = downloadedFiles(context).sumOf { it.length() }

    fun preferOffline(context: Context): Boolean = prefs(context).getBoolean("prefer_offline", true)
    fun setPreferOffline(context: Context, value: Boolean) { prefs(context).edit().putBoolean("prefer_offline", value).apply() }

    fun hasSpaceFor(context: Context, region: MapRegion): Boolean =
        StatFs(dir(context).path).availableBytes > region.sizeMb * MB * 115 / 100 // 15% headroom

    /** Queues the download. The user has explicitly asked for it, so it is allowed on mobile data too. */
    fun enqueue(context: Context, region: MapRegion): Result<Unit> = runCatching {
        check(hasSpaceFor(context, region)) { "NO_SPACE" }
        fileFor(context, region).delete() // otherwise DownloadManager would save a "-1" copy
        val request = DownloadManager.Request(Uri.parse(region.url))
            .setTitle("Solace map: ${region.name}")
            .setDescription(region.url.substringAfterLast('/'))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, "maps", region.fileName)
        val id = context.getSystemService(DownloadManager::class.java).enqueue(request)
        prefs(context).edit().putLong("id:${region.fileName}", id).remove("done:${region.fileName}").remove("failed:${region.fileName}").apply()
    }

    /** Current state of a region's download; also records completion the first time success is seen. */
    fun state(context: Context, region: MapRegion): DownloadState {
        val p = prefs(context)
        val id = p.getLong("id:${region.fileName}", -1L)
        if (id < 0) return if (p.getBoolean("failed:${region.fileName}", false)) DownloadState.Failed else DownloadState.Idle
        val manager = context.getSystemService(DownloadManager::class.java)
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) { p.edit().remove("id:${region.fileName}").apply(); return DownloadState.Idle } // cancelled elsewhere
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val reported = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val total = if (reported > 0) reported else region.sizeMb * MB
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    p.edit().putBoolean("done:${region.fileName}", true).remove("id:${region.fileName}").apply()
                    DownloadState.Idle
                }
                DownloadManager.STATUS_FAILED -> {
                    fileFor(context, region).delete()
                    p.edit().putBoolean("failed:${region.fileName}", true).remove("id:${region.fileName}").apply()
                    DownloadState.Failed
                }
                DownloadManager.STATUS_PAUSED -> DownloadState.Active(soFar, total, queued = false, paused = true)
                DownloadManager.STATUS_PENDING -> DownloadState.Active(soFar, total, queued = true, paused = false)
                else -> DownloadState.Active(soFar, total, queued = false, paused = false)
            }
        }
    }

    fun cancel(context: Context, region: MapRegion) {
        val p = prefs(context)
        val id = p.getLong("id:${region.fileName}", -1L)
        if (id >= 0) context.getSystemService(DownloadManager::class.java).remove(id)
        fileFor(context, region).delete()
        p.edit().remove("id:${region.fileName}").remove("failed:${region.fileName}").apply()
    }

    fun delete(context: Context, region: MapRegion) {
        fileFor(context, region).delete()
        prefs(context).edit().remove("done:${region.fileName}").apply()
    }
}
