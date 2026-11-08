package dev.kyouzo.solace.ui.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.kyouzo.solace.R
import dev.kyouzo.solace.data.local.SavedPlaceEntity
import org.osmdroid.util.GeoPoint
import java.util.Locale
import java.util.UUID

private val radiusOptions = listOf(50f, 100f, 150f, 300f)

/**
 * Create a place: pick a template, set its location (current position, pin on a map, or typed coordinates) and an alert radius.
 * [currentFix] is the phone's last recorded position, if any.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceEditorDialog(initialKind: PlaceKind, currentFix: GeoPoint?, shared: Boolean, onSave: (SavedPlaceEntity) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(initialKind) }
    val kindLabels = PlaceKind.entries.associateWith { stringResource(it.label) }
    var name by remember { mutableStateOf(if (initialKind == PlaceKind.CUSTOM) "" else kindLabels.getValue(initialKind)) }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var radius by remember { mutableFloatStateOf(150f) }
    var picking by remember { mutableStateOf(false) }

    val lat = latitude.toDoubleOrNull()
    val lon = longitude.toDoubleOrNull()
    val valid = name.isNotBlank() && lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0

    fun setLocation(point: GeoPoint) { latitude = String.format(Locale.US, "%.6f", point.latitude); longitude = String.format(Locale.US, "%.6f", point.longitude) }

    if (picking) {
        MapPickerDialog(
            initial = if (lat != null && lon != null) GeoPoint(lat, lon) else currentFix,
            onPicked = { setLocation(it); picking = false },
            onDismiss = { picking = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.places_add)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlaceKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = {
                                // Only replace the name if the user hasn't typed their own.
                                if (name.isBlank() || name in kindLabels.values) name = if (option == PlaceKind.CUSTOM) "" else kindLabels.getValue(option)
                                kind = option
                            },
                            label = { Text(kindLabels.getValue(option)) },
                            leadingIcon = { Icon(option.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Text(stringResource(R.string.places_location_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = currentFix != null, onClick = { currentFix?.let(::setLocation) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.places_use_current), modifier = Modifier.padding(start = 6.dp), maxLines = 2)
                    }
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.places_pick_on_map), modifier = Modifier.padding(start = 6.dp), maxLines = 2)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(latitude, { latitude = it }, label = { Text(stringResource(R.string.field_latitude)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(longitude, { longitude = it }, label = { Text(stringResource(R.string.field_longitude)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }

                Text(stringResource(R.string.places_radius_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    radiusOptions.forEach { option ->
                        FilterChip(selected = radius == option, onClick = { radius = option }, label = { Text(stringResource(R.string.meters_short, option.toInt())) })
                    }
                }
                Text(stringResource(if (shared) R.string.places_visible_shared else R.string.places_visible_local), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                if (lat != null && lon != null) onSave(SavedPlaceEntity(UUID.randomUUID().toString(), name.trim(), lat, lon, radius, System.currentTimeMillis(), kind.name))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
