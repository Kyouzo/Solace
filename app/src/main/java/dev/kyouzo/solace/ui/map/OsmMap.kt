package dev.kyouzo.solace.ui.map

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import dev.kyouzo.solace.BuildConfig
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/**
 * Two tile modes:
 *  - With MAP_API_KEY (a CARTO key): retina 512 px tiles drawn 1:1, with real light (Voyager) and dark basemaps. Sharp on any screen.
 *  - Without a key: plain OpenStreetMap 256 px tiles. They need no key, but are drawn scaled up on high-density screens so
 *    they are readable and therefore softer; dark mode inverts the light tiles.
 * Public OSM tile servers forbid bulk downloading, so offline prefetch is only enabled in keyed mode.
 */
object MapTiles {
    /** Tiles carry no detail beyond this level; capping here stops the map from magnifying (and blurring) them. */
    const val MAX_ZOOM = 19.0
    const val MIN_ZOOM = 3.0

    val keyed: Boolean get() = BuildConfig.MAP_API_KEY.isNotBlank()
    val attribution: String get() = if (keyed) "© OpenStreetMap contributors © CARTO" else "© OpenStreetMap contributors"

    private fun carto(name: String, style: String) = XYTileSource(
        name, 0, 20, 512, "@2x.png?key=${BuildConfig.MAP_API_KEY}",
        arrayOf("https://basemaps.cartocdn.com/rastertiles/$style/"), attribution
    )

    private val osm by lazy { XYTileSource("SolaceOsm", 0, 19, 256, ".png", arrayOf("https://tile.openstreetmap.org/"), attribution) }
    private val cartoLight by lazy { carto("SolaceCartoVoyager2x", "voyager") }
    private val cartoDark by lazy { carto("SolaceCartoDark2x", "dark_all") }

    fun forTheme(isDark: Boolean) = when {
        !keyed -> osm
        isDark -> cartoDark
        else -> cartoLight
    }
}

fun configureOsmdroid(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        tileFileSystemCacheMaxBytes = 400L * 1024 * 1024
        tileFileSystemCacheTrimBytes = 300L * 1024 * 1024
    }
}

fun createMapView(context: Context, dark: Boolean, loadingColor: Int): MapView {
    configureOsmdroid(context) // must run before the MapView is constructed
    val offline = OfflineTiles.provider(context)
    val view = if (offline != null) MapView(context, offline) else MapView(context)
    return view.apply {
        if (offline != null) {
            tag = OfflineTiles.TAG
            isTilesScaledToDpi = true // vector tiles are drawn at the size the screen needs, so text stays readable
        } else {
            setTileSource(MapTiles.forTheme(dark))
            // Retina tiles are already dense: draw them 1:1. Plain 256 px OSM tiles are scaled up so text stays readable.
            isTilesScaledToDpi = !MapTiles.keyed
        }
        setMultiTouchControls(true)
        minZoomLevel = MapTiles.MIN_ZOOM
        maxZoomLevel = MapTiles.MAX_ZOOM
        isHorizontalMapRepetitionEnabled = false
        isVerticalMapRepetitionEnabled = false
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        overlayManager.tilesOverlay.loadingBackgroundColor = loadingColor
        overlayManager.tilesOverlay.loadingLineColor = loadingColor
        controller.setZoom(3.0)
        applyTheme(dark)
    }
}

fun MapView.applyTheme(dark: Boolean) {
    val offline = tag == OfflineTiles.TAG
    if (!offline) {
        val wanted = MapTiles.forTheme(dark)
        if (tileProvider.tileSource.name() != wanted.name()) setTileSource(wanted)
    }
    // Offline maps and keyless OSM only come in a light style, so dark mode inverts them; keyed CARTO has a real dark style.
    val invert = dark && (offline || !MapTiles.keyed)
    overlayManager.tilesOverlay.setColorFilter(if (invert) TilesOverlay.INVERT_COLORS else null)
}

/** A MapView that follows the activity lifecycle and switches basemap with the system theme. */
@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val loading = MaterialTheme.colorScheme.surfaceContainerLow.toArgb()
    val mapView = remember { createMapView(context, dark, loading) }
    SideEffect { mapView.applyTheme(dark) }
    DisposableEffect(mapView) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event -> when (event) { Lifecycle.Event.ON_RESUME -> mapView.onResume(); Lifecycle.Event.ON_PAUSE -> mapView.onPause(); else -> Unit } }
        lifecycle?.addObserver(observer)
        if (lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) mapView.onResume()
        onDispose { lifecycle?.removeObserver(observer); mapView.onPause(); mapView.onDetach() }
    }
    return mapView
}
