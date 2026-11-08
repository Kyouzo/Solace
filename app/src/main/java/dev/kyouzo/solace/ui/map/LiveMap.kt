package dev.kyouzo.solace.ui.map

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/** One-shot request to move the camera; a new [id] triggers a new move. */
data class FocusRequest(val id: Int, val point: GeoPoint)

private const val ME_COLOR = 0xFFB69DF8.toInt()
private const val PARTNER_COLOR = 0xFFF2B8D2.toInt()

/**
 * OpenStreetMap view (no API key). Shows you, your partner, and optionally a route.
 * With [follow] on, the camera tracks [me] on every update; touching the map calls [onUserInteract] so the caller can turn follow off.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun LiveMap(
    modifier: Modifier,
    me: GeoPoint?,
    partner: GeoPoint?,
    path: List<GeoPoint> = emptyList(),
    follow: Boolean = false,
    focus: FocusRequest? = null,
    onUserInteract: () -> Unit = {}
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val interact = rememberUpdatedState(onUserInteract)
    val centred = remember { booleanArrayOf(false) }
    val pathKey = remember { intArrayOf(-1) }
    val lastFocus = remember { intArrayOf(0) }
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(3.0)
            // Let the map handle drags instead of the surrounding scrolling container, and tell the caller the user took over.
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) interact.value()
                if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    DisposableEffect(mapView) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event -> when (event) { Lifecycle.Event.ON_RESUME -> mapView.onResume(); Lifecycle.Event.ON_PAUSE -> mapView.onPause(); else -> Unit } }
        lifecycle?.addObserver(observer)
        if (lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) mapView.onResume()
        onDispose { lifecycle?.removeObserver(observer); mapView.onPause(); mapView.onDetach() }
    }
    AndroidView(modifier = modifier, factory = { mapView }, update = { map ->
        // Light OSM tiles are inverted in dark mode so the map matches the rest of the UI.
        map.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        map.overlays.clear()
        if (path.size > 1) map.overlays.add(Polyline().apply { setPoints(path); outlinePaint.color = ME_COLOR; outlinePaint.strokeWidth = 9f })
        fun dot(point: GeoPoint, label: String, color: Int) = Marker(map).apply {
            position = point; title = label
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color); setStroke(5, Color.WHITE); setSize(44, 44) }
        }
        partner?.let { map.overlays.add(dot(it, "Partner", PARTNER_COLOR)) }
        me?.let { map.overlays.add(dot(it, "You", ME_COLOR)) }

        when {
            path.size > 1 -> if (pathKey[0] != path.size) { pathKey[0] = path.size; map.post { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(path), false, 80) } }
            follow && me != null -> {
                if (!centred[0]) { map.controller.setZoom(18.0); map.controller.setCenter(me); centred[0] = true } else map.controller.animateTo(me)
            }
            !centred[0] -> {
                val points = listOfNotNull(me, partner)
                if (points.size == 1) { map.controller.setZoom(17.0); map.controller.setCenter(points[0]); centred[0] = true }
                else if (points.size == 2) { map.post { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, 120) }; centred[0] = true }
            }
        }
        focus?.let { if (it.id != lastFocus[0]) { lastFocus[0] = it.id; map.controller.setZoom(18.0); map.controller.animateTo(it.point) } }
        map.invalidate()
    })
}
