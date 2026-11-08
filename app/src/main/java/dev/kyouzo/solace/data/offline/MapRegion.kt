package dev.kyouzo.solace.data.offline

import android.content.Context
import dev.kyouzo.solace.R

/**
 * A downloadable Mapsforge map (free OpenStreetMap data, https://download.mapsforge.org).
 * [path] is the server path without extension, for example "africa/egypt".
 */
data class MapRegion(val path: String, val name: String, val sizeMb: Int) {
    val group: String get() = path.substringBefore('/')
    val fileName: String get() = path.replace('/', '_') + ".map"
    val url: String get() = "https://download.mapsforge.org/maps/v5/$path.map"

    companion object {
        /** Parses one "path|Display name|sizeMb" line; returns null for blank or malformed lines. */
        fun parse(line: String): MapRegion? {
            val parts = line.trim().split('|')
            if (parts.size != 3) return null
            val size = parts[2].toIntOrNull() ?: return null
            if (parts[0].isBlank() || parts[1].isBlank()) return null
            return MapRegion(parts[0], parts[1], size)
        }

        fun groupLabel(group: String): String = group.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }
}

object MapRegions {
    /** The bundled catalogue, generated from the server's directory listing. */
    fun load(context: Context): List<MapRegion> =
        context.resources.openRawResource(R.raw.mapsforge_regions).bufferedReader().useLines { lines -> lines.mapNotNull(MapRegion::parse).toList() }
}
