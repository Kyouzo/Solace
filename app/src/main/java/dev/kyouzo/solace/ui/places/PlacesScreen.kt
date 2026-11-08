package dev.kyouzo.solace.ui.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.kyouzo.solace.R
import dev.kyouzo.solace.data.local.AppDatabase
import dev.kyouzo.solace.data.local.SavedPlaceEntity
import dev.kyouzo.solace.ui.common.BrandHeader
import dev.kyouzo.solace.ui.common.IconBadge
import dev.kyouzo.solace.ui.common.PrivacyCard
import dev.kyouzo.solace.ui.common.ScreenColumn
import dev.kyouzo.solace.ui.common.SolaceCard
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private val radiusOptions = listOf(50f, 100f, 150f, 300f)

@Composable
fun PlacesScreen() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).locationDao() }
    val places by dao.observePlaces().collectAsState(emptyList())
    val lastFix by dao.observeLast().collectAsState(null)
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var radius by remember { mutableFloatStateOf(150f) }

    if (adding) {
        val lat = latitude.toDoubleOrNull()
        val lon = longitude.toDoubleOrNull()
        val valid = name.isNotBlank() && lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.places_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(
                        enabled = lastFix != null,
                        onClick = { lastFix?.let { latitude = String.format(Locale.US, "%.6f", it.latitude); longitude = String.format(Locale.US, "%.6f", it.longitude) } }
                    ) {
                        Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.places_use_current), modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(latitude, { latitude = it }, label = { Text(stringResource(R.string.field_latitude)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(longitude, { longitude = it }, label = { Text(stringResource(R.string.field_longitude)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                    Text(stringResource(R.string.places_radius_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        radiusOptions.forEach { option ->
                            FilterChip(selected = radius == option, onClick = { radius = option }, label = { Text(stringResource(R.string.meters_short, option.toInt())) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    if (lat != null && lon != null) {
                        scope.launch { dao.savePlace(SavedPlaceEntity(UUID.randomUUID().toString(), name.trim(), lat, lon, radius, System.currentTimeMillis())) }
                        adding = false; name = ""; latitude = ""; longitude = ""
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    ScreenColumn {
        item { BrandHeader(stringResource(R.string.places_title)) }
        item { Text(stringResource(R.string.places_note), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Button(onClick = { adding = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.places_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (places.isEmpty()) item { PrivacyCard(stringResource(R.string.places_empty_title), stringResource(R.string.places_empty_body)) }
        items(places.size, key = { places[it].id }) { index ->
            val place = places[index]
            SolaceCard {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(Icons.Outlined.Home)
                    Column(Modifier.weight(1f)) {
                        Text(place.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.places_radius, place.radiusMeters.toInt()), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { scope.launch { dao.deletePlace(place.id) } }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.places_delete))
                    }
                }
            }
        }
    }
}
