package dev.kyouzo.solace.ui.map

import android.app.Application
import android.content.Context
import dev.kyouzo.solace.data.offline.OfflineStore
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver

/** Builds a tile provider that draws the downloaded Mapsforge files, or null when offline maps are off or none exist. */
object OfflineTiles {
    const val TAG = "solace-offline"

    fun provider(context: Context): MapTileProviderBase? {
        if (!OfflineStore.preferOffline(context)) return null
        val files = OfflineStore.downloadedFiles(context)
        if (files.isEmpty()) return null
        return runCatching {
            MapsForgeTileSource.createInstance(context.applicationContext as Application)
            val source = MapsForgeTileSource.createFromFiles(files.toTypedArray())
            MapsForgeTileProvider(SimpleRegisterReceiver(context.applicationContext), source, null)
        }.getOrNull() // a corrupt download must never take the whole map down: fall back to online tiles
    }
}
