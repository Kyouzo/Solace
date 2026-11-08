package dev.kyouzo.solace.ui.offline

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.kyouzo.solace.R
import dev.kyouzo.solace.data.offline.DownloadState
import dev.kyouzo.solace.data.offline.MapRegion
import dev.kyouzo.solace.data.offline.MapRegions
import dev.kyouzo.solace.data.offline.OfflineStore
import dev.kyouzo.solace.ui.common.IconBadge
import dev.kyouzo.solace.ui.common.SolaceCard
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Sizes above this ask for confirmation first. */
private const val LARGE_DOWNLOAD_MB = 150

private sealed interface Row {
    data class Header(val label: String) : Row
    data class Item(val region: MapRegion, val saved: Boolean) : Row
}

internal fun formatBytes(context: Context, bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb >= 1024) context.getString(R.string.size_gb, mb / 1024) else context.getString(R.string.size_mb, mb.roundToInt().coerceAtLeast(1))
}

/** Full-screen browser for the free Mapsforge map catalogue: search, download to this phone, and delete. */
@Composable
fun OfflineMapsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val regions = remember { MapRegions.load(context) }
    var query by remember { mutableStateOf("") }
    var states by remember { mutableStateOf<Map<String, DownloadState>>(emptyMap()) }
    var version by remember { mutableIntStateOf(0) } // bumped after delete/cancel so lists recompute
    var largeToConfirm by remember { mutableStateOf<MapRegion?>(null) }
    var deleteToConfirm by remember { mutableStateOf<MapRegion?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var prefer by remember { mutableStateOf(OfflineStore.preferOffline(context)) }

    // DownloadManager has no change callback for progress, so poll while the screen is open.
    LaunchedEffect(Unit) {
        while (true) {
            states = regions.associate { it.path to OfflineStore.state(context, it) }
            delay(800)
        }
    }

    fun start(region: MapRegion) {
        message = null
        OfflineStore.enqueue(context, region).onFailure {
            message = if (it.message == "NO_SPACE") context.getString(R.string.offline_no_space, formatBytes(context, region.sizeMb * 1_048_576L)) else it.message
        }
        version++
    }

    val rows = remember(query, version, states) {
        val q = query.trim()
        val matching = regions.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || MapRegion.groupLabel(it.group).contains(q, ignoreCase = true) }
        val saved = matching.filter { OfflineStore.isDownloaded(context, it) }
        buildList<Row> {
            if (saved.isNotEmpty()) { add(Row.Header(context.getString(R.string.offline_saved_section))); saved.forEach { add(Row.Item(it, true)) } }
            matching.filter { it !in saved }.groupBy { it.group }.forEach { (group, list) ->
                add(Row.Header(MapRegion.groupLabel(group)))
                list.forEach { add(Row.Item(it, false)) }
            }
        }
    }

    largeToConfirm?.let { region ->
        AlertDialog(
            onDismissRequest = { largeToConfirm = null },
            title = { Text(stringResource(R.string.offline_large_title)) },
            text = { Text(stringResource(R.string.offline_large_body, formatBytes(context, region.sizeMb * 1_048_576L))) },
            confirmButton = { TextButton(onClick = { largeToConfirm = null; start(region) }) { Text(stringResource(R.string.offline_large_confirm)) } },
            dismissButton = { TextButton(onClick = { largeToConfirm = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    deleteToConfirm?.let { region ->
        AlertDialog(
            onDismissRequest = { deleteToConfirm = null },
            title = { Text(stringResource(R.string.offline_delete_title)) },
            text = { Text(stringResource(R.string.offline_delete_body, region.name)) },
            confirmButton = { TextButton(onClick = { OfflineStore.delete(context, region); deleteToConfirm = null; version++ }) { Text(stringResource(R.string.places_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteToConfirm = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = dimensionResource(R.dimen.content_max_width)).fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close)) }
                        Text(stringResource(R.string.offline_title), style = MaterialTheme.typography.titleLarge)
                    }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = dimensionResource(R.dimen.screen_padding), vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            SolaceCard {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.offline_info), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(stringResource(R.string.offline_use_toggle), style = MaterialTheme.typography.bodyLarge)
                                            Text(stringResource(R.string.offline_use_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = prefer, onCheckedChange = { prefer = it; OfflineStore.setPreferOffline(context, it) })
                                    }
                                    Text(
                                        stringResource(R.string.offline_storage, formatBytes(context, OfflineStore.usedBytes(context)), OfflineStore.dir(context).absolutePath),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                        item {
                            OutlinedTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                label = { Text(stringResource(R.string.offline_search)) }, modifier = Modifier.fillMaxWidth()
                            )
                        }
                        items(rows, key = { row -> when (row) { is Row.Header -> "h:${row.label}"; is Row.Item -> "r:${row.region.path}:${row.saved}" } }) { row ->
                            when (row) {
                                is Row.Header -> Text(row.label.uppercase(), modifier = Modifier.padding(top = 10.dp, start = 4.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                is Row.Item -> RegionRow(
                                    region = row.region, saved = row.saved, state = states[row.region.path] ?: DownloadState.Idle,
                                    onDownload = { if (row.region.sizeMb > LARGE_DOWNLOAD_MB) largeToConfirm = row.region else start(row.region) },
                                    onCancel = { OfflineStore.cancel(context, row.region); version++ },
                                    onDelete = { deleteToConfirm = row.region }
                                )
                            }
                        }
                        item { Text(stringResource(R.string.offline_attribution), modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionRow(region: MapRegion, saved: Boolean, state: DownloadState, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SolaceCard {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(if (saved) Icons.Outlined.CheckCircle else Icons.Outlined.Map, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(region.name, style = MaterialTheme.typography.titleMedium)
                when {
                    state is DownloadState.Active -> {
                        Text(
                            when {
                                state.queued -> stringResource(R.string.offline_queued)
                                state.paused -> stringResource(R.string.offline_paused)
                                else -> stringResource(R.string.offline_progress_of, formatBytes(context, state.soFar), formatBytes(context, state.total))
                            },
                            style = MaterialTheme.typography.bodyMedium, color = muted
                        )
                        LinearProgressIndicator(progress = { if (state.total > 0) (state.soFar.toFloat() / state.total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                    }
                    state is DownloadState.Failed -> Text(stringResource(R.string.offline_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    saved -> Text(stringResource(R.string.offline_saved_size, formatBytes(context, OfflineStore.fileFor(context, region).length())), style = MaterialTheme.typography.bodyMedium, color = muted)
                    else -> Text(formatBytes(context, region.sizeMb * 1_048_576L), style = MaterialTheme.typography.bodyMedium, color = muted)
                }
            }
            when {
                state is DownloadState.Active -> IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.offline_action_cancel)) }
                saved -> IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.places_delete)) }
                else -> IconButton(onClick = onDownload) { Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.offline_action_download)) }
            }
        }
    }
}
