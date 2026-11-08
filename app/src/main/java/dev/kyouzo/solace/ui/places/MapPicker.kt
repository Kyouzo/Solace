package dev.kyouzo.solace.ui.places

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.kyouzo.solace.R
import dev.kyouzo.solace.ui.common.SolaceCard
import dev.kyouzo.solace.ui.map.MapTiles
import dev.kyouzo.solace.ui.map.rememberMapView
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import java.util.Locale

/**
 * Full-screen map with a fixed pin in the middle: drag the map underneath it, then confirm.
 * [initial] is where the map opens (an existing place, your last fix, or the world view if neither is known).
 */
@Composable
fun MapPickerDialog(initial: GeoPoint?, onPicked: (GeoPoint) -> Unit, onDismiss: () -> Unit) {
    var center by remember { mutableStateOf(initial ?: GeoPoint(0.0, 0.0)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                PickerMap(initial, Modifier.fillMaxSize()) { center = it }
                Icon(
                    Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center).offset(y = (-20).dp).size(40.dp) // pin tip sits on the exact centre
                )
                SolaceCard(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.places_picker_title), style = MaterialTheme.typography.titleMedium)
                        Text(String.format(Locale.US, "%.6f, %.6f", center.latitude, center.longitude), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                    Button(onClick = { onPicked(center) }, Modifier.weight(1f)) { Text(stringResource(R.string.places_use_location)) }
                }
                Text(
                    MapTiles.attribution, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 84.dp, end = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun PickerMap(initial: GeoPoint?, modifier: Modifier, onCenter: (GeoPoint) -> Unit) {
    val mapView = rememberMapView()
    val report = rememberUpdatedState(onCenter)
    remember(mapView) {
        mapView.post {
            if (initial != null) { mapView.controller.setZoom(17.0); mapView.controller.setCenter(initial) } else mapView.controller.setZoom(3.0)
        }
        mapView.addMapListener(object : MapListener {
            private fun publish() { val c = mapView.mapCenter; report.value(GeoPoint(c.latitude, c.longitude)) }
            override fun onScroll(event: ScrollEvent?): Boolean { publish(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { publish(); return false }
        })
        mapView.setOnTouchListener { v, event -> if (event.action == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true); false }
        true
    }
    AndroidView(modifier = modifier, factory = { mapView })
}
