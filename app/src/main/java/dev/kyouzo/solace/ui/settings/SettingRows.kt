package dev.kyouzo.solace.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.kyouzo.solace.R
import dev.kyouzo.solace.data.offline.OfflineStore
import dev.kyouzo.solace.ui.offline.OfflineMapsDialog
import dev.kyouzo.solace.ui.offline.formatBytes
import dev.kyouzo.solace.tracking.Retention
import dev.kyouzo.solace.tracking.TrackingControl
import dev.kyouzo.solace.tracking.TrackingPrecision
import dev.kyouzo.solace.ui.common.SettingRow
import kotlinx.coroutines.launch

/** Tap to cycle how long location history is kept on this phone. */
@Composable
fun RetentionRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by remember { mutableIntStateOf(Retention.days(context)) }
    SettingRow(
        icon = Icons.Outlined.Schedule,
        title = stringResource(R.string.retention_title),
        subtitle = stringResource(R.string.retention_hint),
        onClick = {
            val next = Retention.options[(Retention.options.indexOf(days) + 1) % Retention.options.size]
            days = next
            Retention.set(context, next)
            scope.launch { Retention.purge(context) }
        },
        trailing = { Text(if (days <= 0) stringResource(R.string.retention_forever) else stringResource(R.string.retention_days, days), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
    )
}

/** Tap to switch between maximum precision (every metre) and balanced (battery-friendly). Applies immediately. */
@Composable
fun PrecisionRow() {
    val context = LocalContext.current
    var precision by remember { mutableStateOf(TrackingPrecision.current(context)) }
    SettingRow(
        icon = Icons.Outlined.GpsFixed,
        title = stringResource(R.string.precision_title),
        subtitle = stringResource(if (precision == TrackingPrecision.MAXIMUM) R.string.precision_max_hint else R.string.precision_balanced_hint),
        onClick = {
            val next = precision.next()
            precision = next
            TrackingPrecision.set(context, next)
            TrackingControl.restartIfActive(context)
        },
        trailing = { Text(stringResource(if (precision == TrackingPrecision.MAXIMUM) R.string.precision_max else R.string.precision_balanced), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
    )
}

/** Summarises saved offline maps and opens the screen where free maps are downloaded and managed. */
@Composable
fun OfflineMapRow() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var summaryVersion by remember { mutableIntStateOf(0) }
    val files = remember(summaryVersion) { OfflineStore.downloadedFiles(context) }
    if (open) OfflineMapsDialog(onDismiss = { open = false; summaryVersion++ })
    SettingRow(
        icon = Icons.Outlined.CloudDownload,
        title = stringResource(R.string.offline_title),
        subtitle = if (files.isEmpty()) stringResource(R.string.offline_empty_summary) else stringResource(R.string.offline_summary, files.size, formatBytes(context, files.sumOf { it.length() })),
        onClick = { open = true },
        trailing = { Text(stringResource(R.string.offline_action_manage), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
    )
}

/** Summarises how many permissions still need attention and opens the permissions page. */
@Composable
fun PermissionsRow() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val resumed = rememberResumeTick()
    val missing = remember(open, resumed) { AppPermissions.missingCount(context) }
    if (open) PermissionsDialog(onDismiss = { open = false })
    SettingRow(
        icon = Icons.Outlined.Security,
        title = stringResource(R.string.perm_title),
        subtitle = if (missing == 0) stringResource(R.string.perm_all_set) else stringResource(R.string.perm_need_attention, missing),
        onClick = { open = true },
        trailing = { Text(stringResource(R.string.offline_action_manage), color = if (missing == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge) }
    )
}
