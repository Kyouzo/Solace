package dev.kyouzo.solace.ui.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.kyouzo.solace.R
import dev.kyouzo.solace.data.local.AppDatabase
import dev.kyouzo.solace.data.remote.SyncRepository
import dev.kyouzo.solace.ui.common.BrandHeader
import dev.kyouzo.solace.ui.common.IconBadge
import dev.kyouzo.solace.ui.common.PrivacyCard
import dev.kyouzo.solace.ui.common.ScreenColumn
import dev.kyouzo.solace.ui.common.SolaceCard
import dev.kyouzo.solace.ui.common.rememberSyncUiState
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlacesScreen() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).locationDao() }
    val repo = remember { SyncRepository(context) }
    val places by dao.observePlaces().collectAsState(emptyList())
    val lastFix by dao.observeLast().collectAsState(null)
    val sync by rememberSyncUiState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<PlaceKind?>(null) }

    // Pull whatever your partner added or removed since you last looked.
    LaunchedEffect(Unit) { repo.syncPlaces() }

    editing?.let { startKind ->
        PlaceEditorDialog(
            initialKind = startKind,
            currentFix = lastFix?.let { GeoPoint(it.latitude, it.longitude) },
            shared = sync.paired,
            onSave = { place -> scope.launch { dao.savePlace(place); repo.syncPlaces() }; editing = null },
            onDismiss = { editing = null }
        )
    }

    val missing = listOf(PlaceKind.HOME, PlaceKind.SCHOOL, PlaceKind.WORK).filter { kind -> places.none { it.kind == kind.name } }

    ScreenColumn {
        item { BrandHeader(stringResource(R.string.places_title)) }
        item { Text(stringResource(if (sync.paired) R.string.places_note_shared else R.string.places_note), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Button(onClick = { editing = PlaceKind.CUSTOM }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.places_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (missing.isNotEmpty()) item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                missing.forEach { kind ->
                    AssistChip(
                        onClick = { editing = kind },
                        label = { Text(stringResource(R.string.places_add_kind, stringResource(kind.label))) },
                        leadingIcon = { Icon(kind.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
        if (places.isEmpty()) item { PrivacyCard(stringResource(R.string.places_empty_title), stringResource(R.string.places_empty_body)) }
        items(places.size, key = { places[it].id }) { index ->
            val place = places[index]
            val kind = PlaceKind.from(place.kind)
            SolaceCard {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(kind.icon)
                    Column(Modifier.weight(1f)) {
                        Text(place.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.places_radius, place.radiusMeters.toInt()) + if (place.syncState == 1) " · " + stringResource(R.string.places_shared_badge) else "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(onClick = { scope.launch { dao.removePlace(place.id); repo.syncPlaces() } }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.places_delete))
                    }
                }
            }
        }
    }
}
