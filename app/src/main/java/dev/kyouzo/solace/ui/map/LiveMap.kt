package dev.kyouzo.solace.ui.map

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** One-shot request to move the camera; a new [id] triggers a new move. */
data class FocusRequest(val id: Int, val point: GeoPoint)

private const val ME_COLOR = 0xFFB69DF8.toInt()
private const val PARTNER_COLOR = 0xFFF2B8D2.toInt()

/**
 * HD OpenStreetMap view. Shows you, your partner, and optionally a route.
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
    onUserInteract: () -> Unit = {},
    showAttribution: Boolean = true
) {
    val context = LocalContext.current
    val interact = rememberUpdatedState(onUserInteract)
    val centred = remember { booleanArrayOf(false) }
    val pathKey = remember { intArrayOf(-1) }
    val lastFocus = remember { intArrayOf(0) }
    val mapView = rememberMapView()
    remember(mapView) {
        // Let the map handle drags instead of the surrounding scrolling container, and tell the caller the user took over.
        mapView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) interact.value()
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }
    Box(modifier) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { mapView }, update = { map ->
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
                path.size > 1 -> if (pathKey[0] != path.size) {
                    pathKey[0] = path.size
                    val box = BoundingBox.fromGeoPoints(path)
                    // A route only a few metres long would otherwise be framed far past the map's real detail and look blurry.
                    if (box.diagonalLengthInMeters < 60.0) { map.controller.setZoom(18.0); map.controller.setCenter(path.last()) }
                    else map.post { map.zoomToBoundingBox(box, false, 80) }
                }
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
        if (showAttribution) {
            Text(
                MapTiles.attribution,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).alpha(0.75f),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
